package com.nathan.workspace.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

        updateNavButtons()
    }

    private fun updateNavButtons() {
        if (_binding == null) return
        binding.btnBack.isEnabled = binding.webview.canGoBack()
        binding.btnForward.isEnabled = binding.webview.canGoForward()
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host ?: url
        } catch (_: Exception) {
            url
        }
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