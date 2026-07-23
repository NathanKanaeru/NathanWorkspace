package com.nathan.workspace.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nathan.workspace.api.GitHubApi
import com.nathan.workspace.databinding.FragmentWorkflowBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkflowFragment : Fragment() {

    private var _binding: FragmentWorkflowBinding? = null
    private val binding get() = _binding!!
    private var token: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorkflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("app", Context.MODE_PRIVATE)
        token = prefs.getString("github_token", "") ?: ""

        val login = prefs.getString("github_login", "")
        val name = prefs.getString("github_name", "")
        binding.tvUserName.text = name?.ifBlank { login }
        binding.tvUserLogin.text = "@$login"

        binding.btnRun.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.isBlank()) {
                showStatus("Masukkan CRD code terlebih dahulu")
                return@setOnClickListener
            }
            triggerWorkflow(code)
        }
    }

    private fun triggerWorkflow(code: String) {
        binding.btnRun.isEnabled = false
        binding.progressWorkflow.visibility = View.VISIBLE
        binding.cardStatus.visibility = View.VISIBLE
        binding.tvStatusDetail.text = "Mengirim trigger ke GitHub..."

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                GitHubApi.triggerWorkflow(token, code)
            }

            binding.progressWorkflow.visibility = View.GONE
            binding.btnRun.isEnabled = true

            result.fold(
                onSuccess = { msg -> binding.tvStatusDetail.text = msg },
                onFailure = { error -> binding.tvStatusDetail.text = "Gagal: ${error.message}" }
            )
        }
    }

    private fun showStatus(msg: String) {
        binding.cardStatus.visibility = View.VISIBLE
        binding.tvStatusDetail.text = msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
