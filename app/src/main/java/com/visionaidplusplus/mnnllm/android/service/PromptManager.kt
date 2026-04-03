package com.visionaidplusplus.mnnllm.android.service

import android.content.Context
import androidx.preference.PreferenceManager

object PromptManager {
    private const val KEY_CUSTOM_NAV_PROMPT = "custom_navigation_prompt"
    private const val KEY_CUSTOM_VISION_PROMPT = "custom_vision_prompt"
    private const val KEY_USE_CUSTOM_NAV = "use_custom_navigation_prompt"
    private const val KEY_USE_CUSTOM_VISION = "use_custom_vision_prompt"

    val DEFAULT_NAV_PROMPT = """
        System Role: You are a real-time visual navigation assistant for a blind user.
        
        Describe the immediate path ahead for safe navigation.
        
        Mention:
        - The closest obstacles and their general direction.
        - A safe direction to walk or navigate.
        - Keep it very short and conversational, strictly should under 30 words.
        mention the objects distance (0 to 10 METERS) approximately
        - Be direct, calm, and do not apologize or say things are not visible.
        - Output ONLY the navigation instruction.
    """.trimIndent()

    val DEFAULT_VISION_PROMPT = """
        You are assisting a low-vision user.
        Describe the image clearly and calmly.

        Mention:
        - Objects present
        - Any visible text
        - Identify objects
        - read available signs 
        - Dont repeat the same sentence 
        - be human way of describing the image
        - if there is any sign language interpretation symbol convert to english words
        (dont give any path or naviagation just describe)
        Maximum 60 words.
    """.trimIndent()

    fun getNavigationPrompt(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return if (prefs.getBoolean(KEY_USE_CUSTOM_NAV, false)) {
            prefs.getString(KEY_CUSTOM_NAV_PROMPT, DEFAULT_NAV_PROMPT) ?: DEFAULT_NAV_PROMPT
        } else {
            DEFAULT_NAV_PROMPT
        }
    }

    fun getVisionPrompt(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return if (prefs.getBoolean(KEY_USE_CUSTOM_VISION, false)) {
            prefs.getString(KEY_CUSTOM_VISION_PROMPT, DEFAULT_VISION_PROMPT) ?: DEFAULT_VISION_PROMPT
        } else {
            DEFAULT_VISION_PROMPT
        }
    }

    fun setCustomNavigationPrompt(context: Context, prompt: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_CUSTOM_NAV_PROMPT, prompt)
            .apply()
    }

    fun setCustomVisionPrompt(context: Context, prompt: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY_CUSTOM_VISION_PROMPT, prompt)
            .apply()
    }

    fun setUseCustomNavigation(context: Context, use: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(KEY_USE_CUSTOM_NAV, use)
            .apply()
    }

    fun setUseCustomVision(context: Context, use: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(KEY_USE_CUSTOM_VISION, use)
            .apply()
    }

    fun isUsingCustomNavigation(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_CUSTOM_NAV, false)

    fun isUsingCustomVision(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_CUSTOM_VISION, false)
    
    fun getCustomNavigationPrompt(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(KEY_CUSTOM_NAV_PROMPT, DEFAULT_NAV_PROMPT) ?: DEFAULT_NAV_PROMPT

    fun getCustomVisionPrompt(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(KEY_CUSTOM_VISION_PROMPT, DEFAULT_VISION_PROMPT) ?: DEFAULT_VISION_PROMPT
}
