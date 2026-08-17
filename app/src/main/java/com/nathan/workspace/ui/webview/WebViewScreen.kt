package com.nathan.workspace.ui.webview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

@Composable
fun WebViewScreen(holder: WebViewHolder) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var pageLoading by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var blockedHint by remember { mutableStateOf<String?>(null) }
    var ctrlToggled by rememberSaveable { mutableStateOf(false) }
    var altToggled by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun updateNavButtons() {
        val wv = holder.webView ?: return
        canGoBack = wv.canGoBack()
        canGoForward = wv.canGoForward()
    }

    fun openInExternalBrowser(url: String?) {
        val target = url ?: holder.webView?.url ?: CRD_HOME
        if (target.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {}
    }

    fun handleBlockedUrl(url: String?) {
        blockedHint =
            "Halaman diblokir (hanya remotedesktop.google.com).\nGunakan tombol \"Buka di Browser\"."
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Navigasi diblokir",
                actionLabel = "Buka di Browser",
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                openInExternalBrowser(url)
            }
        }
    }

    fun focusRemoteFrame() {
        holder.webView?.evaluateJavascript(FOCUS_SCRIPT, null)
    }

    fun dispatchKey(keyCode: Int, scanCode: Int) {
        val wv = holder.webView ?: return
        focusRemoteFrame()
        wv.requestFocus()
        var meta = 0
        if (ctrlToggled) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (altToggled) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        val now = SystemClock.uptimeMillis()
        val down = newKeyEvent(now, KeyEvent.ACTION_DOWN, keyCode, scanCode, meta)
        val up = newKeyEvent(now, KeyEvent.ACTION_UP, keyCode, scanCode, meta)
        wv.postDelayed({
            wv.dispatchKeyEvent(down)
            wv.dispatchKeyEvent(up)
        }, 60L)
    }

    fun toggleModifier(isCtrl: Boolean) {
        val turningOn = !(if (isCtrl) ctrlToggled else altToggled)
        if (isCtrl) ctrlToggled = turningOn else altToggled = turningOn

        val wv = holder.webView ?: return
        focusRemoteFrame()
        wv.requestFocus()
        val modKey = if (isCtrl) KeyEvent.KEYCODE_CTRL_LEFT else KeyEvent.KEYCODE_ALT_LEFT
        val scan = if (isCtrl) SCAN_CTRL_LEFT else SCAN_ALT_LEFT
        val action = if (turningOn) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val now = SystemClock.uptimeMillis()
        wv.postDelayed({
            wv.dispatchKeyEvent(newKeyEvent(now, action, modKey, scan, 0))
        }, 60L)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== Toolbar =====
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { holder.webView?.goBack() },
                            enabled = canGoBack
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(
                            onClick = { holder.webView?.goForward() },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Maju",
                                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(onClick = { holder.webView?.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                        }
                        IconButton(onClick = { holder.webView?.loadUrl(CRD_HOME) }) {
                            Icon(Icons.Filled.Home, contentDescription = "Beranda")
                        }

                        val url = currentUrl
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (url != null) {
                                Text(
                                    text = when {
                                        url.startsWith("https://") -> "🔒"
                                        url.startsWith("http://") -> "⚠️"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = url?.let { extractDomain(it) } ?: "Loading...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { openInExternalBrowser(null) }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Buka di browser")
                        }
                    }

                    if (pageLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    blockedHint?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ===== WebView =====
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    val existing = holder.webView
                    if (existing != null) return@AndroidView existing

                    WebView(ctx).apply {
                        holder.webView = this

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT

                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

                        isFocusable = true
                        isFocusableInTouchMode = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (!isAllowedUrl(url)) {
                                    view?.stopLoading()
                                    handleBlockedUrl(url)
                                    return
                                }
                                pageLoading = true
                                currentUrl = url
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                pageLoading = false
                                currentUrl = url
                                blockedHint = null
                                updateNavButtons()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest
                            ): Boolean {
                                if (isAllowedUrl(request.url.toString())) return false
                                handleBlockedUrl(request.url.toString())
                                return true
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?, url: String?, isReload: Boolean
                            ) {
                                updateNavButtons()
                            }

                            override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent) {
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?, isDialog: Boolean,
                                isUserGesture: Boolean, resultMsg: android.os.Message
                            ): Boolean {
                                return false
                            }
                        }

                        if (holder.savedBundle.size() > 0) {
                            restoreState(holder.savedBundle)
                        } else {
                            loadUrl(CRD_HOME)
                        }
                    }
                }
            )

            // ===== Shortcut bar =====
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShortcutKey("Esc") { dispatchKey(KeyEvent.KEYCODE_ESCAPE, SCAN_ESC) }
                    ShortcutKey("Tab") { dispatchKey(KeyEvent.KEYCODE_TAB, SCAN_TAB) }
                    ShortcutKey("↑") { dispatchKey(KeyEvent.KEYCODE_DPAD_UP, SCAN_UP) }
                    ShortcutKey("↓") { dispatchKey(KeyEvent.KEYCODE_DPAD_DOWN, SCAN_DOWN) }
                    ShortcutKey("←") { dispatchKey(KeyEvent.KEYCODE_DPAD_LEFT, SCAN_LEFT) }
                    ShortcutKey("→") { dispatchKey(KeyEvent.KEYCODE_DPAD_RIGHT, SCAN_RIGHT) }
                    ShortcutKey("Enter") { dispatchKey(KeyEvent.KEYCODE_ENTER, SCAN_ENTER) }
                    ShortcutKey("⌫") { dispatchKey(KeyEvent.KEYCODE_DEL, SCAN_BACKSPACE) }
                    ModifierKey("Ctrl", ctrlToggled) { toggleModifier(isCtrl = true) }
                    ModifierKey("Alt", altToggled) { toggleModifier(isCtrl = false) }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Lifecycle: pause/resume + capture state
    DisposableEffect(lifecycleOwner, holder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    holder.webView?.onResume()
                    holder.webView?.requestFocus()
                }
                Lifecycle.Event.ON_PAUSE -> holder.webView?.onPause()
                Lifecycle.Event.ON_STOP -> holder.captureState()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.captureState()
        }
    }
}

