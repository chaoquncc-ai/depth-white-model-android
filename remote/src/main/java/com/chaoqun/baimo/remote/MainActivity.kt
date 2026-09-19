package com.chaoqun.baimo.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.chaoqun.baimo.remote.download.DownloadSaver
import com.chaoqun.baimo.remote.picker.GalleryVideoPicker
import com.chaoqun.baimo.remote.ui.RemoteScreen
import com.chaoqun.baimo.remote.ui.theme.BaiMoRemoteTheme
import com.chaoqun.baimo.remote.web.RemoteChromeClient
import com.chaoqun.baimo.remote.web.RemoteWebViewClient

class MainActivity : ComponentActivity() {
    private val videoPicker = GalleryVideoPicker(this)
    private val downloadSaver = DownloadSaver(this)
    private val vm: RemoteViewModel by viewModels()
    private var webView: WebView? = null

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* DownloadManager still writes files if notifications are denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyKeepScreenOn(vm.keepScreenOn)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        maybeRequestNotifications()
        downloadSaver.onMessage = { vm.showMessage(it) }

        setContent {
            BaiMoRemoteTheme {
                BackHandler(enabled = !vm.showSettings) {
                    val view = webView
                    if (view != null && view.canGoBack()) {
                        view.goBack()
                    } else {
                        finish()
                    }
                }
                RemoteScreen(
                    vm = vm,
                    ensureWebView = ::ensureWebView,
                    onReload = { webView?.reload() },
                    onOpenBrowser = ::openInBrowser,
                    onClearCache = ::clearCacheAndReload,
                    onSaveUrl = { raw ->
                        vm.saveUrl(raw)
                        loadServer(vm.serverUrl)
                    },
                    onResetUrl = {
                        vm.resetUrl()
                        loadServer(vm.serverUrl)
                    },
                    onApplyKeepScreenOn = ::applyKeepScreenOn,
                )
            }
        }
    }

    override fun onDestroy() {
        videoPicker.cancelPending()
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun loadServer(url: String) {
        webView?.loadUrl(url)
        vm.setPageError(null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(context: android.content.Context): WebView {
        webView?.let { existing ->
            (existing.parent as? android.view.ViewGroup)?.removeView(existing)
            return existing
        }
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = RemoteWebViewClient(
                onPageStarted = { vm.setPageError(null) },
                onPageFinished = { vm.setProgress(100) },
                onMainFrameError = { vm.setPageError(it ?: getString(R.string.page_error)) },
            )
            webChromeClient = RemoteChromeClient(videoPicker) { vm.setProgress(it) }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                downloadSaver.enqueue(this, url, userAgent, contentDisposition, mimeType)
            }
            addJavascriptInterface(downloadSaver.jsBridge, "BaimoRemote")
            loadUrl(vm.serverUrl)
        }
        webView = view
        return view
    }

    private fun openInBrowser() {
        val url = webView?.url ?: vm.serverUrl
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            vm.showMessage(getString(R.string.page_error))
        }
    }

    private fun clearCacheAndReload() {
        webView?.clearCache(true)
        webView?.clearFormData()
        webView?.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView?.loadUrl(vm.serverUrl)
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
