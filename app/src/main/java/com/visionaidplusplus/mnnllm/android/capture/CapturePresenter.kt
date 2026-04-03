package com.visionaidplusplus.mnnllm.android.capture

import android.app.Activity
import android.util.Log
import com.visionaidplusplus.mnnllm.android.chat.GenerateResultProcessor
import com.visionaidplusplus.mnnllm.android.llm.ChatService
import com.visionaidplusplus.mnnllm.android.llm.ChatSession
import com.visionaidplusplus.mnnllm.android.llm.GenerateProgressListener
import com.visionaidplusplus.mnnllm.android.model.ModelTypeUtils
import com.visionaidplusplus.mnnllm.android.model.ModelUtils
import com.visionaidplusplus.mnnllm.android.modelist.ModelListManager
import com.visionaidplusplus.mls.api.ModelItem
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CapturePresenter(
    private val activity: Activity,
    private val view: CaptureContract.View,
    private val lifecycleScope: CoroutineScope
) : CaptureContract.Presenter {

    companion object {
        const val TAG = "CapturePresenter"
    }

    private var textToSpeech: TextToSpeech? = null
    private var chatSession: ChatSession? = null
    private var isGenerating = false

    override fun start() {
        initTts()
    }

    private fun initTts() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(activity) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "TTS Language not supported")
                    } else {
                        Log.d(TAG, "TTS initialized")
                    }
                } else {
                    Log.e(TAG, "TTS init failed")
                }
            }
        }
    }

    override fun stop() {
        isGenerating = false
        chatSession?.release()
        chatSession = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun findVisionModel(): ModelItem? {
        val downloadedModelsMap = ModelListManager.getModelIdModelMap()
        return downloadedModelsMap.values.firstOrNull { 
            ModelTypeUtils.isVisualModel(it.modelId!!)
        }
    }

    override fun analyzeImage(imagePath: String, prompt: String, model: ModelItem?) {
        if (isGenerating) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    view.showLoading(true)
                    view.updateExplanation("Analyzing...")
                }
                
                val visionModelInfo = model ?: findVisionModel()
                if (visionModelInfo == null) {
                    withContext(Dispatchers.Main) {
                        view.showLoading(false)
                        view.showError("No Vision model found. Please download one from Models Market.")
                    }
                    return@launch
                }

                val chatService = ChatService.provide()
                val configPath = ModelUtils.getConfigPathForModel(visionModelInfo)

                textToSpeech?.stop() // Stop previous speech
                chatSession?.release()
                chatSession = chatService.createSession(
                    visionModelInfo.modelId!!, visionModelInfo.modelName!!, null, null, configPath, true
                )
                
                chatSession?.load()
                val PROMPT = """
        You are assisting a low-vision user.
        Describe the image clearly and calmly.

        Mention:
        - Objects present
        - Any visible text
        - Identify objects
        -read available signs 
        -Dont repeat the same sentence 
        -be human way of describing the image
        
        Maximum 60 words.
    """.trimIndent()
                val promptWithImage = "<img>$imagePath</img>$PROMPT"
                
                val resultProcessor = GenerateResultProcessor()
                resultProcessor.generateBegin()
                var currentExplanation = StringBuilder()
                var ttsBuffer = StringBuilder()
                var lastNormalLength = 0
                
                isGenerating = true
                chatSession?.generate(promptWithImage, mapOf(), object : GenerateProgressListener {
                    override fun onProgress(progress: String?): Boolean {
                        if (!isGenerating) return true
                        
                        resultProcessor.process(progress)
                        val normalOutput = resultProcessor.getNormalOutput()
                        val displayResult = resultProcessor.getDisplayResult()
                        
                        lifecycleScope.launch(Dispatchers.Main) {
                            view.updateExplanation(displayResult)
                        }
                        
                        if (normalOutput.length > lastNormalLength) {
                            val outputChunk = normalOutput.substring(lastNormalLength)
                            lastNormalLength = normalOutput.length
                            ttsBuffer.append(outputChunk)
                            
                            val delimiters = "[.,!。，！？?\n、：；:]".toRegex()
                            if (delimiters.containsMatchIn(ttsBuffer.toString())) {
                                val textToSpeak = ttsBuffer.toString()
                                ttsBuffer.clear()
                                textToSpeech?.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, null)
                            }
                        }
                        return false
                    }
                })

                if (ttsBuffer.isNotEmpty()) {
                    val textToSpeak = ttsBuffer.toString()
                    textToSpeech?.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, null)
                }
                
                withContext(Dispatchers.Main) {
                    view.showLoading(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing image", e)
                withContext(Dispatchers.Main) {
                    view.showLoading(false)
                    view.showError("Failed to analyze image: ${e.message}")
                }
            } finally {
                isGenerating = false
            }
        }
    }
}
