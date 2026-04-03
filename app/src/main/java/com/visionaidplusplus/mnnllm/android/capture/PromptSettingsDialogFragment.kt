package com.visionaidplusplus.mnnllm.android.capture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.visionaidplusplus.mnnllm.android.R
import com.visionaidplusplus.mnnllm.android.service.PromptManager

class PromptSettingsDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_prompt_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val tvDefaultNav = view.findViewById<TextView>(R.id.tv_default_nav_prompt)
        val tvDefaultVision = view.findViewById<TextView>(R.id.tv_default_vision_prompt)
        val switchCustomNav = view.findViewById<SwitchMaterial>(R.id.switch_custom_nav)
        val switchCustomVision = view.findViewById<SwitchMaterial>(R.id.switch_custom_vision)
        val tilCustomNav = view.findViewById<TextInputLayout>(R.id.til_custom_nav)
        val tilCustomVision = view.findViewById<TextInputLayout>(R.id.til_custom_vision)
        val etCustomNav = view.findViewById<TextInputEditText>(R.id.et_custom_nav)
        val etCustomVision = view.findViewById<TextInputEditText>(R.id.et_custom_vision)
        val btnSave = view.findViewById<Button>(R.id.btn_save)

        // Load current data
        tvDefaultNav.text = PromptManager.DEFAULT_NAV_PROMPT
        tvDefaultVision.text = PromptManager.DEFAULT_VISION_PROMPT

        val useCustomNav = PromptManager.isUsingCustomNavigation(context)
        val useCustomVision = PromptManager.isUsingCustomVision(context)

        switchCustomNav.isChecked = useCustomNav
        switchCustomVision.isChecked = useCustomVision
        
        tilCustomNav.visibility = if (useCustomNav) View.VISIBLE else View.GONE
        tilCustomVision.visibility = if (useCustomVision) View.VISIBLE else View.GONE

        etCustomNav.setText(PromptManager.getCustomNavigationPrompt(context))
        etCustomVision.setText(PromptManager.getCustomVisionPrompt(context))

        // Logic for toggles
        switchCustomNav.setOnCheckedChangeListener { _, isChecked ->
            tilCustomNav.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        switchCustomVision.setOnCheckedChangeListener { _, isChecked ->
            tilCustomVision.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnSave.setOnClickListener {
            PromptManager.setUseCustomNavigation(context, switchCustomNav.isChecked)
            PromptManager.setUseCustomVision(context, switchCustomVision.isChecked)
            PromptManager.setCustomNavigationPrompt(context, etCustomNav.text.toString())
            PromptManager.setCustomVisionPrompt(context, etCustomVision.text.toString())
            
            android.widget.Toast.makeText(context, "Settings Saved", android.widget.Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    companion object {
        const val TAG = "PromptSettingsDialog"
        fun newInstance() = PromptSettingsDialogFragment()
    }
}
