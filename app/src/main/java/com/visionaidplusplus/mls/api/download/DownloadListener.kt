// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 alibaba Group Holding Limited All rights reserved.
package com.visionaidplusplus.mls.api.download

interface DownloadListener {
    fun onDownloadStart(modelId: String)
    fun onDownloadProgress(modelId: String, downloadInfo: DownloadInfo)
    fun onDownloadFinished(modelId: String, path: String)
    fun onDownloadFailed(modelId: String, e: Exception)
    fun onDownloadPaused(modelId: String) {}
    fun onDownloadFileRemoved(modelId: String) {}
    fun onDownloadTotalSize(modelId: String, totalSize: Long) {}
    fun onDownloadHasUpdate(modelId: String, downloadInfo: DownloadInfo) {}
}
