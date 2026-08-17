package com.nathan.workspace.ui.webview

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

class WebViewHolder {
    var webView: WebView? = null
    var savedBundle: Bundle = Bundle()

    fun captureState() {
        webView?.saveState(savedBundle)
    }
}

private val WebViewHolderSaver = Saver<WebViewHolder, Bundle>(
    save = { holder ->
        holder.captureState()
        holder.savedBundle
    },
    restore = { bundle ->
        WebViewHolder().apply { savedBundle = bundle }
    }
)

@Composable
fun rememberWebViewHolder(): WebViewHolder {
    return rememberSaveable(saver = WebViewHolderSaver) { WebViewHolder() }
}