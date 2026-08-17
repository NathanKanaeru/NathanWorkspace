package com.nathan.workspace.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nathan.workspace.BuildConfig
import com.nathan.workspace.LoginActivity
import com.nathan.workspace.R
import com.nathan.workspace.viewmodel.WorkflowViewModel
import java.io.File

@Composable
fun ProfileScreen(viewModel: WorkflowViewModel) {
    val context = LocalContext.current
    val runs by viewModel.runs.collectAsState()

    val prefs = remember {
        context.getSharedPreferences("app", Context.MODE_PRIVATE)
    }
    val login = remember { prefs.getString("github_login", "") ?: "" }
    val name = remember { prefs.getString("github_name", "") ?: "" }

    var showLicenses by remember { mutableStateOf(false) }
    var showClearCache by remember { mutableStateOf(false) }
    var showClearAll by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }

    val total = runs.size
    val successCount = runs.count { it.conclusion == "success" }
    val failCount = runs.count {
        val c = it.conclusion
        c != null && c != "success" && c != "cancelled" && c != "canceled"
    }
    val successRate = if (total > 0) (successCount * 100) / total else 0
    val lastRun = runs.firstOrNull()?.createdAt?.take(10) ?: "-"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // ===== User card =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github_mark),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = name.ifBlank { login },
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

        Spacer(modifier = Modifier.height(24.dp))

        // ===== Stats card =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Statistik Workflow",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Total Runs", total.toString(), Modifier.weight(1f))
                    StatItem("Success Rate", "$successRate%", Modifier.weight(1f))
                    StatItem("Failed", failCount.toString(), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Run Terakhir",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = lastRun,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ===== Menu =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column {
                MenuItem(
                    icon = Icons.Filled.Person,
                    label = "Profil GitHub",
                    onClick = { openUrl(context, "https://github.com/$login") }
                )
                MenuItem(
                    icon = Icons.Filled.Code,
                    label = "Repositori Aplikasi",
                    onClick = { openUrl(context, "https://github.com/NathanKanaeru/NathanWorkspace") }
                )
                MenuItem(
                    icon = Icons.Filled.Info,
                    label = "Lisensi",
                    onClick = { showLicenses = true }
                )
                MenuItem(
                    icon = Icons.Filled.Storage,
                    label = "Bersihkan Cache Download",
                    onClick = { showClearCache = true }
                )
                MenuItem(
                    icon = Icons.Filled.Delete,
                    label = "Bersihkan Semua Data",
                    onClick = { showClearAll = true }
                )
                MenuItem(
                    icon = Icons.Filled.Logout,
                    label = "Keluar",
                    destructive = true,
                    onClick = { showLogout = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Nathan Workspace v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text("Bersihkan Cache Download") },
            text = { Text("Semua file APK yang diunduh akan dihapus dari perangkat. Lanjutkan?") },
            confirmButton = {
                TextButton(onClick = {
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { it.delete() }
                    }
                    Toast.makeText(context, "Download cache cleared", Toast.LENGTH_SHORT).show()
                    showClearCache = false
                }) { Text("Bersihkan") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCache = false }) { Text("Batal") }
            }
        )
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("Bersihkan Data Lokal") },
            text = { Text("Data lokal aplikasi akan dihapus. Data GitHub tidak terpengaruh. Anda harus masuk kembali.") },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("workflow", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    context.getSharedPreferences("app", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    navigateToLogin(context)
                }) { Text("Bersihkan Data") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text("Batal") }
            }
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Keluar") },
            text = { Text("Anda yakin ingin keluar?") },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("app", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    navigateToLogin(context)
                }) { Text("Keluar") }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lisensi Open Source") },
        text = {
            Text(
                text = """
                    Nathan Workspace
                    Copyright 2026 NathanKanaeru

                    Licensed under the Apache License, Version 2.0 (the "License");
                    you may not use this file except in compliance with the License.
                    You may obtain a copy of the License at

                      http://www.apache.org/licenses/LICENSE-2.0

                    Unless required by applicable law or agreed to in writing, software
                    distributed under the License is distributed on an "AS IS" BASIS,
                    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

                    ---- Third-Party Libraries ----

                    • OkHttp 4.12 (Apache 2.0)
                    • Gson (Apache 2.0)
                    • Jetpack Compose (Apache 2.0)
                    • Android Jetpack (Apache 2.0)
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}

private fun navigateToLogin(context: Context) {
    val intent = Intent(context, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
    (context as? android.app.Activity)?.finishAffinity()
}