package com.chaoqun.baimo.remote.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.webkit.CookieManager
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AlbumVideoSaver(private val activity: ComponentActivity) {
    var latest: ResultVideo? = null
        private set

    var onLatestChanged: ((Boolean) -> Unit)? = null
    var onSaving: ((Boolean) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null

    private val busy = AtomicBoolean(false)
    private val notifyId = AtomicInteger(2000)
    private var pending: (() -> Unit)? = null
    private var userAgent: String? = null
    private var pageUrl: String? = null

    private val requestWrite = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pending?.invoke()
        } else {
            onMessage?.invoke(activity.getString(R.string.save_to_album_failed))
        }
        pending = null
    }

    fun remember(
        url: String,
        mimeType: String? = null,
        contentDisposition: String? = null,
        filename: String? = null,
    ) {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || !ResultVideoFinder.isLikelyResultUrl(trimmed)) return
        val name = filename
            ?: DownloadNames.fromContentDisposition(contentDisposition)
            ?: ResultVideoFinder.displayName(null, trimmed, mimeType)
        latest = ResultVideo(
            url = trimmed,
            mimeType = mimeType,
            filename = name,
        )
        onLatestChanged?.invoke(true)
    }

    fun clear() {
        latest = null
        onLatestChanged?.invoke(false)
    }

    fun injectObserver(webView: WebView) {
        userAgent = webView.settings.userAgentString
        pageUrl = webView.url
        webView.evaluateJavascript(OBSERVER_JS, null)
    }

    fun saveToAlbum(webView: WebView) {
        userAgent = webView.settings.userAgentString
        pageUrl = webView.url
        if (!busy.compareAndSet(false, true)) return
        val run = {
            val known = latest
            if (known != null) {
                persist(webView, known)
            } else {
                discover(webView) { found ->
                    if (found == null) {
                        finish(false, activity.getString(R.string.save_to_album_none))
                    } else {
                        remember(found)
                        latest?.let { persist(webView, it) } ?: finish(
                            false,
                            activity.getString(R.string.save_to_album_none),
                        )
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            busy.set(false)
            pending = { saveToAlbum(webView) }
            requestWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        onSaving?.invoke(true)
        onMessage?.invoke(activity.getString(R.string.save_to_album_saving))
        run()
    }

    private fun discover(webView: WebView, done: (String?) -> Unit) {
        webView.evaluateJavascript(DISCOVER_JS) { raw ->
            val best = ResultVideoFinder.pickBest(ResultVideoFinder.parseEvaluateJsonArray(raw))
            done(best)
        }
    }

    private fun persist(webView: WebView, video: ResultVideo) {
        when {
            ResultVideoFinder.isHttp(video.url) -> persistHttp(video)
            ResultVideoFinder.isBlob(video.url) || video.url.startsWith("data:", ignoreCase = true) ->
                persistBlobOrData(webView, video)
            else -> persistHttp(video)
        }
    }

    private fun persistHttp(video: ResultVideo) {
        activity.lifecycleScope.launch {
            val filename = ResultVideoFinder.displayName(video.filename, video.url, video.mimeType)
            val ok = withContext(Dispatchers.IO) {
                fetchHttpIntoAlbum(video.url, filename, video.mimeType)
            }
            finish(
                ok,
                activity.getString(if (ok) R.string.save_to_album_ok else R.string.save_to_album_failed),
            )
            if (ok) notifySaved(filename)
        }
    }

    private fun persistBlobOrData(webView: WebView, video: ResultVideo) {
        val filename = ResultVideoFinder.displayName(video.filename, video.url, video.mimeType)
        val mime = ResultVideoFinder.albumMime(video.mimeType, filename, video.url)
        if (video.url.startsWith("data:", ignoreCase = true)) {
            activity.lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    writeDataUrlToAlbum(video.url, filename, mime)
                }
                finish(
                    ok,
                    activity.getString(if (ok) R.string.save_to_album_ok else R.string.save_to_album_failed),
                )
                if (ok) notifySaved(filename)
            }
            return
        }
        val script = """
            (async function() {
              try {
                const resp = await fetch(${JSONObject.quote(video.url)});
                const blob = await resp.blob();
                const reader = new FileReader();
                reader.onloadend = function() {
                  BaimoRemote.saveAlbumBase64(reader.result || '', ${JSONObject.quote(filename)}, ${JSONObject.quote(mime)});
                };
                reader.readAsDataURL(blob);
              } catch (e) {
                BaimoRemote.saveAlbumBase64('', ${JSONObject.quote(filename)}, ${JSONObject.quote(mime)});
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun persistAlbumBase64(dataUrl: String, filename: String, mimeType: String) {
        if (dataUrl.isBlank()) {
            finish(false, activity.getString(R.string.save_to_album_failed))
            return
        }
        activity.lifecycleScope.launch {
            val mime = ResultVideoFinder.albumMime(mimeType, filename, filename)
            val ok = withContext(Dispatchers.IO) {
                writeDataUrlToAlbum(dataUrl, filename, mime)
            }
            finish(
                ok,
                activity.getString(if (ok) R.string.save_to_album_ok else R.string.save_to_album_failed),
            )
            if (ok) notifySaved(filename)
        }
    }

    private fun fetchHttpIntoAlbum(url: String, filename: String, mimeHint: String?): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 180_000
                setRequestProperty("User-Agent", userAgent ?: "BaiMoRemote")
                pageUrl?.let { setRequestProperty("Referer", it) }
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookies)
                }
            }
            val code = connection.responseCode
            if (code !in 200..299) return false
            val mime = ResultVideoFinder.albumMime(
                connection.contentType ?: mimeHint,
                filename,
                url,
            )
            connection.inputStream.use { input ->
                MediaStoreAlbum.insertVideo(activity, filename, mime) { out ->
                    input.copyTo(out)
                } != null
            }
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun writeDataUrlToAlbum(dataUrl: String, filename: String, mimeType: String): Boolean {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return false
        val bytes = try {
            Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        } catch (_: Exception) {
            return false
        }
        return MediaStoreAlbum.insertVideo(activity, filename, mimeType) { out ->
            out.write(bytes)
        } != null
    }

    private fun finish(ok: Boolean, message: String) {
        busy.set(false)
        onSaving?.invoke(false)
        onMessage?.invoke(message)
        if (ok) {
            onLatestChanged?.invoke(latest != null)
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
            .setContentTitle(activity.getString(R.string.save_to_album_ok))
            .setContentText(filename)
            .setAutoCancel(true)
            .build()
        manager.notify(notifyId.incrementAndGet(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "baimo_remote_album"
        const val OBSERVER_JS = """
            (function(){
              if (window.__baimoAlbumHooked) return;
              window.__baimoAlbumHooked = true;
              function report(url){
                if (!url || !window.BaimoRemote || !BaimoRemote.reportResultVideo) return;
                BaimoRemote.reportResultVideo(String(url), '', '');
              }
              function scan(){
                var nodes = document.querySelectorAll('video');
                for (var i=0;i<nodes.length;i++){
                  var v = nodes[i];
                  var src = v.currentSrc || v.src || '';
                  if (!src) {
                    var source = v.querySelector('source');
                    if (source) src = source.src || '';
                  }
                  if (src) report(src);
                }
              }
              var mo = new MutationObserver(scan);
              mo.observe(document.documentElement, {subtree:true, childList:true, attributes:true, attributeFilter:['src']});
              scan();
            })();
        """
        const val DISCOVER_JS = """
            (function(){
              function abs(u){
                try { return new URL(u, document.baseURI).href; } catch (e) { return String(u || ''); }
              }
              var out = [];
              var videos = document.querySelectorAll('video');
              for (var i=0;i<videos.length;i++){
                var v = videos[i];
                var src = v.currentSrc || v.src || '';
                if (!src) {
                  var source = v.querySelector('source');
                  if (source) src = source.src || '';
                }
                if (src) out.push(abs(src));
              }
              var links = document.querySelectorAll('a[href], a[download]');
              for (var j=0;j<links.length;j++){
                var href = links[j].href || '';
                if (!href) continue;
                var low = href.toLowerCase();
                if (low.indexOf('/file=') >= 0 || low.indexOf('gradio_api/file') >= 0 ||
                    /\.(mp4|webm|mov|mkv)(\?|#|$)/i.test(href)) {
                  out.push(abs(href));
                }
              }
              return JSON.stringify(out);
            })();
        """
    }
}
