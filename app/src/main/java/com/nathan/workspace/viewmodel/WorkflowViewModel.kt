package com.nathan.workspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nathan.workspace.api.GitHubApi
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

data class ActiveRun(
    val run: WorkflowRunInfo,
    val code: String,
    val logs: String = "",
    val isRunning: Boolean = true
)

data class LogEntry(
    val run: WorkflowRunInfo,
    val logs: String,
    val endedAt: String
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

    private var pollingJob: Job? = null
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
            if (active.isRunning) startPolling(active.run.id)
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

    fun startRun(code: String, token: String) {
        scope.launch {
            val result = GitHubApi.triggerWorkflow(token, code)
            result.fold(
                onSuccess = {
                    delay(3000)
                    val runResult = GitHubApi.getLatestRun(token)
                    runResult.fold(
                        onSuccess = { info ->
                            val active = ActiveRun(run = info, code = code, isRunning = true)
                            _activeRun.value = active
                            saveActiveRun()
                            startPolling(info.id)
                        },
                        onFailure = { }
                    )
                },
                onFailure = { }
            )
        }
    }

    private fun startPolling(runId: Long) {
        pollingJob?.cancel()
        _isPolling.value = true
        pollingJob = scope.launch {
            val token = getToken()
            var lastKnownLogs = ""

            while (isActive) {
                val runResult = GitHubApi.getRun(token, runId)
                runResult.fold(
                    onSuccess = { info ->
                        _activeRun.value = _activeRun.value?.copy(run = info)

                        val logsResult = GitHubApi.getRunLogs(token, runId)
                        logsResult.fold(
                            onSuccess = { logs ->
                                if (logs != lastKnownLogs) {
                                    lastKnownLogs = logs
                                    _activeRun.value = _activeRun.value?.copy(logs = logs)
                                    saveActiveRun()
                                }
                            },
                            onFailure = { }
                        )

                        if (info.status == "completed") {
                            val entry = LogEntry(
                                run = info,
                                logs = lastKnownLogs,
                                endedAt = info.updatedAt
                            )
                            _history.value = listOf(entry) + _history.value
                            saveHistory()
                            pollingJob?.cancel()
                            _isPolling.value = false
                            clearActiveRun()
                            return@launch
                        }
                    },
                    onFailure = { }
                )
                delay(5000)
            }
        }
    }

    fun cancelRun(token: String) {
        val runId = _activeRun.value?.run?.id ?: return
        scope.launch {
            GitHubApi.cancelRun(token, runId)
            pollingJob?.cancel()
            _isPolling.value = false
            _activeRun.value = _activeRun.value?.copy(isRunning = false)
            val updated = GitHubApi.getRun(token, runId)
            updated.fold(
                onSuccess = { info ->
                    val logsResult = GitHubApi.getRunLogs(token, runId)
                    val logs = logsResult.getOrNull() ?: ""
                    val entry = LogEntry(run = info, logs = logs, endedAt = info.updatedAt)
                    _history.value = listOf(entry) + _history.value
                    saveHistory()
                },
                onFailure = { }
            )
            clearActiveRun()
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

    private fun getToken(): String {
        return getApplication<Application>()
            .getSharedPreferences("app", Application.MODE_PRIVATE)
            .getString("github_token", "") ?: ""
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
