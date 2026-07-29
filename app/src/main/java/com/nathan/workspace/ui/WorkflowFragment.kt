package com.nathan.workspace.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nathan.workspace.R
import com.nathan.workspace.api.JobInfo
import com.nathan.workspace.api.StepInfo
import com.nathan.workspace.databinding.FragmentWorkflowBinding
import com.nathan.workspace.databinding.ItemHistoryBinding
import com.nathan.workspace.databinding.ItemStepBinding
import com.nathan.workspace.viewmodel.LogEntry
import com.nathan.workspace.viewmodel.WorkflowUiState
import com.nathan.workspace.viewmodel.WorkflowViewModel
import kotlinx.coroutines.launch

class WorkflowFragment : Fragment() {

    private var _binding: FragmentWorkflowBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkflowViewModel by activityViewModels()
    private var token: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
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

        binding.btnStart.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.isBlank()) return@setOnClickListener
            binding.etCode.setText("")
            viewModel.startRun(code, token)
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cancelRun(token)
        }

        binding.btnRefreshActive.setOnClickListener {
            viewModel.refreshActiveRun()
            showToast("Refreshing...")
        }

        binding.btnViewGithub.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is WorkflowUiState.Running) {
                openInBrowser(state.htmlUrl)
            }
        }

        binding.btnCopyLogs.setOnClickListener {
            val state = viewModel.uiState.value
            val logs = if (state is WorkflowUiState.Running) state.logs else ""
            copyToClipboard(logs)
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all workflow history?")
                .setPositiveButton("Clear") { _, _ -> viewModel.clearHistory() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is WorkflowUiState.Idle -> renderIdle()
                            is WorkflowUiState.Starting -> renderStarting(state)
                            is WorkflowUiState.Running -> renderRunning(state)
                            is WorkflowUiState.Completed -> renderCompleted(state)
                            is WorkflowUiState.Error -> renderError(state)
                        }
                    }
                }
                launch {
                    viewModel.elapsedTime.collect { time ->
                        binding.tvActiveElapsed.text = time
                    }
                }
                launch {
                    viewModel.history.collect { entries ->
                        renderHistory(entries)
                    }
                }
            }
        }
    }

    private fun renderIdle() {
        if (_binding == null) return

        binding.cardInput.isVisible = true
        binding.cardActive.isVisible = false
        binding.cardActive.alpha = 1f
        binding.cardActive.translationY = 0f
        binding.cardInput.alpha = 1f
        binding.cardInput.translationY = 0f

        binding.layoutRepoInfo.isVisible = true
        binding.layoutUserInfo.isVisible = true
        binding.layoutRunInfo.isVisible = false

        binding.btnStart.text = "Start Workflow"
        binding.btnStart.isEnabled = true

        val hasHistory = viewModel.history.value.isNotEmpty()
        binding.layoutHistoryHeader.isVisible = hasHistory
        binding.layoutHistory.isVisible = hasHistory
        binding.btnClearHistory.isVisible = hasHistory
        binding.tvEmpty.isVisible = !hasHistory && viewModel.activeRun.value == null
    }

    private fun renderStarting(@Suppress("UNUSED_PARAMETER") state: WorkflowUiState.Starting) {
        if (_binding == null) return

        binding.layoutRepoInfo.isVisible = false
        binding.layoutUserInfo.isVisible = false
        binding.layoutRunInfo.isVisible = true
        binding.tvRunTitle.text = "Starting..."
        binding.chipStatus.text = "QUEUED"
        binding.chipStatus.setBackgroundResource(R.drawable.bg_chip_queued)

        animateCardTransition(binding.cardInput, binding.cardActive)

        binding.tvActiveRunId.text = "Starting..."
        binding.chipActiveStatus.text = "QUEUED"
        binding.chipActiveStatus.setBackgroundResource(R.drawable.bg_chip_queued)
        binding.layoutSteps.isVisible = false
        binding.tvLogs.text = "Triggering workflow..."
        binding.btnCopyLogs.isVisible = false
    }

    private fun renderRunning(state: WorkflowUiState.Running) {
        if (_binding == null) return

        binding.cardActive.isVisible = true
        binding.cardInput.isVisible = false

        binding.layoutRepoInfo.isVisible = false
        binding.layoutUserInfo.isVisible = false
        binding.layoutRunInfo.isVisible = true
        binding.tvRunTitle.text = "Run #${state.runId}"

        val isQueued = state.status == "queued"
        binding.chipStatus.text = if (isQueued) "QUEUED" else "RUNNING"
        binding.chipStatus.setBackgroundResource(
            if (isQueued) R.drawable.bg_chip_queued else R.drawable.bg_chip_running
        )

        binding.tvActiveRunId.text = "Run #${state.runId}"
        binding.chipActiveStatus.text = if (isQueued) "QUEUED" else "RUNNING"
        binding.chipActiveStatus.setBackgroundResource(
            if (isQueued) R.drawable.bg_chip_queued else R.drawable.bg_chip_running
        )

        binding.tvLogs.text = if (state.logs.isBlank()) {
            if (isQueued) "Waiting for runner..." else "Waiting for logs..."
        } else {
            state.logs
        }
        binding.btnCopyLogs.isVisible = state.logs.isNotBlank()

        renderSteps(state.jobs)

        binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }

        binding.tvEmpty.isVisible = false

        animateStatusChip(binding.chipStatus)
    }

    private fun renderSteps(jobs: List<JobInfo>) {
        if (_binding == null) return
        val container = binding.layoutSteps
        container.removeAllViews()

        if (jobs.isEmpty()) {
            container.isVisible = false
            return
        }

        container.isVisible = true
        for (job in jobs) {
            if (job.steps.isEmpty()) continue

            for (step in job.steps) {
                val itemBinding = ItemStepBinding.inflate(
                    LayoutInflater.from(context), container, true
                )

                val iconRes: Int
                val iconColor: Int
                val chipBg: Int
                val chipText: String

                when (step.status) {
                    "completed" -> {
                        if (step.conclusion == "success") {
                            iconRes = R.drawable.ic_check
                            iconColor = Color.rgb(82, 199, 122)
                            chipBg = R.drawable.bg_chip_success
                            chipText = "OK"
                        } else {
                            iconRes = R.drawable.ic_close
                            iconColor = Color.rgb(255, 138, 128)
                            chipBg = R.drawable.bg_chip_failure
                            chipText = "FAIL"
                        }
                    }
                    "in_progress" -> {
                        iconRes = R.drawable.ic_play
                        iconColor = Color.rgb(100, 181, 246)
                        chipBg = R.drawable.bg_chip_running
                        chipText = "RUN"
                    }
                    "queued" -> {
                        iconRes = R.drawable.ic_code
                        iconColor = Color.rgb(245, 197, 66)
                        chipBg = R.drawable.bg_chip_queued
                        chipText = "PENDING"
                    }
                    else -> {
                        iconRes = R.drawable.ic_code
                        iconColor = Color.rgb(150, 150, 150)
                        chipBg = R.drawable.bg_chip_queued
                        chipText = step.status.uppercase()
                    }
                }

                itemBinding.ivStepIcon.setImageResource(iconRes)
                itemBinding.ivStepIcon.setColorFilter(iconColor)
                itemBinding.tvStepName.text = step.name
                itemBinding.tvStepStatus.text = chipText
                itemBinding.tvStepStatus.setBackgroundResource(chipBg)
            }
        }
    }

    private fun renderCompleted(state: WorkflowUiState.Completed) {
        if (_binding == null) return

        val entry = state.entry
        val isSuccess = entry.run.conclusion == "success"
        val isCancelled = entry.run.conclusion == "cancelled" || entry.run.conclusion == "canceled"

        binding.layoutRunInfo.isVisible = true
        binding.tvRunTitle.text = "Run #${entry.run.id}"
        binding.chipStatus.text = when {
            isSuccess -> "SUCCESS"
            isCancelled -> "CANCELLED"
            else -> "FAILURE"
        }
        binding.chipStatus.setBackgroundResource(
            when {
                isSuccess -> R.drawable.bg_chip_success
                isCancelled -> R.drawable.bg_chip_cancelled
                else -> R.drawable.bg_chip_failure
            }
        )

        binding.chipActiveStatus.text = when {
            isSuccess -> "SUCCESS"
            isCancelled -> "CANCELLED"
            else -> "FAILURE"
        }
        binding.chipActiveStatus.setBackgroundResource(
            when {
                isSuccess -> R.drawable.bg_chip_success
                isCancelled -> R.drawable.bg_chip_cancelled
                else -> R.drawable.bg_chip_failure
            }
        )
        binding.progressActive.isVisible = false

        binding.tvLogs.text = entry.logs.ifBlank { "No logs available" }
        binding.btnCopyLogs.isVisible = entry.logs.isNotBlank()
        binding.layoutSteps.isVisible = false

        binding.cardActive.postDelayed({
            if (_binding != null) {
                renderIdle()
            }
        }, 3000)
    }

    private fun renderError(state: WorkflowUiState.Error) {
        if (_binding == null) return
        showToast(state.message)
        if (viewModel.activeRun.value == null) {
            renderIdle()
        }
    }

    private fun animateCardTransition(hide: View, show: View) {
        hide.animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                hide.isVisible = false
                show.alpha = 0f
                show.translationY = 20f
                show.isVisible = true
                show.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private var isChipAnimating = false

    private fun animateStatusChip(chip: View) {
        if (isChipAnimating) return
        isChipAnimating = true
        chip.animate()
            .translationYBy(-4f)
            .setDuration(600)
            .withEndAction {
                chip.animate()
                    .translationYBy(4f)
                    .setDuration(600)
                    .withEndAction { isChipAnimating = false }
                    .start()
            }
            .start()
    }

    private fun renderHistory(entries: List<LogEntry>) {
        if (_binding == null) return

        binding.layoutHistory.removeAllViews()

        if (entries.isEmpty()) {
            binding.layoutHistory.isVisible = false
            binding.layoutHistoryHeader.isVisible = false
            binding.btnClearHistory.isVisible = false
            if (viewModel.activeRun.value == null) {
                binding.tvEmpty.isVisible = true
            }
            return
        }

        binding.layoutHistoryHeader.isVisible = true
        binding.layoutHistory.isVisible = true
        binding.btnClearHistory.isVisible = true
        binding.tvEmpty.isVisible = false
        binding.tvHistoryCount.text = "(${entries.size})"

        entries.forEachIndexed { index, entry ->
            val itemBinding = ItemHistoryBinding.inflate(
                LayoutInflater.from(context), binding.layoutHistory, true
            )

            val isSuccess = entry.run.conclusion == "success"
            val isCancelled = entry.run.conclusion == "cancelled" || entry.run.conclusion == "canceled"

            val iconRes = when {
                isSuccess -> R.drawable.ic_check
                isCancelled -> R.drawable.ic_close
                else -> R.drawable.ic_close
            }
            val iconColor = when {
                isSuccess -> Color.rgb(82, 199, 122)
                isCancelled -> Color.rgb(245, 197, 66)
                else -> Color.rgb(255, 138, 128)
            }
            val iconBgColor = when {
                isSuccess -> 0x2052C77A
                isCancelled -> 0x20F5C542
                else -> 0x20FF8A80
            }

            itemBinding.ivIcon.setImageResource(iconRes)
            itemBinding.ivIcon.setColorFilter(iconColor)
            itemBinding.vStatusBg.backgroundTintList =
                android.content.res.ColorStateList.valueOf(iconBgColor)

            itemBinding.tvTitle.text = "Run #${entry.run.id}"

            val conclusionText = when {
                isSuccess -> "SUCCESS"
                isCancelled -> "CANCELLED"
                else -> entry.run.conclusion?.uppercase() ?: "UNKNOWN"
            }
            itemBinding.tvConclusion.text = conclusionText
            itemBinding.tvConclusion.setBackgroundResource(
                when {
                    isSuccess -> R.drawable.bg_chip_success
                    isCancelled -> R.drawable.bg_chip_cancelled
                    else -> R.drawable.bg_chip_failure
                }
            )

            itemBinding.tvTime.text = entry.endedAt

            if (entry.logs.isNotBlank()) {
                val preview = entry.logs.take(200).replace("\n", " ").trim()
                itemBinding.tvLogPreview.text = preview
                itemBinding.tvLogPreview.isVisible = true
                itemBinding.tvLogPreview.setOnClickListener {
                    showLogsDialog(entry)
                }
            } else {
                itemBinding.tvLogPreview.isVisible = false
            }

            itemBinding.btnView.setOnClickListener {
                openInBrowser(entry.run.htmlUrl)
            }

            itemBinding.btnRerun.setOnClickListener {
                viewModel.reRunRun(token, entry)
            }

            itemBinding.btnDelete.setOnClickListener {
                showDeleteConfirmDialog(index, entry)
            }

            itemBinding.root.scaleX = 0.95f
            itemBinding.root.scaleY = 0.95f
            itemBinding.root.alpha = 0f
            itemBinding.root.post {
                itemBinding.root.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(250)
                    .setStartDelay((index * 30).toLong())
                    .start()
            }
        }
    }

    private fun showDeleteConfirmDialog(index: Int, entry: LogEntry) {
        val options = arrayOf(
            "Delete from history only",
            "Delete from GitHub too (run + logs)"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Run #${entry.run.id}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.deleteHistoryEntry(token, index)
                        showToast("Run removed from history")
                    }
                    1 -> {
                        viewModel.deleteHistoryEntryAndRun(token, index)
                        showToast("Run deleted from GitHub and history")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openInBrowser(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { }
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Workflow Logs", text)
        clipboard.setPrimaryClip(clip)
        showToast("Logs copied to clipboard")
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showLogsDialog(entry: LogEntry) {
        val logText = entry.logs.ifBlank { "No logs available" }
        val scrollView = ScrollView(requireContext()).apply {
            val tv = TextView(context).apply {
                text = logText
                setTextColor(Color.parseColor("#e1e2e9"))
                setBackgroundColor(Color.parseColor("#191c20"))
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setPadding(24, 24, 24, 24)
                setLineSpacing(0f, 1.2f)
            }
            addView(tv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, 0)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Run #${entry.run.id} - Logs")
            .setView(scrollView)
            .setPositiveButton("Copy") { _, _ -> copyToClipboard(logText) }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "WorkflowFragment"
    }
}
