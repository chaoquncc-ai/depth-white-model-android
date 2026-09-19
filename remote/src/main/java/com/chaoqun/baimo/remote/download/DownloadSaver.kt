package com.chaoqun.baimo.remote.download

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaoqun.baimo.remote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class DownloadSaver(private val activity: ComponentActivity) {
    val jsBridge = JsBridge()

    private var pending: (() -> Unit)? = null
    private val notifyId = AtomicInteger(1000)

    private val requestWrite = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pending?.invoke()
        } else {
            onMessage?.invoke(activity.getString(R.string.download_failed))
        }
        pending = null
    }

    var onMessage: ((String) -> Unit)? = null

    fun enqueue(
        webView: WebView,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val run = {
            when {
                url.startsWith("blob:", ignoreCase = true) -> saveBlob(webView, url, contentDisposition, mimeType)
                url.startsWith("data:", ignoreCase = true) -> saveDataUrl(url, contentDisposition, mimeType)
                else -> enqueueHttp(url, userAgent, contentDisposition, mimeType)
            }
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pending = run
            requestWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        run()
    }

    private fun enqueueHttp(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val named = DownloadNames.fromContentDisposition(contentDisposition)
            ?: DownloadNames.fromUrl(url)
            ?: guessed
        val filename = DownloadNames.ensureExtension(named, mimeType)
        val dir = DownloadNames.choosePublicDirectory(mimeType, filename)
        val publicDir = if (dir == DownloadNames.PublicDir.MOVIES) {
            Environment.DIRECTORY_MOVIES
        } else {
            Environment.DIRECTORY_DOWNLOADS
        }
        val cookies = CookieManager.getInstance().getCookie(url)
        fun buildRequest(subPath: String): DownloadManager.Request {
            return DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setTitle(filename)
                setDescription(activity.getString(R.string.download_channel))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", userAgent ?: "")
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                setDestinationInExternalPublicDir(publicDir, subPath)
            }
        }
        try {
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            try {
                manager.enqueue(buildRequest("BaiMoRemote/$filename"))
            } catch (_: Exception) {
                manager.enqueue(buildRequest(filename))
            }
            onMessage?.invoke(activity.getString(R.string.download_started))
        } catch (_: Exception) {
            onMessage?.invoke(activity.getString(R.string.download_failed))
        }
    }

    private fun saveBlob(
        webView: WebView,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val filename = DownloadNames.ensureExtension(
            DownloadNames.fromContentDisposition(contentDisposition) ?: "baimo-result.mp4",
            mimeType,
        )
        val mime = mimeType ?: "video/mp4"
        val script = """
            (async function() {
              try {
                const resp = await fetch(${JSONObject.quote(url)});
                const blob = await resp.blob();
                const reader = new FileReader();
                reader.onloadend = function() {
                  BaimoRemote.saveBase64(reader.result || '', ${JSONObject.quote(filename)}, ${JSONObject.quote(mime)});
                };
                reader.readAsDataURL(blob);
              } catch (e) {
                BaimoRemote.saveBase64('', ${JSONObject.quote(filename)}, ${JSONObject.quote(mime)});
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun saveDataUrl(url: String, contentDisposition: String?, mimeType: String?) {
        val filename = DownloadNames.ensureExtension(
            DownloadNames.fromContentDisposition(contentDisposition) ?: "baimo-result.mp4",
            mimeType,
        )
        persistDataUrl(url, filename, mimeType ?: "application/octet-stream")
    }

    private fun persistDataUrl(dataUrl: String, filename: String, mimeType: String) {
        activity.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                writeDataUrl(dataUrl, filename, mimeType)
            }
            onMessage?.invoke(
                activity.getString(if (ok) R.string.download_started else R.string.download_failed),
            )
            if (ok) notifySaved(filename)
        }
    }

    private fun writeDataUrl(dataUrl: String, filename: String, mimeType: String): Boolean {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return false
        val bytes = try {
            Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        } catch (_: Exception) {
            return false
        }
        val video = DownloadNames.isVideo(mimeType, filename)
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= 29) {
                val relative = if (video) {
                    Environment.DIRECTORY_MOVIES + "/BaiMoRemote"
                } else {
                    Environment.DIRECTORY_DOWNLOADS + "/BaiMoRemote"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            if (video) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        } else {
            if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Files.getContentUri("external")
        }
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun notifySaved(filename: String) {
        val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                activity.getString(R.string.download_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = activity.getString(R.string.download_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(activity, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(activity.getString(R.string.download_complete))
            .setContentText(filename)
            .setAutoCancel(true)
            .build()
        manager.notify(notifyId.incrementAndGet(), notification)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun saveBase64(dataUrl: String, filename: String, mimeType: String) {
            if (dataUrl.isBlank()) {
                activity.runOnUiThread {
                    onMessage?.invoke(activity.getString(R.string.download_failed))
                }
                return
            }
            persistDataUrl(dataUrl, filename, mimeType.ifBlank { "video/mp4" })
        }
    }

    private companion object {
        const val CHANNEL_ID = "baimo_remote_downloads"
    }
}
