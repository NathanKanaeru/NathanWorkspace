package com.nathan.workspace.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nathan.workspace.ui.profile.ProfileScreen
import com.nathan.workspace.ui.repo.RepoScreen
import com.nathan.workspace.ui.webview.WebViewScreen
import com.nathan.workspace.ui.webview.WebViewHolder
import com.nathan.workspace.ui.webview.rememberWebViewHolder
import com.nathan.workspace.ui.workflow.WorkflowScreen
import com.nathan.workspace.viewmodel.WorkflowViewModel

enum class MainTab(val label: String, val icon: ImageVector) {
    WORKFLOW("Workflow", Icons.Filled.PlayCircle),
    REPO("Repo", Icons.Filled.Folder),
    WEB("Web", Icons.Filled.Language),
    PROFILE("Profile", Icons.Filled.Person)
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val token = remember {
        context.getSharedPreferences("app", Context.MODE_PRIVATE)
            .getString("github_token", "") ?: ""
    }

    val viewModel: WorkflowViewModel = viewModel()
    val webViewHolder = rememberWebViewHolder()

    LaunchedEffect(Unit) {
        if (token.isNotBlank()) viewModel.startPolling(token)
    }

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.WORKFLOW) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 4 })
                    .togetherWith(fadeOut() + slideOutHorizontally { -it / 4 })
            },
            label = "main_tab"
        ) { tab ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (tab) {
                    MainTab.WORKFLOW -> WorkflowScreen(viewModel = viewModel, token = token)
                    MainTab.REPO -> RepoScreen(token = token)
                    MainTab.WEB -> WebViewScreen(holder = webViewHolder)
                    MainTab.PROFILE -> ProfileScreen(viewModel = viewModel)
                }
            }
        }
    }
}