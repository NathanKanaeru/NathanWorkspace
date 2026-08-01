package com.nathan.workspace.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.nathan.workspace.databinding.FragmentWebviewBinding

class WebViewFragment : Fragment() {

    private var _binding: FragmentWebviewBinding? = null
    private val binding get() = _binding!!

    private var ctrlToggled = false
    private var altToggled = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.webview.apply {
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

            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (_binding == null) return
                    // Safety net: URL yang tidak diizinkan (termasuk dari saved state)
                    if (!isAllowedUrl(url)) {
                        view?.stopLoading()
                        handleBlockedUrl(url)
                        return
                    }
                    binding.progressBar.isVisible = true
                    binding.tvUrl.text = url?.let { extractDomain(it) } ?: "Loading..."
                    updateSecurityIcon(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (_binding != null) {
                        binding.progressBar.isVisible = false
                        binding.tvUrl.text = extractDomain(url)
                        binding.tvHint.visibility = View.GONE
                        updateNavButtons()
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest
                ): Boolean {
                    if (isAllowedUrl(request.url.toString())) return false
                    handleBlockedUrl(request.url.toString())
                    return true
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    updateNavButtons()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean,
                    isUserGesture: Boolean, resultMsg: android.os.Message
                ): Boolean {
                    // Blok popup window.open; target=_blank jatuh ke navigasi in-view
                    return false
                }
            }

            if (savedInstanceState != null) {
                restoreState(savedInstanceState)
            } else {
                loadUrl(CRD_HOME)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.cardToolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }

        // Toolbar navigation
        binding.btnBack.setOnClickListener {
            if (binding.webview.canGoBack()) {
                binding.webview.goBack()
            }
        }

        binding.btnForward.setOnClickListener {
            if (binding.webview.canGoForward()) {
                binding.webview.goForward()
            }
        }

        binding.btnRefresh.setOnClickListener {
            binding.webview.reload()
        }

        binding.btnHome.setOnClickListener {
            binding.webview.loadUrl(CRD_HOME)
        }

        binding.btnExternal.setOnClickListener {
            openInExternalBrowser(null)
        }

        // ===== Shortcut bar =====
        binding.btnKEsc.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_ESCAPE) }
        binding.btnKTab.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_TAB) }
        binding.btnKUp.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_DPAD_UP) }
        binding.btnKDown.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        binding.btnKLeft.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        binding.btnKRight.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        binding.btnKEnter.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_ENTER) }
        binding.btnKBackspace.setOnClickListener { dispatchKey(KeyEvent.KEYCODE_DEL) }
        binding.btnKCtrl.setOnClickListener { toggleModifier(isCtrl = true) }
        binding.btnKAlt.setOnClickListener { toggleModifier(isCtrl = false) }

        updateNavButtons()
    }

    /** Hanya izinkan CRD /access (+ /access/...) dan host OAuth Google untuk login. */
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

    /** Tampilkan notice diblokir + Snackbar dengan aksi buka di browser eksternal. */
    private fun handleBlockedUrl(url: String?) {
        if (_binding == null) return
        binding.tvHint.text = "Halaman diblokir (hanya remotedesktop.google.com).\nGunakan tombol \"Buka di Browser\"."
        binding.tvHint.visibility = View.VISIBLE
        Snackbar.make(binding.root, "Navigasi diblokir", Snackbar.LENGTH_LONG)
            .setAction("Buka di Browser") { openInExternalBrowser(url) }
            .show()
    }

    /** Buka URL di browser eksternal (fallback karena CRD bisa menolak WebView). */
    private fun openInExternalBrowser(url: String?) {
        val target = url ?: binding.webview.url ?: CRD_HOME
        if (target.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
            ContextCompat.startActivity(requireContext(), intent, null)
        } catch (_: ActivityNotFoundException) { }
    }

    private fun updateSecurityIcon(url: String?) {
        if (_binding == null) return
        if (url != null && url.startsWith("https://")) {
            binding.tvUrlIcon.text = "🔒"
            binding.tvUrlIcon.visibility = View.VISIBLE
        } else if (url != null && url.startsWith("http://")) {
            binding.tvUrlIcon.text = "⚠️"
            binding.tvUrlIcon.visibility = View.VISIBLE
        } else {
            binding.tvUrlIcon.visibility = View.GONE
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host ?: url
        } catch (_: Exception) {
            url
        }
    }

    private fun updateNavButtons() {
        if (_binding == null) return
        binding.btnBack.isEnabled = binding.webview.canGoBack()
        binding.btnForward.isEnabled = binding.webview.canGoForward()
        val backAlpha = if (binding.btnBack.isEnabled) 1.0f else 0.38f
        val fwdAlpha = if (binding.btnForward.isEnabled) 1.0f else 0.38f
        binding.btnBack.alpha = backAlpha
        binding.btnForward.alpha = fwdAlpha
    }

    // ===== Shortcut key handling =====

    /** Kirim tombol ke WebView sebagai pasangan down+up (event trusted dari input pipeline). */
    private fun dispatchKey(keyCode: Int) {
        val wv = binding.webview
        wv.requestFocus()
        var meta = 0
        if (ctrlToggled) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (altToggled) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        val now = SystemClock.uptimeMillis()
        wv.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        wv.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    /** Toggle Ctrl/Alt: flip state, perbarui visual, kirim key event modifier asli. */
    private fun toggleModifier(isCtrl: Boolean) {
        val button = if (isCtrl) binding.btnKCtrl else binding.btnKAlt
        val turningOn = !(if (isCtrl) ctrlToggled else altToggled)
        if (isCtrl) ctrlToggled = turningOn else altToggled = turningOn
        setToggleLit(button, turningOn)

        val wv = binding.webview
        wv.requestFocus()
        val now = SystemClock.uptimeMillis()
        val modKey = if (isCtrl) KeyEvent.KEYCODE_CTRL_LEFT else KeyEvent.KEYCODE_ALT_LEFT
        val action = if (turningOn) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        wv.dispatchKeyEvent(KeyEvent(now, now, action, modKey, 0, 0))
    }

    private fun setToggleLit(button: MaterialButton, lit: Boolean) {
        button.isSelected = lit
        button.backgroundTintList = if (lit) {
            ColorStateList.valueOf(
                MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimaryContainer)
            )
        } else {
            null // OutlinedButton default: transparan
        }
        button.setTextColor(
            MaterialColors.getColor(
                button,
                if (lit) com.google.android.material.R.attr.colorOnPrimaryContainer
                else android.R.attr.textColorPrimary
            )
        )
    }

    override fun onResume() {
        super.onResume()
        _binding?.webview?.onResume()
        _binding?.webview?.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        _binding?.webview?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webview.saveState(outState)
    }

    override fun onDestroyView() {
        _binding?.webview?.destroy()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CRD_HOME = "https://remotedesktop.google.com/access"
        private const val CRD_HOST = "remotedesktop.google.com"

        // Host OAuth Google dibolehkan agar login tetap jalan
        private val ALLOWED_HOSTS = setOf(
            CRD_HOST,
            "accounts.google.com",
            "accounts.youtube.com",
            "consent.google.com"
        )
        private val ALLOWED_CRD_PATHS = listOf("/access", "/access/")
    }
}
