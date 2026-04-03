// Created by ruoyi.sjd on 2025/5/14.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.visionaidplusplus.mnnllm.android.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.annotation.IdRes
import com.visionaidplusplus.mnnllm.android.R

class BottomTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : com.google.android.material.card.MaterialCardView(context, attrs, defStyle) {

    enum class Tab(@IdRes val viewId: Int) {
        LOCAL_MODELS(R.id.tab_local_models),
        MODEL_MARKET(R.id.tab_model_market)
    }

    private var listener: ((Tab) -> Unit)? = null

    private val tabLocalModels: LinearLayout
    private val tabModelMarket: LinearLayout

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.view_bottom_tab_bar, this, true)

        tabLocalModels      = findViewById(R.id.tab_local_models)
        tabModelMarket  = findViewById(R.id.tab_model_market)

        tabLocalModels.setOnClickListener { select(Tab.LOCAL_MODELS) }
        tabModelMarket.setOnClickListener { select(Tab.MODEL_MARKET) }
        select(Tab.LOCAL_MODELS)
    }

    fun setOnTabSelectedListener(block: (Tab) -> Unit) {
        listener = block
    }

    fun getSelectedTab(): Tab {
        return when {
            tabLocalModels.isSelected -> Tab.LOCAL_MODELS
            tabModelMarket.isSelected -> Tab.MODEL_MARKET
            else -> Tab.LOCAL_MODELS
        }
    }

    fun select(tab: Tab) {
        tabLocalModels.isSelected     = (tab == Tab.LOCAL_MODELS)
        tabModelMarket.isSelected = (tab == Tab.MODEL_MARKET)

        // Find and update icon/text selected states if they exist
        findView<ImageView>(R.id.icon_local_models)?.isSelected = tabLocalModels.isSelected
        findView<TextView>(R.id.text_local_models)?.isSelected = tabLocalModels.isSelected
        findView<ImageView>(R.id.icon_model_market)?.isSelected = tabModelMarket.isSelected
        findView<TextView>(R.id.text_model_market)?.isSelected = tabModelMarket.isSelected

        listener?.invoke(tab)
    }

    private inline fun <reified T : View> findView(@IdRes id: Int): T? = findViewById(id)
}
