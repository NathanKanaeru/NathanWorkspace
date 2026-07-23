package com.nathan.workspace.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.nathan.workspace.databinding.FragmentWebviewBinding

class WebViewFragment : Fragment() {

    private var _binding: FragmentWebviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("https://remotedesktop.google.com/access")
        }

        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                super.onPageFinished(view, url)
                binding.tvHint.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
