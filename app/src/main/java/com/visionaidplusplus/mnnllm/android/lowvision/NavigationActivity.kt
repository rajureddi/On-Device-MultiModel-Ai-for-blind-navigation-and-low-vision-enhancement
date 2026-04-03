package com.visionaidplusplus.mnnllm.android.lowvision

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
import java.util.*

class NavigationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private lateinit var controller: LifecycleCameraController
    private lateinit var viewFinder: PreviewView
    private lateinit var btnScan: ImageButton
    private lateinit var tvPrompt: TextView
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
        setContentView(R.layout.activity_navigation)
        
        tts = TextToSpeech(this, this)
        
        viewFinder = findViewById(R.id.view_finder)
        btnScan = findViewById(R.id.btn_scan)
        tvPrompt = findViewById(R.id.tv_prompt)

        controller = LifecycleCameraController(this)

        val scanAction = {
            tts?.stop()
            tvPrompt.text = "Checking..."
            takePicture()
        }

        viewFinder.setOnClickListener { scanAction() }
        btnScan.setOnClickListener { scanAction() }

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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }
    
    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun takePicture() {
        controller.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    
                    scope.launch(Dispatchers.IO) {
                        try {
                            val prompt = SharedAIManager.buildNavigationPrompt(this@NavigationActivity)
                            SharedAIManager.analyzeImageStream(
                                bitmap = bitmap,
                                prompt = prompt,
                                context = this@NavigationActivity,
                                onProgressUpdate = { text ->
                                    launch(Dispatchers.Main) { tvPrompt.text = text }
                                },
                                onTtsSpeak = { chunk ->
                                    speak(chunk)
                                }
                            )
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) {
                                tvPrompt.text = "Error scanning"
                                speak("Error scanning")
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    tvPrompt.text = "Error capturing image"
                    speak("Error capturing image")
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
