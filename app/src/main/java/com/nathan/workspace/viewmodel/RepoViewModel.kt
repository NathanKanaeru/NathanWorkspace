package com.nathan.workspace.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import com.nathan.workspace.api.AssetInfo
import com.nathan.workspace.api.GitHubApi
import com.nathan.workspace.api.ReleaseInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class RepoViewModel(application: Application) : AndroidViewModel(application) {

    private val _releases = MutableStateFlow<List<ReleaseInfo>>(emptyList())
    val releases: StateFlow<List<ReleaseInfo>> = _releases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showAllReleases = MutableStateFlow(false)
    val showAllReleases: StateFlow<Boolean> = _showAllReleases.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Set<Long>>(emptySet())
    val activeDownloads: StateFlow<Set<Long>> = _activeDownloads.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val progressMap: StateFlow<Map<Long, Int>> = _progressMap.asStateFlow()

    private val _speedMap = MutableStateFlow<Map<Long, String>>(emptyMap())
    val speedMap: StateFlow<Map<Long, String>> = _speedMap.asStateFlow()

    private val dmIdToAsset = mutableMapOf<Long, Long>()
    private val lastBytesMap = mutableMapOf<Long, Long>()
    private lateinit var downloadManager: DownloadManager
    private lateinit var prefs: android.content.SharedPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollingJob: Job? = null

    init {
        val context = getApplication<Application>()
        downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        prefs = context.getSharedPreferences("downloads_repo", Context.MODE_PRIVATE)
        restoreActiveDownloads()
        startPolling()
    }

    private fun restoreActiveDownloads() {
        dmIdToAsset.clear()
        prefs.all.forEach { (key, value) ->
            try {
                val dmId = key.toLong()
                val assetId = (value as? Long) ?: (value as? String)?.toLong() ?: return@forEach
                dmIdToAsset[dmId] = assetId
            } catch (_: Exception) {}
        }
        _activeDownloads.value = dmIdToAsset.values.toSet()
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                if (dmIdToAsset.isNotEmpty()) {
                    pollDownloadManager()
                }
                delay(1000)
            }
        }
    }

    private fun pollDownloadManager() {
        val cursor: Cursor = try {
            downloadManager.query(DownloadManager.Query())
        } catch (e: Exception) { return }

        val currentValidIds = mutableSetOf<Long>()

        if (cursor.moveToFirst()) {
            do {
                val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                if (idIndex < 0 || statusIndex < 0 || bytesDownloadedIndex < 0 || bytesTotalIndex < 0) continue

                val dmId = cursor.getLong(idIndex)
                val assetId = dmIdToAsset[dmId] ?: continue
                currentValidIds.add(dmId)

                val status = cursor.getInt(statusIndex)
                val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                val bytesTotal = cursor.getLong(bytesTotalIndex)

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                        removeActiveDownload(dmId)
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0
                        _progressMap.value = _progressMap.value + (assetId to progress)

                        val lastBytes = lastBytesMap[dmId] ?: 0L
                        val speedBytes = bytesDownloaded - lastBytes
                        lastBytesMap[dmId] = bytesDownloaded

                        val speedText = "${formatSize(speedBytes)}/s"
                        val sizeText = "${formatSize(bytesDownloaded)} / ${formatSize(bytesTotal)}"
                        _speedMap.value = _speedMap.value + (assetId to "$sizeText ($speedText)")
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        val missingIds = dmIdToAsset.keys - currentValidIds
        missingIds.forEach { removeActiveDownload(it) }
    }

    private fun saveActiveDownload(dmId: Long, assetId: Long) {
        dmIdToAsset[dmId] = assetId
        prefs.edit().putLong(dmId.toString(), assetId).apply()
        _activeDownloads.value = dmIdToAsset.values.toSet()
    }

    private fun removeActiveDownload(dmId: Long) {
        val assetId = dmIdToAsset.remove(dmId) ?: return
        prefs.edit().remove(dmId.toString()).apply()
        lastBytesMap.remove(dmId)
        _activeDownloads.value = dmIdToAsset.values.toSet()
        _progressMap.value = _progressMap.value - assetId
        _speedMap.value = _speedMap.value - assetId
    }

    fun refresh(token: String) {
        if (token.isBlank()) {
            _error.value = "Token tidak tersedia. Silakan login ulang."
            return
        }
        _isLoading.value = true
        scope.launch {
            val result = GitHubApi.getReleases(token)
            _isLoading.value = false
            result.fold(
                onSuccess = { list ->
                    _releases.value = list
                    _error.value = null
                },
                onFailure = { e ->
                    if (_releases.value.isEmpty()) {
                        _error.value = e.message ?: "Gagal memuat rilis"
                    } else {
                        _error.value = "Gagal refresh: ${e.message}"
                    }
                }
            )
        }
    }

    fun startDownload(token: String, asset: AssetInfo) {
        try {
            val context = getApplication<Application>()
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null && !dir.exists()) dir.mkdirs()

            val existingFile = File(dir, asset.name)
            if (existingFile.exists()) existingFile.delete()

            val request = DownloadManager.Request(Uri.parse(asset.browserDownloadUrl))
                .setTitle(asset.name)
                .setDescription("Downloading asset from GitHub")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, asset.name)
                .addRequestHeader("Authorization", "Bearer $token")

            val downloadId = downloadManager.enqueue(request)
            saveActiveDownload(downloadId, asset.id)
            _progressMap.value = _progressMap.value + (asset.id to 0)
            _speedMap.value = _speedMap.value + (asset.id to "Starting...")
        } catch (e: Exception) {
            _error.value = "Failed to start download: ${e.message}"
        }
    }

    fun setShowAll(show: Boolean) {
        _showAllReleases.value = show
    }

    fun clearError() {
        _error.value = null
    }

    fun isDownloaded(asset: AssetInfo): Boolean {
        val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return dir != null && File(dir, asset.name).exists()
    }

    fun downloadedFile(asset: AssetInfo): File? {
        val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return dir?.let { File(it, asset.name) }?.takeIf { it.exists() }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb)
        else String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        pollingJob = null
    }
}