package com.nathan.workspace.ui.workflow

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nathan.workspace.R
import com.nathan.workspace.api.WorkflowRunInfo
import com.nathan.workspace.ui.theme.ChipCancelled
import com.nathan.workspace.ui.theme.ChipFailure
import com.nathan.workspace.ui.theme.ChipRunning
import com.nathan.workspace.ui.theme.ChipSuccess
import com.nathan.workspace.ui.theme.FullPillShape
import com.nathan.workspace.viewmodel.WorkflowViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun WorkflowScreen(viewModel: WorkflowViewModel, token: String) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("app", Context.MODE_PRIVATE)
    }
    val login = remember { prefs.getString("github_login", "") ?: "" }
    val name = remember { prefs.getString("github_name", "") ?: "" }

    val runs by viewModel.runs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var code by rememberSaveable { mutableStateOf("") }
    var manageRun by remember { mutableStateOf<WorkflowRunInfo?>(null) }
    var logsRun by remember { mutableStateOf<WorkflowRunInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.error.collect { error ->
            if (error != null) {
                snackbarHostState.showSnackbar(error)
                viewModel.clearError()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    UserHeader(name = name, login = login)
                }

                item {
                    TriggerCard(
                        code = code,
                        onCodeChange = { code = it },
                        isLoading = isLoading,
                        onStart = {
                            val value = code.trim()
                            if (value.isBlank()) return@TriggerCard
                            code = ""
                            viewModel.startRun(value, token)
                        }
                    )
                }

                item {
                    HistoryHeader(
                        count = runs.size,
                        onRefresh = { viewModel.refreshRuns(token) }
                    )
                }

                if (runs.isEmpty()) {
                    item {
                        Text(
                            text = "No workflow runs found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(runs, key = { it.id }) { run ->
                        RunCard(
                            run = run,
                            onCancel = { viewModel.cancelRun(token, run.id) },
                            onDeleteLogs = { viewModel.deleteRunLogs(token, run.id) },
                            onDeleteRun = { viewModel.deleteRun(token, run.id) },
                            onView = { openInBrowser(context, run.htmlUrl) },
                            onLogs = { logsRun = run },
                            onManage = { manageRun = run }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    manageRun?.let { run ->
        ManageRunDialog(
            run = run,
            onDismiss = { manageRun = null },
            onDeleteLogs = {
                viewModel.deleteRunLogs(token, run.id)
                scope.launch { snackbarHostState.showSnackbar("Deleting logs...") }
                manageRun = null
            },
            onDeleteRun = {
                viewModel.deleteRun(token, run.id)
                scope.launch { snackbarHostState.showSnackbar("Deleting run...") }
                manageRun = null
            }
        )
    }

    logsRun?.let { run ->
        LogsDialog(
            run = run,
            onDismiss = { logsRun = null },
            fetchLogs = { viewModel.fetchLogs(token, run.id) }
        )
    }
}

@Composable
private fun UserHeader(name: String, login: String) {
    val displayName = name.ifBlank { login }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_github_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "@$login",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TriggerCard(
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Mulai Workflow",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text("CRD Code") },
                placeholder = { Text("Tempel kode akses komputer...") },
                enabled = !isLoading,
                shape = MaterialTheme.shapes.extraSmall,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = FullPillShape,
                enabled = !isLoading
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mulai", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HistoryHeader(count: Int, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Riwayat",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RunCard(
    run: WorkflowRunInfo,
    onCancel: () -> Unit,
    onDeleteLogs: () -> Unit,
    onDeleteRun: () -> Unit,
    onView: () -> Unit,
    onLogs: () -> Unit,
    onManage: () -> Unit
) {
    val isRunning = run.status == "in_progress" || run.status == "queued"
    val isSuccess = run.conclusion == "success"
    val isCancelled = run.conclusion == "cancelled" || run.conclusion == "canceled"

    val (icon, iconColor) = when {
        isRunning -> Icons.Filled.PlayCircle to ChipRunning
        isSuccess -> Icons.Filled.Check to ChipSuccess
        isCancelled -> Icons.Filled.Close to ChipCancelled
        else -> Icons.Filled.Close to ChipFailure
    }
    val statusText = when {
        isRunning -> run.status.uppercase()
        isSuccess -> "SUCCESS"
        isCancelled -> "CANCELLED"
        else -> run.conclusion?.uppercase() ?: "UNKNOWN"
    }
    val chipColor = when {
        isRunning -> ChipRunning
        isSuccess -> ChipSuccess
        isCancelled -> ChipCancelled
        else -> ChipFailure
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconColor.copy(alpha = 0.13f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Run #${run.id}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Triggered: ${formatTime(run.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(text = statusText, color = chipColor)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = FullPillShape,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                }
                TextButton(
                    onClick = onLogs,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Logs", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = onManage,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = onView) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = "Buka di browser",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF111318)
        )
    }
}

@Composable
private fun ManageRunDialog(
    run: WorkflowRunInfo,
    onDismiss: () -> Unit,
    onDeleteLogs: () -> Unit,
    onDeleteRun: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Run #${run.id}") },
        text = {
            Column {
                TextButton(
                    onClick = onDeleteLogs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Logs Only", modifier = Modifier.weight(1f))
                }
                TextButton(
                    onClick = onDeleteRun,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Entire Run", modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LogsDialog(
    run: WorkflowRunInfo,
    onDismiss: () -> Unit,
    fetchLogs: suspend () -> String
) {
    var logText by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(run.id) {
        logText = fetchLogs()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Run #${run.id} - Logs",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                val text = logText
                if (text == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
                            .padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        logText?.let { clipboard.setText(AnnotatedString(it)) }
                    }) {
                        Text("Copy")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
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

private fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}