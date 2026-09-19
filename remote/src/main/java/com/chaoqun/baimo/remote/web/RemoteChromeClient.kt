package com.chaoqun.baimo.remote.web

import android.net.Uri
import android.os.Message
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.chaoqun.baimo.remote.picker.GalleryVideoPicker

class RemoteChromeClient(
    private val picker: GalleryVideoPicker,
    private val onProgress: (Int) -> Unit,
) : WebChromeClient() {

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        if (filePathCallback == null) return false
        return picker.launch(filePathCallback, fileChooserParams)
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        // Album video pick does not need camera/mic; deny so Gradio cannot pivot to capture UI.
        request?.deny()
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val temp = WebView(view.context).apply {
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: android.webkit.WebView,
                    request: android.webkit.WebResourceRequest,
                ): Boolean {
                    view.loadUrl(request.url.toString())
                    return true
                }
            }
        }
        transport.webView = temp
        resultMsg.sendToTarget()
        return true
    }
}
