package com.nathan.workspace.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.nathan.workspace.databinding.FragmentWebviewBinding

class WebViewFragment : Fragment() {

    private var _binding: FragmentWebviewBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (webView == null) {
            webView = binding.webview
            webView!!.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        binding.tvHint.visibility = View.GONE
                    }
                }
                webChromeClient = WebChromeClient()
                if (savedInstanceState == null) {
                    loadUrl("https://remotedesktop.google.com/access")
                } else {
                    restoreState(savedInstanceState)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onDestroyView() {
        webView?.let { wv ->
            val parent = wv.parent as? ViewGroup
            parent?.removeView(wv)
        }
        _binding = null
    }

    override fun onDestroy() {
        if (webView != null && !requireActivity().isChangingConfigurations) {
            webView?.destroy()
            webView = null
        }
        super.onDestroy()
    }
}
