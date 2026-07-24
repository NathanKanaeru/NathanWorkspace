package com.nathan.workspace.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nathan.workspace.R
import com.nathan.workspace.databinding.FragmentWorkflowBinding
import com.nathan.workspace.databinding.ItemHistoryBinding
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

        // Start button (embedded in card_input)
        binding.btnStart.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.isBlank()) return@setOnClickListener
            binding.etCode.setText("")
            viewModel.startRun(code, token)
        }

        // Cancel button
        binding.btnCancel.setOnClickListener {
            viewModel.cancelRun(token)
        }

        // View on GitHub button
        binding.btnViewGithub.setOnClickListener {
            val state = viewModel.uiState.value
            if (state is WorkflowUiState.Running) {
                openInBrowser(state.htmlUrl)
            }
        }

        // Copy logs button
        binding.btnCopyLogs.setOnClickListener {
            val state = viewModel.uiState.value
            val logs = if (state is WorkflowUiState.Running) state.logs else ""
            copyToClipboard(logs)
        }

        // Clear history button
        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all workflow history?")
                .setPositiveButton("Clear") { _, _ -> viewModel.clearHistory() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Observe UI state
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

    // ──── STATE RENDERERS ────

    private fun renderIdle() {
        if (_binding == null) return

        // Show input card, hide active card
        binding.cardInput.isVisible = true
        binding.cardActive.isVisible = false
        binding.cardActive.alpha = 1f
        binding.cardActive.translationY = 0f
        binding.cardInput.alpha = 1f
        binding.cardInput.translationY = 0f

        // Show repo info, hide run info in header
        binding.layoutRepoInfo.isVisible = true
        binding.layoutUserInfo.isVisible = true
        binding.layoutRunInfo.isVisible = false

        // Reset button state
        binding.btnStart.text = "Start Workflow"
        binding.btnStart.isEnabled = true

        // History visibility
        val hasHistory = viewModel.history.value.isNotEmpty()
        binding.layoutHistoryHeader.isVisible = hasHistory
        binding.layoutHistory.isVisible = hasHistory
        binding.btnClearHistory.isVisible = hasHistory
        binding.tvEmpty.isVisible = !hasHistory && viewModel.activeRun.value == null
    }

    private fun renderStarting(@Suppress("UNUSED_PARAMETER") state: WorkflowUiState.Starting) {
        if (_binding == null) return

        // Update header to show run info
        binding.layoutRepoInfo.isVisible = false
        binding.layoutUserInfo.isVisible = false
        binding.layoutRunInfo.isVisible = true
        binding.tvRunTitle.text = "Starting..."
        binding.chipStatus.text = "QUEUED"
        binding.chipStatus.setBackgroundResource(R.drawable.bg_chip_queued)

        // Animate: fade out input card, fade in active card
        animateCardTransition(binding.cardInput, binding.cardActive)

        // Show queued state in active card
        binding.tvActiveRunId.text = "Starting..."
        binding.chipActiveStatus.text = "QUEUED"
        binding.chipActiveStatus.setBackgroundResource(R.drawable.bg_chip_queued)
        binding.tvLogs.text = "Triggering workflow..."
        binding.btnCopyLogs.isVisible = false
    }

    private fun renderRunning(state: WorkflowUiState.Running) {
        if (_binding == null) return

        // Ensure active card visible, input card hidden
        binding.cardActive.isVisible = true
        binding.cardInput.isVisible = false

        // Update header
        binding.layoutRepoInfo.isVisible = false
        binding.layoutUserInfo.isVisible = false
        binding.layoutRunInfo.isVisible = true
        binding.tvRunTitle.text = "Run #${state.runId}"

        val isQueued = state.status == "queued"
        binding.chipStatus.text = if (isQueued) "QUEUED" else "RUNNING"
        binding.chipStatus.setBackgroundResource(
            if (isQueued) R.drawable.bg_chip_queued else R.drawable.bg_chip_running
        )

        // Update active card
        binding.tvActiveRunId.text = "Run #${state.runId}"
        binding.chipActiveStatus.text = if (isQueued) "QUEUED" else "RUNNING"
        binding.chipActiveStatus.setBackgroundResource(
            if (isQueued) R.drawable.bg_chip_queued else R.drawable.bg_chip_running
        )

        // Update logs
        binding.tvLogs.text = if (state.logs.isBlank()) {
            if (isQueued) "Waiting for runner..." else "Waiting for logs..."
        } else {
            state.logs
        }
        binding.btnCopyLogs.isVisible = state.logs.isNotBlank()

        // Auto-scroll logs
        binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }

        // Hide empty state
        binding.tvEmpty.isVisible = false

        // Status chip bounce animation
        animateStatusChip(binding.chipStatus)
    }

    private fun renderCompleted(state: WorkflowUiState.Completed) {
        if (_binding == null) return

        val entry = state.entry
        val isSuccess = entry.run.conclusion == "success"
        val isCancelled = entry.run.conclusion == "cancelled" || entry.run.conclusion == "canceled"

        // Update header
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

        // Update active card
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

        // Show logs in active card
        binding.tvLogs.text = entry.logs.ifBlank { "No logs available" }

        // After a brief delay, transition to idle
        binding.cardActive.postDelayed({
            if (_binding != null) {
                renderIdle()
            }
        }, 2500)
    }

    private fun renderError(state: WorkflowUiState.Error) {
        if (_binding == null) return

        // Show snackbar
        android.widget.Toast.makeText(requireContext(), state.message, android.widget.Toast.LENGTH_LONG).show()

        // Return to idle if not already
        if (viewModel.activeRun.value == null) {
            renderIdle()
        }
    }

    // ──── ANIMATIONS ────

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

    // ──── HISTORY ────

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

            // Status icon
            val iconRes = when {
                isSuccess -> R.drawable.ic_check
                isCancelled -> R.drawable.ic_close
                else -> R.drawable.ic_close
            }
            val iconColor = when {
                isSuccess -> Color.rgb(52, 168, 83)   // success_green
                isCancelled -> Color.rgb(251, 188, 4) // warning_amber
                else -> Color.rgb(217, 48, 37)        // error
            }
            val iconBgColor = when {
                isSuccess -> 0x2034A853
                isCancelled -> 0x20FBBC04
                else -> 0x20D93025
            }

            itemBinding.ivIcon.setImageResource(iconRes)
            itemBinding.ivIcon.setColorFilter(iconColor)
            itemBinding.vStatusBg.backgroundTintList =
                android.content.res.ColorStateList.valueOf(iconBgColor)

            // Title
            itemBinding.tvTitle.text = "Run #${entry.run.id}"

            // Conclusion chip
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

            // Time
            itemBinding.tvTime.text = entry.endedAt

            // Log preview
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

            // View button
            itemBinding.btnView.setOnClickListener {
                openInBrowser(entry.run.htmlUrl)
            }

            // Re-run button
            itemBinding.btnRerun.setOnClickListener {
                viewModel.reRunRun(token, entry)
            }

            // Delete button
            itemBinding.btnDelete.setOnClickListener {
                viewModel.deleteHistoryEntry(token, index)
            }

            // Entry animation (staggered scale)
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

    // ──── HELPERS ────

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
        android.widget.Toast.makeText(requireContext(), "Logs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showLogsDialog(entry: LogEntry) {
        val logText = entry.logs.ifBlank { "No logs available" }
        // Use a simple dialog with scrollable text
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            val tv = android.widget.TextView(context).apply {
                text = logText
                setTextColor(android.graphics.Color.parseColor("#00E676"))
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
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