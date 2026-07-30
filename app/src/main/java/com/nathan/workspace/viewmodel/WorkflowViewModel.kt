package com.nathan.workspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nathan.workspace.api.GitHubApi
import com.nathan.workspace.api.JobInfo
import com.nathan.workspace.api.WorkflowRunInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WorkflowViewModel(application: Application) : AndroidViewModel(application) {

    private val _runs = MutableStateFlow<List<WorkflowRunInfo>>(emptyList())
    val runs: StateFlow<List<WorkflowRunInfo>> = _runs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun startPolling(token: String) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                fetchRuns(token, isBackground = true)
                
                val currentRuns = _runs.value
                val hasActive = currentRuns.any { it.status == "in_progress" || it.status == "queued" }
                val delayTime = if (hasActive) 4000L else 10000L
                delay(delayTime)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshRuns(token: String) {
        scope.launch {
            fetchRuns(token, isBackground = false)
        }
    }

    private suspend fun fetchRuns(token: String, isBackground: Boolean) {
        if (!isBackground) {
            _isLoading.value = true
        }
        val result = GitHubApi.listRuns(token, 20)
        result.fold(
            onSuccess = { fetchedRuns ->
                _runs.value = fetchedRuns
                _error.value = null
            },
            onFailure = { e ->
                if (!isBackground) {
                    _error.value = "Failed to fetch runs: ${e.message}"
                }
            }
        )
        if (!isBackground) {
            _isLoading.value = false
        }
    }

    fun startRun(code: String, token: String) {
        _isLoading.value = true
        scope.launch {
            val result = GitHubApi.triggerWorkflow(token, code)
            result.fold(
                onSuccess = {
                    _error.value = null
                    delay(2000)
                    fetchRuns(token, isBackground = false)
                },
                onFailure = { e ->
                    _error.value = "Failed to trigger workflow: ${e.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun cancelRun(token: String, runId: Long) {
        scope.launch {
            val result = GitHubApi.cancelRun(token, runId)
            if (result.isSuccess) {
                delay(1000)
                fetchRuns(token, isBackground = false)
            } else {
                _error.value = "Failed to cancel run: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteRun(token: String, runId: Long) {
        scope.launch {
            val result = GitHubApi.deleteRun(token, runId)
            if (result.isSuccess) {
                _runs.value = _runs.value.filter { it.id != runId }
            } else {
                _error.value = "Failed to delete run: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteRunLogs(token: String, runId: Long) {
        scope.launch {
            val result = GitHubApi.deleteRunLogs(token, runId)
            if (result.isFailure) {
                _error.value = "Failed to delete logs: ${result.exceptionOrNull()?.message}"
            } else {
                _error.value = "Logs deleted successfully (refresh might be needed)"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    suspend fun fetchLogs(token: String, runId: Long): String {
        val jobsResult = GitHubApi.getRunJobs(token, runId)
        val jobs = jobsResult.getOrNull() ?: emptyList()
        return buildLogsString(jobs, token)
    }

    private suspend fun buildLogsString(jobs: List<JobInfo>, token: String): String {
        if (jobs.isEmpty()) return "No jobs found for this run."
        val sb = StringBuilder()
        for (job in jobs) {
            val jobIcon = when (job.status) {
                "completed" -> if (job.conclusion == "success") "\u2713" else "\u2717"
                "in_progress" -> "\u25B6"
                else -> "\u25CB"
            }
            val jobStatus = job.status.uppercase().replace("_", " ")
            sb.appendLine("$jobIcon JOB: ${job.name} [$jobStatus]")
            if (job.conclusion != null && job.conclusion != "success") {
                sb.appendLine("  Conclusion: ${job.conclusion.uppercase()}")
            }

            for (step in job.steps) {
                val stepIcon = when (step.status) {
                    "completed" -> if (step.conclusion == "success") "\u2713" else "\u2717"
                    "in_progress" -> "\u25B6"
                    "queued" -> "\u25CB"
                    else -> "\u25CB"
                }
                val stepConclusion = step.conclusion?.uppercase() ?: step.status.uppercase().replace("_", " ")
                sb.appendLine("  $stepIcon Step ${step.number}: ${step.name} [$stepConclusion]")
            }

            if (job.status == "completed" || job.status == "in_progress") {
                val logsResult = GitHubApi.getJobLogs(token, job.id)
                logsResult.onSuccess { logText ->
                    if (logText.isNotBlank()) {
                        val truncated = logText.take(2000)
                        sb.appendLine("  --- Logs ---")
                        truncated.lines().forEach { line ->
                            sb.appendLine("  $line")
                        }
                        if (logText.length > 2000) {
                            sb.appendLine("  ... (${logText.length - 2000} more bytes)")
                        }
                        sb.appendLine("  --- End ---")
                    }
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
