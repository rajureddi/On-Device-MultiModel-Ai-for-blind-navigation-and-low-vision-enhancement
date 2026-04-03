package com.visionaidplusplus.mnnllm.android.modelmarket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import com.visionaidplusplus.mls.api.download.ModelDownloadManager
import com.visionaidplusplus.mnnllm.android.utils.PreferenceUtils
import com.visionaidplusplus.mnnllm.android.R
import com.visionaidplusplus.mnnllm.android.main.MainActivity
import com.visionaidplusplus.mnnllm.android.utils.BaseBottomSheetDialogFragment

class CommunityDownloadDialogFragment : BaseBottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_community_download, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sourceGroup = view.findViewById<RadioGroup>(R.id.source_radio_group)
        val etRepoId = view.findViewById<EditText>(R.id.et_repo_id)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnDownload = view.findViewById<Button>(R.id.btn_download)

        btnCancel.setOnClickListener { dismiss() }

        btnDownload.setOnClickListener {
            val repoId = etRepoId.text.toString().trim()
            if (repoId.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a repository ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefix = when (sourceGroup.checkedRadioButtonId) {
                R.id.rb_modelscope -> "ModelScope/"
                R.id.rb_modelers -> "Modelers/"
                R.id.rb_huggingface -> "HuggingFace/"
                else -> "ModelScope/"
            }

            val fullModelId = if (repoId.startsWith(prefix, ignoreCase = true)) {
                repoId
            } else {
                prefix + repoId
            }

            ModelDownloadManager.getInstance(requireContext()).startDownload(fullModelId)
            PreferenceUtils.addCommunityModel(requireContext(), fullModelId)
            Toast.makeText(requireContext(), "Starting download: $fullModelId", Toast.LENGTH_LONG).show()
            (activity as? MainActivity)?.refreshModelMarket()
            dismiss()
        }
    }

    companion object {
        const val TAG = "CommunityDownloadDialog"
        fun newInstance() = CommunityDownloadDialogFragment()
    }
}
