package com.visionaidplusplus.mnnllm.android.capture

interface CaptureContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun updateExplanation(text: String)
        fun showError(message: String)
    }

    interface Presenter {
        fun start()
        fun stop()
        fun analyzeImage(imagePath: String, prompt: String, model: com.visionaidplusplus.mls.api.ModelItem?)
    }
}
