package com.chaoqun.baimo.remote.web

import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class RemoteWebViewClient(
    private val onPageStarted: () -> Unit,
    private val onPageFinished: (WebView) -> Unit,
    private val onMainFrameError: (String?) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase()
        if (scheme == "http" || scheme == "https" || scheme == "blob" || scheme == "data") {
            return false
        }
        return try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
            true
        } catch (_: Exception) {
            true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onPageStarted()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (view != null) onPageFinished(view)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onMainFrameError(error.description?.toString())
        }
    }
}
