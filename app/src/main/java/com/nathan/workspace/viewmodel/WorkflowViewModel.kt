package com.nathan.workspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

sealed interface WorkflowUiState {
    data object Idle : WorkflowUiState
    data class Starting(val code: String) : WorkflowUiState
    data class Running(
        val runId: Long,
        val status: String,
        val logs: String,
        val htmlUrl: String,
        val jobs: List<JobInfo> = emptyList()
    ) : WorkflowUiState
    data class Completed(val entry: LogEntry) : WorkflowUiState
    data class Error(val message: String) : WorkflowUiState
}

data class ActiveRun(
    val run: WorkflowRunInfo,
    val code: String,
    val logs: String = "",
    val jobs: List<JobInfo> = emptyList(),
    val isRunning: Boolean = true,
    val elapsedSeconds: Long = 0L
)

data class LogEntry(
    val run: WorkflowRunInfo,
    val logs: String,
    val endedAt: String,
    val code: String = ""
)

class WorkflowViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("workflow", Application.MODE_PRIVATE)
    private val gson = Gson()

    private val _activeRun = MutableStateFlow<ActiveRun?>(null)
    val activeRun: StateFlow<ActiveRun?> = _activeRun.asStateFlow()

    private val _history = MutableStateFlow<List<LogEntry>>(emptyList())
    val history: StateFlow<List<LogEntry>> = _history.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _uiState = MutableStateFlow<WorkflowUiState>(WorkflowUiState.Idle)
    val uiState: StateFlow<WorkflowUiState> = _uiState.asStateFlow()

    private val _elapsedTime = MutableStateFlow("00:00")
    val elapsedTime: StateFlow<String> = _elapsedTime.asStateFlow()

    private var pollingJob: Job? = null
    private var elapsedJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        loadHistory()
        restoreActiveRun()
    }

    private fun restoreActiveRun() {
        val json = prefs.getString("active_run", null) ?: return
        try {
            val active = gson.fromJson(json, ActiveRun::class.java)
            _activeRun.value = active
            if (active.isRunning) {
                startPolling(active.run.id)
                startElapsedTimer(active.run.createdAt)
                _uiState.value = WorkflowUiState.Running(
                    runId = active.run.id,
                    status = active.run.status,
                    logs = active.logs,
                    htmlUrl = active.run.htmlUrl,
                    jobs = active.jobs
                )
            } else {
                val entry = LogEntry(
                    run = active.run,
                    logs = active.logs,
                    endedAt = active.run.updatedAt,
                    code = active.code
                )
                _uiState.value = WorkflowUiState.Completed(entry)
            }
        } catch (_: Exception) {
            prefs.edit().remove("active_run").apply()
        }
    }

    private fun saveActiveRun() {
        _activeRun.value?.let {
            prefs.edit().putString("active_run", gson.toJson(it)).apply()
        }
    }

    private fun clearActiveRun() {
        _activeRun.value = null
        _elapsedTime.value = "00:00"
        prefs.edit().remove("active_run").apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("history", null) ?: return
        try {
            val type = object : TypeToken<List<LogEntry>>() {}.type
            _history.value = gson.fromJson(json, type)
        } catch (_: Exception) {
            prefs.edit().remove("history").apply()
        }
    }

    private fun saveHistory() {
        prefs.edit().putString("history", gson.toJson(_history.value)).apply()
    }

    fun dismissCompleted() {
        _uiState.value = WorkflowUiState.Idle
        clearActiveRun()
    }

    fun startRun(code: String, token: String) {
        // If there's a completed run, clear it first
        if (_uiState.value is WorkflowUiState.Completed) {
            clearActiveRun()
        }
        _uiState.value = WorkflowUiState.Starting(code)
        scope.launch {
            val result = GitHubApi.triggerWorkflow(token, code)
            result.fold(
                onSuccess = {
                    delay(3000)
                    val runResult = GitHubApi.getLatestRun(token)
                    runResult.fold(
                        onSuccess = { info ->
                            val active = ActiveRun(
                                run = info,
                                code = code,
                                isRunning = true
                            )
                            _activeRun.value = active
                            saveActiveRun()
                            _uiState.value = WorkflowUiState.Running(
                                runId = info.id,
                                status = info.status,
                                logs = "",
                                htmlUrl = info.htmlUrl,
                                jobs = emptyList()
                            )
                            startPolling(info.id)
                            startElapsedTimer(info.createdAt)
                        },
                        onFailure = { error ->
                            _uiState.value = WorkflowUiState.Error("Gagal mendapatkan run info: ${error.message}")
                        }
                    )
                },
                onFailure = { error ->
                    _uiState.value = WorkflowUiState.Error("Gagal trigger workflow: ${error.message}")
                }
            )
        }
    }

    private fun startPolling(runId: Long) {
        pollingJob?.cancel()
        _isPolling.value = true
        pollingJob = scope.launch {
            val token = getToken()
            var lastKnownLogs = ""
            var lastKnownJobs = emptyList<JobInfo>()

            while (isActive) {
                val runResult = GitHubApi.getRun(token, runId)
                runResult.fold(
                    onSuccess = { info ->
                        _activeRun.value = _activeRun.value?.copy(run = info)

                        val jobsResult = GitHubApi.getRunJobs(token, runId)
                        val currentJobs = jobsResult.getOrNull() ?: lastKnownJobs
                        val jobsChanged = currentJobs != lastKnownJobs
                        lastKnownJobs = currentJobs

                        val logs = buildLogsString(currentJobs, token)
                        if (logs != lastKnownLogs || jobsChanged) {
                            lastKnownLogs = logs
                            _activeRun.value = _activeRun.value?.copy(
                                logs = logs,
                                jobs = currentJobs
                            )
                            saveActiveRun()
                        }

                        if (info.status == "completed") {
                            val entry = LogEntry(
                                run = info,
                                logs = lastKnownLogs,
                                endedAt = info.updatedAt,
                                code = _activeRun.value?.code ?: ""
                            )
                            _history.value = listOf(entry) + _history.value
                            saveHistory()
                            pollingJob?.cancel()
                            elapsedJob?.cancel()
                            _isPolling.value = false
                            _activeRun.value = _activeRun.value?.copy(isRunning = false)
                            saveActiveRun()
                            _uiState.value = WorkflowUiState.Completed(entry)
                            return@launch
                        } else {
                            _uiState.value = WorkflowUiState.Running(
                                runId = info.id,
                                status = info.status,
                                logs = lastKnownLogs,
                                htmlUrl = info.htmlUrl,
                                jobs = currentJobs
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = WorkflowUiState.Error("Gagal polling: ${error.message}")
                    }
                )
                delay(4000)
            }
        }
    }

    private suspend fun buildLogsString(jobs: List<JobInfo>, token: String): String {
        if (jobs.isEmpty()) return ""
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

    fun refreshActiveRun() {
        val runId = _activeRun.value?.run?.id ?: return
        pollingJob?.cancel()
        _activeRun.value = _activeRun.value?.copy(isRunning = true)
        startPolling(runId)
    }

    private fun startElapsedTimer(createdAt: String) {
        elapsedJob?.cancel()
        elapsedJob = scope.launch {
            val createdMillis = parseIso8601(createdAt)
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - createdMillis) / 1000
                _activeRun.value = _activeRun.value?.copy(elapsedSeconds = elapsed)
                _elapsedTime.value = formatElapsed(elapsed)
                delay(1000)
            }
        }
    }

    private fun parseIso8601(dateStr: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatElapsed(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    fun cancelRun(token: String) {
        val runId = _activeRun.value?.run?.id ?: return
        scope.launch {
            GitHubApi.cancelRun(token, runId)
            pollingJob?.cancel()
            elapsedJob?.cancel()
            _isPolling.value = false
            _activeRun.value = _activeRun.value?.copy(isRunning = false)
            val updated = GitHubApi.getRun(token, runId)
            updated.fold(
                onSuccess = { info ->
                    val jobsResult = GitHubApi.getRunJobs(token, runId)
                    val logs = jobsResult.map { jobs ->
                        buildLogsString(jobs, token)
                    }.getOrNull() ?: _activeRun.value?.logs ?: ""
                    val entry = LogEntry(
                        run = info,
                        logs = logs,
                        endedAt = info.updatedAt,
                        code = _activeRun.value?.code ?: ""
                    )
                    _history.value = listOf(entry) + _history.value
                    saveHistory()
                    _activeRun.value = _activeRun.value?.copy(isRunning = false)
                    saveActiveRun()
                    _uiState.value = WorkflowUiState.Completed(entry)
                },
                onFailure = {
                    _uiState.value = WorkflowUiState.Error("Gagal cancel workflow")
                }
            )
        }
    }

    fun deleteHistoryEntry(token: String, index: Int) {
        val entry = _history.value.getOrNull(index) ?: return
        scope.launch {
            GitHubApi.deleteRunLogs(token, entry.run.id)
            val list = _history.value.toMutableList()
            if (index < list.size) {
                list.removeAt(index)
                _history.value = list
                saveHistory()
            }
        }
    }

    fun deleteHistoryEntryAndRun(token: String, index: Int) {
        val entry = _history.value.getOrNull(index) ?: return
        scope.launch {
            GitHubApi.deleteRunLogs(token, entry.run.id)
            GitHubApi.deleteRun(token, entry.run.id)
            val list = _history.value.toMutableList()
            if (index < list.size) {
                list.removeAt(index)
                _history.value = list
                saveHistory()
            }
        }
    }

    fun reRunRun(token: String, entry: LogEntry) {
        val code = if (entry.code.isNotBlank()) entry.code else entry.run.id.toString()
        startRun(code, token)
    }

    fun deleteRunLogs(runId: Long) {
        scope.launch {
            val t = getToken()
            GitHubApi.deleteRunLogs(t, runId)
        }
    }

    fun refreshHistory(token: String) {
        scope.launch {
            GitHubApi.listRuns(token, 5).fold(
                onSuccess = { runs ->
                    val currentIds = _history.value.map { it.run.id }.toSet()
                    val newEntries = runs
                        .filter { it.id !in currentIds }
                        .map { run ->
                            LogEntry(
                                run = run,
                                logs = "",
                                endedAt = run.updatedAt,
                                code = ""
                            )
                        }
                    if (newEntries.isNotEmpty()) {
                        _history.value = newEntries + _history.value
                        saveHistory()
                    }
                },
                onFailure = { }
            )
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history").apply()
    }

    private fun getToken(): String {
        return getApplication<Application>()
            .getSharedPreferences("app", Application.MODE_PRIVATE)
            .getString("github_token", "") ?: ""
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        elapsedJob?.cancel()
    }
}
