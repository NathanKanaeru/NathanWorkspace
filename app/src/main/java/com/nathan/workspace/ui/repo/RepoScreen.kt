package com.nathan.workspace.ui.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nathan.workspace.api.AssetInfo
import com.nathan.workspace.api.ReleaseInfo
import com.nathan.workspace.ui.theme.FullPillShape
import com.nathan.workspace.viewmodel.RepoViewModel
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun RepoScreen(token: String) {
    val context = LocalContext.current
    val viewModel: RepoViewModel = viewModel()

    val releases by viewModel.releases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showAll by viewModel.showAllReleases.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val speedMap by viewModel.speedMap.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refresh(token)
    }

    LaunchedEffect(Unit) {
        viewModel.error.collect { e ->
            if (e != null) {
                snackbarHostState.showSnackbar(e)
                viewModel.clearError()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isLoading && releases.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                isLoading && releases.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null && releases.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh(token) },
                            shape = FullPillShape
                        ) {
                            Text("Coba Lagi")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            RepoHeader(onOpenRepo = {
                                openUrl(context, "https://github.com/NathanKanaeru/samptest")
                            })
                        }

                        if (releases.isEmpty()) {
                            item {
                                Text(
                                    text = "Belum ada rilis.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        } else {
                            val visible = if (showAll) releases else releases.take(3)
                            items(visible, key = { it.id }) { release ->
                                ReleaseCard(
                                    release = release,
                                    activeDownloads = activeDownloads,
                                    progressMap = progressMap,
                                    speedMap = speedMap,
                                    isDownloaded = { viewModel.isDownloaded(it) },
                                    onDownload = { viewModel.startDownload(token, it) },
                                    onInstall = { installApk(context, viewModel.downloadedFile(it)) },
                                    onOpenGitHub = { openUrl(context, release.htmlUrl) }
                                )
                            }
                            if (!showAll && releases.size > 3) {
                                item {
                                    OutlinedButton(
                                        onClick = { viewModel.setShowAll(true) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = FullPillShape
                                    ) {
                                        Text("View All Releases", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RepoHeader(onOpenRepo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "GitHub Releases",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "NathanKanaeru/samptest",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = onOpenRepo,
            shape = FullPillShape
        ) {
            Text("Repo", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseInfo,
    activeDownloads: Set<Long>,
    progressMap: Map<Long, Int>,
    speedMap: Map<Long, String>,
    isDownloaded: (AssetInfo) -> Boolean,
    onDownload: (AssetInfo) -> Unit,
    onInstall: (AssetInfo) -> Unit,
    onOpenGitHub: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDate(release.publishedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = release.name.ifBlank { release.tagName },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = release.body.ifBlank { "Tidak ada catatan rilis" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (release.assets.isNotEmpty()) {
                Text(
                    text = "Assets",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                release.assets.forEach { asset ->
                    AssetRow(
                        asset = asset,
                        isActive = asset.id in activeDownloads,
                        progress = progressMap[asset.id] ?: 0,
                        speed = speedMap[asset.id],
                        isDownloaded = isDownloaded(asset),
                        onDownload = { onDownload(asset) },
                        onInstall = { onInstall(asset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onOpenGitHub) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Buka di GitHub", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AssetRow(
    asset: AssetInfo,
    isActive: Boolean,
    progress: Int,
    speed: String?,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatSize(asset.size)} · ${NumberFormat.getNumberInstance(Locale.US).format(asset.downloadCount)} downloads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            when {
                isActive -> {
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isDownloaded -> {
                    Button(onClick = onInstall, shape = FullPillShape) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Install", style = MaterialTheme.typography.labelLarge)
                    }
                }
                else -> {
                    Button(onClick = onDownload, shape = FullPillShape) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            speed?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoDate) ?: return isoDate
        val outFmt = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        outFmt.format(date)
    } catch (_: Exception) { isoDate }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}

private fun installApk(context: Context, file: File?) {
    if (file == null || !file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Buka pengaturan untuk izinkan install dari sumber tidak dikenal",
            Toast.LENGTH_LONG
        ).show()
    }
}