// Created by ruoyi.sjd on 2025/2/11.
// Copyright (c) 2024 alibaba Group Holding Limited All rights reserved.
package com.visionaidplusplus.mls.api.source
import com.visionaidplusplus.mls.api.source.ModelSources.Companion.get

class RepoConfig(var modelScopePath: String,
                 private var huggingFacePath: String,
                 var modelId: String) {
    fun repositoryPath(): String {
        return if (get().remoteSourceType == ModelSources.ModelSourceType.HUGGING_FACE)
            huggingFacePath
            else
            modelScopePath
    }
}