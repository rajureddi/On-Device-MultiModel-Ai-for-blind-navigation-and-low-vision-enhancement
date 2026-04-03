package com.visionaidplusplus.mnnllm.android

object MNN {
    external fun nativeGetVersion(): String

    fun getVersion(): String {
        return nativeGetVersion()
    }

    init {
        System.loadLibrary("mnnllmapp")
    }
}
