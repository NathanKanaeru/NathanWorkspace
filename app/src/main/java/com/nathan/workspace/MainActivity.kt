package com.nathan.workspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nathan.workspace.ui.MainScreen
import com.nathan.workspace.ui.theme.NathanWorkspaceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NathanWorkspaceTheme {
                MainScreen()
            }
        }
    }
}