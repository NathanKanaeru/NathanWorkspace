package com.nathan.workspace.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.nathan.workspace.R
import com.nathan.workspace.databinding.FragmentWorkflowBinding
import com.nathan.workspace.databinding.ItemHistoryBinding
import com.nathan.workspace.viewmodel.LogEntry
import com.nathan.workspace.viewmodel.WorkflowViewModel
import kotlinx.coroutines.launch

class WorkflowFragment : Fragment() {

    private var _binding: FragmentWorkflowBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkflowViewModel by activityViewModels()
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

        binding.fabRun.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.isBlank()) return@setOnClickListener
            binding.etCode.setText("")
            viewModel.startRun(code, token)
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cancelRun(token)
        }

        lifecycleScope.launch {
            viewModel.activeRun.collect { active ->
                if (active != null && active.isRunning) {
                    showRunningState(active.run.id)
                    binding.tvLogs.text = if (active.logs.isBlank()) "Waiting for logs..." else active.logs
                    binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
                } else {
                    showIdleState()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.history.collect { entries ->
                binding.layoutHistory.removeAllViews()
                if (entries.isEmpty()) {
                    binding.tvHistoryHeader.isVisible = false
                    binding.layoutHistory.isVisible = false
                    binding.tvEmpty.isVisible = viewModel.activeRun.value == null
                } else {
                    binding.tvHistoryHeader.isVisible = true
                    binding.layoutHistory.isVisible = true
                    binding.tvEmpty.isVisible = false

                    entries.forEachIndexed { index, entry ->
                        val itemBinding = ItemHistoryBinding.inflate(LayoutInflater.from(context), binding.layoutHistory, true)
                        val isSuccess = entry.run.conclusion == "success"

                        itemBinding.ivIcon.setImageResource(
                            if (isSuccess) R.drawable.ic_check else R.drawable.ic_close
                        )
                        itemBinding.ivIcon.setColorFilter(
                            if (isSuccess) Color.rgb(76, 175, 80) else Color.rgb(244, 67, 54)
                        )

                        itemBinding.tvTitle.text = "Run #${entry.run.id} - ${entry.run.conclusion?.uppercase() ?: "UNKNOWN"}"
                        itemBinding.tvTime.text = entry.endedAt

                        itemBinding.btnDelete.setOnClickListener {
                            viewModel.deleteHistoryEntry(token, index)
                        }
                    }
                }
            }
        }

        if (viewModel.activeRun.value != null) showRunningState(viewModel.activeRun.value!!.run.id)
    }

    private fun showRunningState(@Suppress("UNUSED_PARAMETER") runId: Long) {
        binding.cardLogs.isVisible = true
        binding.cardInput.isEnabled = false
        binding.tvEmpty.isVisible = false
        binding.fabRun.text = "Running..."
        binding.fabRun.isEnabled = false
    }

    private fun showIdleState() {
        binding.cardLogs.isVisible = false
        binding.cardInput.isEnabled = true
        binding.fabRun.text = "Start Workflow"
        binding.fabRun.isEnabled = true
        val hasHistory = viewModel.history.value.isNotEmpty()
        binding.tvEmpty.isVisible = !hasHistory
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
