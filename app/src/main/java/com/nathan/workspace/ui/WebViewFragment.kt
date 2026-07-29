package com.nathan.workspace.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.nathan.workspace.databinding.FragmentWebviewBinding

class WebViewFragment : Fragment() {

    private var _binding: FragmentWebviewBinding? = null
    private val binding get() = _binding!!

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
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (_binding != null) {
                        binding.progressBar.isVisible = true
                        binding.tvUrl.text = url?.let { extractDomain(it) } ?: "Loading..."
                        updateSecurityIcon(url)
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (_binding != null) {
                        binding.progressBar.isVisible = false
                        binding.tvUrl.text = extractDomain(url)
                        binding.tvHint.visibility = View.GONE
                        updateNavButtons()
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    updateNavButtons()
                }
            }

            webChromeClient = WebChromeClient()

            if (savedInstanceState != null) {
                restoreState(savedInstanceState)
            } else {
                loadUrl("https://remotedesktop.google.com/access")
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
            binding.webview.loadUrl("https://remotedesktop.google.com/access")
        }

        binding.btnExternal.setOnClickListener {
            val url = binding.webview.url
            if (!url.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                ContextCompat.startActivity(requireContext(), intent, null)
            }
        }

        updateNavButtons()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webview.saveState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}