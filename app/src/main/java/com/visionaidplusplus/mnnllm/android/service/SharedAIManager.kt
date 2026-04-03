package com.visionaidplusplus.mnnllm.android.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.visionaidplusplus.mls.api.ModelItem
import com.visionaidplusplus.mnnllm.android.chat.GenerateResultProcessor
import com.visionaidplusplus.mnnllm.android.llm.ChatService
import com.visionaidplusplus.mnnllm.android.llm.ChatSession
import com.visionaidplusplus.mnnllm.android.llm.GenerateProgressListener
import com.visionaidplusplus.mnnllm.android.model.ModelTypeUtils
import com.visionaidplusplus.mnnllm.android.model.ModelUtils
import com.visionaidplusplus.mnnllm.android.modelist.ModelListManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SharedAIManager {
    private const val TAG = "SharedAIManager"
    var chatSession: ChatSession? = null
    var isInitialized = false
    var currentModelId: String? = null
    var initializationError: String? = null

    suspend fun loadSelectedModel(context: Context, modelItem: ModelItem) = withContext(Dispatchers.IO) {
        if (isInitialized && chatSession != null && currentModelId == modelItem.modelId) return@withContext

        try {
            val chatService = ChatService.provide()
            val configPath = ModelUtils.getConfigPathForModel(modelItem)
            
            chatSession?.release()
            chatSession = chatService.createSession(
                modelItem.modelId!!, modelItem.modelName!!, null, null, configPath, true
            )
            chatSession?.load()
            
            isInitialized = true
            currentModelId = modelItem.modelId
            initializationError = null
            Log.d(TAG, "MNN Vision AI Initialized successfully with ${modelItem.modelName}")

        } catch (e: Exception) {
            isInitialized = false
            initializationError = e.message
            Log.e(TAG, "Initialization failed", e)
            throw Exception("AI Initialization failed: ${e.message}")
        }
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized && chatSession != null) return@withContext

        try {
            val models = ModelListManager.getModelIdModelMap().values
            val visionModelInfo = models.firstOrNull { item ->
                item.modelId?.let { ModelTypeUtils.isVisualModel(it) } == true
            }

            if (visionModelInfo == null) {
                throw Exception("No Vision model downloaded. Please download one from the Market.")
            }
            
            loadSelectedModel(context, visionModelInfo)

        } catch (e: Exception) {
            isInitialized = false
            initializationError = e.message
            Log.e(TAG, "Initialization failed", e)
            throw Exception("AI Initialization failed: ${e.message}")
        }
    }

    var isGenerating = false

    suspend fun analyzeImageStream(
        bitmap: Bitmap,
        prompt: String,
        context: Context,
        onProgressUpdate: (String) -> Unit,
        onTtsSpeak: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!isInitialized || chatSession == null) {
            withContext(Dispatchers.Main) { onProgressUpdate("Error: AI not ready. $initializationError") }
            return@withContext
        }
        
        if (isGenerating) return@withContext
        isGenerating = true

        try {
            var processBitmap = bitmap
            val isNavigation = prompt.contains("navigation assistant")
            
            if (isNavigation) {
                // Scale down significantly for navigation to process extremely fast
                val maxDim = 384f
                val scale = Math.min(maxDim / processBitmap.width, maxDim / processBitmap.height)
                if (scale < 1.0f) {
                    processBitmap = Bitmap.createScaledBitmap(processBitmap, (processBitmap.width * scale).toInt(), (processBitmap.height * scale).toInt(), true)
                }
                
                // Limit the generation output tokens for fast but complete responses
                if (chatSession is com.visionaidplusplus.mnnllm.android.llm.LlmSession) {
                    (chatSession as com.visionaidplusplus.mnnllm.android.llm.LlmSession).updateMaxNewTokens(100)
                }
            } else {
                // Restore tokens for standard modes
                if (chatSession is com.visionaidplusplus.mnnllm.android.llm.LlmSession) {
                    (chatSession as com.visionaidplusplus.mnnllm.android.llm.LlmSession).updateMaxNewTokens(200)
                }
            }

            // Save bitmap to temp file to feed into MNN ChatSession which expects path
            val tempFile = File(context.cacheDir, "temp_vision_img.jpg")
            val fos = FileOutputStream(tempFile)
            // Lower JPEG quality slightly for navigation to further reduce I/O and parse time
            processBitmap.compress(Bitmap.CompressFormat.JPEG, if (isNavigation) 70 else 90, fos)
            fos.close()

            val promptWithImage = "<img>${tempFile.absolutePath}</img>$prompt"
            
            val resultProcessor = GenerateResultProcessor()
            resultProcessor.generateBegin()
            
            var ttsBuffer = StringBuilder()
            var lastNormalLength = 0

            chatSession?.generate(promptWithImage, mapOf(), object : GenerateProgressListener {
                override fun onProgress(progress: String?): Boolean {
                    if (!isGenerating) return true

                    resultProcessor.process(progress)
                    val displayResult = resultProcessor.getDisplayResult()
                    val normalOutput = resultProcessor.getNormalOutput()

                    onProgressUpdate(displayResult)

                    if (normalOutput.length > lastNormalLength) {
                        val outputChunk = normalOutput.substring(lastNormalLength)
                        lastNormalLength = normalOutput.length
                        ttsBuffer.append(outputChunk)

                        val delimiters = "[.,!。，！？?\n、：；:]".toRegex()
                        if (delimiters.containsMatchIn(ttsBuffer.toString())) {
                            val textToSpeak = ttsBuffer.toString()
                            ttsBuffer.clear()
                            onTtsSpeak(textToSpeak)
                        }
                    }
                    
                    // CRITICAL: return false to continue generation. true aborts generation!
                    return false
                }
            })

            // Speak remaining buffer if any
            if (ttsBuffer.isNotEmpty()) {
                onTtsSpeak(ttsBuffer.toString())
            }

            // Cleanup temp file
            if (tempFile.exists()) { tempFile.delete() }
            
        } catch (e: Exception) {
            Log.e(TAG, "Analysis error", e)
            withContext(Dispatchers.Main) { onProgressUpdate("Error: ${e.message}") }
        } finally {
            isGenerating = false
        }
    }

    fun buildNavigationPrompt(context: Context): String {
        return PromptManager.getNavigationPrompt(context)
    }

    fun buildExplanationPrompt(context: Context): String {
        return PromptManager.getVisionPrompt(context)
    }

    fun shutdown() {
        isGenerating = false
        chatSession?.release()
        chatSession = null
        isInitialized = false
    }
}