@Composable
private fun ShortcutKey(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(width = 48.dp, height = 32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModifierKey(label: String, lit: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (lit) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(width = 56.dp, height = 32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (lit) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun isAllowedUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    if (url == "about:blank") return true
    return try {
        val uri = Uri.parse(url)
        val host = uri.host ?: return false
        if (host !in ALLOWED_HOSTS) return false
        if (host == CRD_HOST) {
            val path = uri.path ?: return false
            ALLOWED_CRD_PATHS.any { path == it || path.startsWith("$it/") }
        } else {
            true
        }
    } catch (_: Exception) {
        false
    }
}

private fun extractDomain(url: String): String {
    return try {
        val uri = Uri.parse(url)
        uri.host ?: url
    } catch (_: Exception) {
        url
    }
}

private fun newKeyEvent(time: Long, action: Int, keyCode: Int, scanCode: Int, meta: Int): KeyEvent =
    KeyEvent(
        time, time, action, keyCode, 0, meta,
        KeyCharacterMap.VIRTUAL_KEYBOARD, scanCode, 0, InputDevice.SOURCE_KEYBOARD
    )

private const val CRD_HOME = "https://remotedesktop.google.com/access"
private const val CRD_HOST = "remotedesktop.google.com"

private val ALLOWED_HOSTS = setOf(
    CRD_HOST,
    "accounts.google.com",
    "accounts.youtube.com",
    "consent.google.com"
)
private val ALLOWED_CRD_PATHS = listOf("/access", "/access/")

private const val SCAN_ESC = 1
private const val SCAN_TAB = 15
private const val SCAN_ENTER = 28
private const val SCAN_BACKSPACE = 14
private const val SCAN_UP = 103
private const val SCAN_DOWN = 108
private const val SCAN_LEFT = 105
private const val SCAN_RIGHT = 106
private const val SCAN_CTRL_LEFT = 29
private const val SCAN_ALT_LEFT = 56

private const val FOCUS_SCRIPT = """(function(){
    function focusIn(win){
        try{
            var doc=win.document;
            var c=doc.querySelector('canvas');
            if(c&&typeof c.focus==='function'){
                try{c.setAttribute('tabindex','0');c.focus();}catch(e){}
                return;
            }
            var a=doc.activeElement;
            if(a&&a!==doc.body&&typeof a.focus==='function'){try{a.focus();}catch(e){}return;}
            if(doc.body&&typeof doc.body.focus==='function'){try{doc.body.focus();}catch(e){}}
        }catch(e){}
    }
    try{
        var fs=document.querySelectorAll('iframe'),best=null,area=-1;
        for(var i=0;i<fs.length;i++){
            var r=fs[i].getBoundingClientRect(),a=r.width*r.height;
            if(a>area){area=a;best=fs[i];}
        }
        if(best&&best.contentWindow){try{best.contentWindow.focus();}catch(e){}focusIn(best.contentWindow);}
        else{focusIn(window);}
    }catch(e){}
})()"""