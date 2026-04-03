package com.visionaidplusplus.mnnllm.android.capture

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.visionaidplusplus.mnnllm.android.R
import com.visionaidplusplus.mnnllm.android.lowvision.LowVisionActivity
import com.visionaidplusplus.mnnllm.android.lowvision.NavigationActivity
import com.visionaidplusplus.mnnllm.android.service.SharedAIManager
import com.visionaidplusplus.mnnllm.android.capture.PromptSettingsDialogFragment
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.visionaidplusplus.mls.api.ModelItem
import com.visionaidplusplus.mnnllm.android.model.ModelTypeUtils
import com.visionaidplusplus.mnnllm.android.modelist.ModelListManager
import com.visionaidplusplus.mnnllm.android.utils.Searchable
import kotlinx.coroutines.launch

class CaptureFragment : Fragment(), Searchable {

    companion object {
        const val TAG = "CaptureFragment"
    }

    private lateinit var btnNavigate: Button
    private lateinit var btnEnhance: Button
    private lateinit var btnPromptSettings: ImageButton
    private lateinit var spinnerModel: Spinner
    private var selectedModel: ModelItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_capture, container, false)
        btnNavigate = view.findViewById(R.id.btn_navigate)
        btnEnhance = view.findViewById(R.id.btn_enhance)
        btnPromptSettings = view.findViewById(R.id.btn_prompt_settings)
        spinnerModel = view.findViewById(R.id.spinner_model)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinner()
    }

    private fun setupSpinner() {
        lifecycleScope.launch {
            ModelListManager.observeModels().collect { wrappers ->
                if (!isAdded) return@collect
                val models = wrappers.map { it.modelItem }.filter { 
                    it.modelId?.let { id -> ModelTypeUtils.isVisualModel(id) } == true 
                }
                
                if (models.isEmpty()) {
                    if (spinnerModel.adapter == null || spinnerModel.adapter.count == 0) {
                        Toast.makeText(requireContext(), "No Vision Models available. Download from Market", Toast.LENGTH_SHORT).show()
                    }
                    return@collect
                }
                
                val modelNames = models.map { it.modelName ?: "Unknown Model" }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modelNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                
                // Save old listener and remove it before setting adapter to avoid unwanted callbacks
                val oldListener = spinnerModel.onItemSelectedListener
                spinnerModel.onItemSelectedListener = null
                
                spinnerModel.adapter = adapter

                val previousSelectionIndex = models.indexOfFirst { it.modelId == selectedModel?.modelId }
                if (previousSelectionIndex != -1) {
                    spinnerModel.setSelection(previousSelectionIndex, false)
                } else if (models.isNotEmpty()) {
                    spinnerModel.setSelection(0, false)
                    val firstModel = models[0]
                    selectedModel = firstModel
                    lifecycleScope.launch {
                        try {
                            SharedAIManager.loadSelectedModel(requireContext(), firstModel)
                        } catch (e: Exception) {
                            android.util.Log.e("CaptureFragment", "Failed to load initial model", e)
                        }
                    }
                }

                spinnerModel.post {
                    spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                            val newModel = models[position]
                            if (selectedModel?.modelId != newModel.modelId) {
                                selectedModel = newModel
                                lifecycleScope.launch {
                                    try {
                                        SharedAIManager.loadSelectedModel(requireContext(), newModel)
                                        Toast.makeText(requireContext(), "Model Switched to ${newModel.modelName}", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(requireContext(), "Failed to load ${newModel.modelName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }
                }
            }
        }

        btnNavigate.setOnClickListener {
            if (SharedAIManager.isInitialized) {
                startActivity(Intent(requireContext(), NavigationActivity::class.java))
            } else {
                Toast.makeText(requireContext(), "Please wait for AI model to load or download one in Market tab", Toast.LENGTH_SHORT).show()
            }
        }

        btnEnhance.setOnClickListener {
            if (SharedAIManager.isInitialized) {
                startActivity(Intent(requireContext(), LowVisionActivity::class.java))
            } else {
                Toast.makeText(requireContext(), "Please wait for AI model to load or download one in Market tab", Toast.LENGTH_SHORT).show()
            }
        }

        btnPromptSettings.setOnClickListener {
            PromptSettingsDialogFragment.newInstance().show(childFragmentManager, PromptSettingsDialogFragment.TAG)
        }
    }

    override fun onSearchQuery(query: String) {}
    override fun onSearchCleared() {}
}
