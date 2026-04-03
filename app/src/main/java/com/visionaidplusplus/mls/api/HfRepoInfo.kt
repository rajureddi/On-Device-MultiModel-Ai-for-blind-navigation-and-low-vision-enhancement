// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 alibaba Group Holding Limited. All rights reserved.
package com.visionaidplusplus.mls.api

import com.google.gson.annotations.SerializedName

class HfRepoInfo {
    class SiblingItem {
        @JvmField
        var rfilename: String? = null
    }

    // Getters and Setters
    @JvmField
    var modelId: String? = null
    var revision: String? = null
    @JvmField
    var sha: String? = null
    @SerializedName("siblings")
    var siblings: MutableList<SiblingItem> = ArrayList()
}
