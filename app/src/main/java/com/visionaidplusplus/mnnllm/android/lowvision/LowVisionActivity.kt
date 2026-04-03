package com.visionaidplusplus.mnnllm.android.lowvision

import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.visionaidplusplus.mnnllm.android.R
import com.visionaidplusplus.mnnllm.android.service.SharedAIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class LowVisionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private lateinit var controller: LifecycleCameraController
    private lateinit var viewFinder: PreviewView
    private lateinit var ivLivePreview: ImageView
    private lateinit var ivCaptured: ImageView
    private lateinit var btnCaptureEnhance: Button
    private lateinit var llBottomHalf: LinearLayout
    private lateinit var tvOcrResult: TextView
    private lateinit var tvAiResult: TextView
    private lateinit var btnRetake: Button
    private lateinit var btnExtract: Button
    private lateinit var btnAiExplain: Button
    private lateinit var spinnerMode: Spinner

    private var rawBitmap: Bitmap? = null
    private var capturedBitmap: Bitmap? = null
    private var currentConfig = ImageProcessor.EnhancementConfig()
    private var ocrText = ""
    private var explanationText = ""
    private var isProcessingPreview = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            android.widget.Toast.makeText(this, "Camera permission is required", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_low_vision)

        ImageProcessor.initOpenCV()
        tts = TextToSpeech(this, this)

        viewFinder = findViewById(R.id.view_finder)
        ivLivePreview = findViewById(R.id.iv_live_preview)
        ivCaptured = findViewById(R.id.iv_captured)
        btnCaptureEnhance = findViewById(R.id.btn_capture_enhance)
        llBottomHalf = findViewById(R.id.ll_bottom_half)
        tvOcrResult = findViewById(R.id.tv_ocr_result)
        tvAiResult = findViewById(R.id.tv_ai_result)
        btnRetake = findViewById(R.id.btn_retake)
        btnExtract = findViewById(R.id.btn_extract)
        btnAiExplain = findViewById(R.id.btn_ai_explain)
        spinnerMode = findViewById(R.id.spinner_mode)

        controller = LifecycleCameraController(this)

        // Try to force frames per second to be smoother / 60 FPS
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.initializationFuture.addListener({
            // CameraX doesn't have a direct FPS setter, so we enforce STRATEGY_KEEP_ONLY_LATEST
            // so we can drop frames safely but receive them extremely quickly
        }, ContextCompat.getMainExecutor(this))

        controller.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            if (currentConfig.mode != ImageProcessor.EnhancementMode.ORIGINAL && rawBitmap == null && !isProcessingPreview) {
                isProcessingPreview = true
                var bmp = imageProxy.toBitmap()

                // Correctly rotate the bitmap based on sensor orientation, otherwise it will be sideways
                val rotation = imageProxy.imageInfo.rotationDegrees
                if (rotation != 0) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(rotation.toFloat())
                    bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                }

                scope.launch(Dispatchers.IO) {
                    val enhanced = ImageProcessor.enhance(bmp, currentConfig)
                    withContext(Dispatchers.Main) {
                        if (currentConfig.mode != ImageProcessor.EnhancementMode.ORIGINAL && rawBitmap == null) {
                            ivLivePreview.visibility = View.VISIBLE
                            ivLivePreview.setImageBitmap(enhanced)
                        } else {
                            ivLivePreview.visibility = View.GONE
                        }
                        isProcessingPreview = false
                    }
                }
            } else if (currentConfig.mode == ImageProcessor.EnhancementMode.ORIGINAL) {
                ivLivePreview.visibility = View.GONE
            }
            imageProxy.close()
        }

        setupModeSpinner()

        btnCaptureEnhance.setOnClickListener {
            controller.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = image.toBitmap()
                        rawBitmap = bmp  // Keep original raw image
                        image.close()
                        tts?.stop()

                        applyEnhancementFilter()
                    }

                    override fun onError(e: ImageCaptureException) {}
                }
            )
        }

        btnRetake.setOnClickListener {
            rawBitmap = null
            capturedBitmap = null
            ocrText = ""
            explanationText = ""
            tts?.stop()
            viewFinder.visibility = View.VISIBLE
            ivLivePreview.visibility = if (currentConfig.mode != ImageProcessor.EnhancementMode.ORIGINAL) View.VISIBLE else View.GONE
            ivCaptured.visibility = View.GONE
            btnCaptureEnhance.visibility = View.VISIBLE
            llBottomHalf.visibility = View.GONE
            tvOcrResult.text = "Tap 'EXTRACT' to read text."
            tvAiResult.text = "Tap 'AI EXPLAIN' for AI Vision."
        }

        btnExtract.setOnClickListener {
            if (capturedBitmap == null) return@setOnClickListener
            tts?.stop()
            tvOcrResult.text = "Extracting text..."

            scope.launch(Dispatchers.IO) {
                val result = OcrProcessor.extractText(capturedBitmap!!)  // Returns ProcessedTextResult
                ocrText = cleanOcrText(result.cleanedText)  // Access cleanedText property

                withContext(Dispatchers.Main) {
                    val display = ocrText.ifBlank { "No text found." }
                    tvOcrResult.text = display
                    speak(display)
                }
            }
        }

        btnAiExplain.setOnClickListener {
            if (rawBitmap == null) return@setOnClickListener
            tts?.stop()
            tvAiResult.text = "✨ On Device AI is analyzing..."

            scope.launch(Dispatchers.IO) {
                try {
                    SharedAIManager.analyzeImageStream(
                        bitmap = rawBitmap!!,
                        prompt = SharedAIManager.buildExplanationPrompt(this@LowVisionActivity),
                        context = this@LowVisionActivity,
                        onProgressUpdate = { text ->
                            launch(Dispatchers.Main) {
                                explanationText = text
                                tvAiResult.text = "✨ $explanationText"
                            }
                        },
                        onTtsSpeak = { chunk ->
                            speak(chunk)
                        }
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvAiResult.text = "Error: \${e.message}"
                    }
                }
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        controller.bindToLifecycle(this)
        viewFinder.controller = controller
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun setupModeSpinner() {
        val modes = ImageProcessor.EnhancementMode.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMode.adapter = adapter

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMode = ImageProcessor.EnhancementMode.values()[position]
                if (currentConfig.mode != selectedMode) {
                    currentConfig = currentConfig.copy(mode = selectedMode)
                    if (rawBitmap != null) {
                        applyEnhancementFilter()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyEnhancementFilter() {
        if (rawBitmap == null) return

        scope.launch(Dispatchers.IO) {
            val enhanced = ImageProcessor.enhance(rawBitmap!!, currentConfig)

            withContext(Dispatchers.Main) {
                capturedBitmap = enhanced
                ivCaptured.setImageBitmap(enhanced)
                viewFinder.visibility = View.GONE
                ivLivePreview.visibility = View.GONE
                ivCaptured.visibility = View.VISIBLE
                btnCaptureEnhance.visibility = View.GONE
                llBottomHalf.visibility = View.VISIBLE
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun cleanOcrText(rawText: String): String {
        return rawText
            .replace(Regex("[|@#_~«»\\(\\)\\[\\]\\{\\}]"), "")
            .replace(Regex("(?<=[a-zA-Z])3(?=[a-zA-Z])"), "e")
            .replace(Regex("(?<=[a-zA-Z])0(?=[a-zA-Z])"), "o")
            .replace(Regex("(?<=[a-zA-Z])1(?=[a-zA-Z])"), "i")
            .split(" ")
            .filter { word -> word.length <= 1 || word.any { it.lowercaseChar() in "aeiouy" } }
            .joinToString(" ")
            .replace(Regex("\n+"), "\n")
            .replace(Regex(" +"), " ")
            .trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
