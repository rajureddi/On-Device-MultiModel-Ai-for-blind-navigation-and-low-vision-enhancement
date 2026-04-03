package com.visionaidplusplus.mnnllm.android.modelist

import com.visionaidplusplus.mls.api.ModelItem

interface ModelItemListener {
    fun onItemClicked(modelItem: ModelItem)
    fun onItemLongClicked(modelItem: ModelItem): Boolean
    fun onItemDeleted(modelItem: ModelItem)
    fun onItemUpdate(modelItem: ModelItem)
}
