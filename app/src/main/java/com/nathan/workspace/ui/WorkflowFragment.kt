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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nathan.workspace.R
import com.nathan.workspace.api.WorkflowRunInfo
import com.nathan.workspace.databinding.FragmentWorkflowBinding
import com.nathan.workspace.databinding.ItemWorkflowRunBinding
import com.nathan.workspace.viewmodel.WorkflowViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshRuns(token)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.runs.collect { runs ->
                        renderRuns(runs)
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.swipeRefresh.isRefreshing = isLoading
                    }
                }
                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            showToast(it)
                            viewModel.clearError()
                        }
                    }
                }
            }
        }

        // Start polling when view is created
        viewModel.startPolling(token)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // We do not stop polling here, let ViewModel handle lifecycle if needed,
        // or we can stop it if we want it completely backgrounded.
        // Actually since it's an AndroidViewModel scoped to Activity, it will keep polling.
        _binding = null
    }

    private fun renderRuns(runs: List<WorkflowRunInfo>) {
        if (_binding == null) return

        binding.layoutHistory.removeAllViews()

        if (runs.isEmpty()) {
            binding.layoutHistory.isVisible = false
            binding.layoutHistoryHeader.isVisible = false
            binding.tvEmpty.text = "No workflow runs found."
            binding.tvEmpty.isVisible = true
            return
        }

        binding.layoutHistoryHeader.isVisible = true
        binding.layoutHistory.isVisible = true
        binding.tvEmpty.isVisible = false
        binding.tvHistoryCount.text = "(${runs.size})"

        runs.forEachIndexed { index, run ->
            val itemBinding = ItemWorkflowRunBinding.inflate(
                LayoutInflater.from(context), binding.layoutHistory, true
            )

            val isSuccess = run.conclusion == "success"
            val isCancelled = run.conclusion == "cancelled" || run.conclusion == "canceled"
            val isRunning = run.status == "in_progress" || run.status == "queued"

            val iconRes = when {
                isRunning -> R.drawable.ic_play
                isSuccess -> R.drawable.ic_check
                isCancelled -> R.drawable.ic_close
                else -> R.drawable.ic_close
            }
            val iconColor = when {
                isRunning -> Color.rgb(100, 181, 246)
                isSuccess -> Color.rgb(82, 199, 122)
                isCancelled -> Color.rgb(245, 197, 66)
                else -> Color.rgb(255, 138, 128)
            }
            val iconBgColor = when {
                isRunning -> 0x2064B5F6
                isSuccess -> 0x2052C77A
                isCancelled -> 0x20F5C542
                else -> 0x20FF8A80
            }

            itemBinding.ivIcon.setImageResource(iconRes)
            itemBinding.ivIcon.setColorFilter(iconColor)
            itemBinding.vStatusBg.backgroundTintList =
                android.content.res.ColorStateList.valueOf(iconBgColor)

            itemBinding.tvTitle.text = "Run #${run.id}"

            val conclusionText = when {
                isRunning -> run.status.uppercase()
                isSuccess -> "SUCCESS"
                isCancelled -> "CANCELLED"
                else -> run.conclusion?.uppercase() ?: "UNKNOWN"
            }
            itemBinding.tvStatus.text = conclusionText
            itemBinding.tvStatus.setBackgroundResource(
                when {
                    isRunning -> R.drawable.bg_chip_running
                    isSuccess -> R.drawable.bg_chip_success
                    isCancelled -> R.drawable.bg_chip_cancelled
                    else -> R.drawable.bg_chip_failure
                }
            )

            val displayTime = formatTime(run.createdAt)
            itemBinding.tvTime.text = "Triggered: $displayTime"

            // Button Logic
            itemBinding.btnCancel.isVisible = isRunning
            itemBinding.btnLogs.isVisible = true // Always visible to see realtime logs
            
            itemBinding.btnView.setOnClickListener {
                openInBrowser(run.htmlUrl)
            }

            itemBinding.btnCancel.setOnClickListener {
                viewModel.cancelRun(token, run.id)
            }

            itemBinding.btnDelete.setOnClickListener {
                showDeleteConfirmDialog(run)
            }

            itemBinding.btnLogs.setOnClickListener {
                showLogsDialog(run)
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

    private fun formatTime(isoDate: String): String {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val date = fmt.parse(isoDate) ?: return isoDate.take(10)
            val outFmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US)
            outFmt.format(date)
        } catch (_: Exception) {
            isoDate.take(10)
        }
    }

    private fun showDeleteConfirmDialog(run: WorkflowRunInfo) {
        val options = arrayOf(
            "Delete Logs Only",
            "Delete Entire Run"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage Run #${run.id}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.deleteRunLogs(token, run.id)
                        showToast("Deleting logs...")
                    }
                    1 -> {
                        viewModel.deleteRun(token, run.id)
                        showToast("Deleting run...")
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

    private fun showLogsDialog(run: WorkflowRunInfo) {
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage("Fetching logs...")
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val logText = viewModel.fetchLogs(token, run.id)
            progressDialog.dismiss()

            val scrollView = ScrollView(requireContext()).apply {
                val tv = TextView(context).apply {
                    text = logText
                    setTextColor(Color.parseColor("#e1e2e9"))
                    setBackgroundColor(Color.parseColor("#191c20"))
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setPadding(32, 32, 32, 32)
                    setLineSpacing(0f, 1.2f)
                }
                addView(tv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, 0, 0, 0)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Run #${run.id} - Logs")
                .setView(scrollView)
                .setPositiveButton("Copy") { _, _ -> copyToClipboard(logText) }
                .setNegativeButton("Close", null)
                .show()
        }
    }
}
