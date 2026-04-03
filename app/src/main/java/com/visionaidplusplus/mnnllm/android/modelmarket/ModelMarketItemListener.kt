package com.visionaidplusplus.mnnllm.android.modelmarket

interface ModelMarketItemListener {
    fun onDownloadOrResumeClicked(item: ModelMarketItemWrapper)
    fun onPauseClicked(item: ModelMarketItemWrapper)
    fun onActionClicked(item: ModelMarketItemWrapper)
    fun onDeleteClicked(item: ModelMarketItemWrapper)
    fun onUpdateClicked(item: ModelMarketItemWrapper)
    fun onDefaultVoiceModelChanged(item: ModelMarketItemWrapper)
} 