package com.chaoqun.depthwhite.ml

import android.content.Context
import android.util.Log
import com.chaoqun.depthwhite.core.ModelSize
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class ModelStore(private val context: Context) {

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(size: ModelSize): File = File(modelsDir(), size.fileName)

    fun isReady(size: ModelSize): Boolean {
        if (isOnDisk(size)) return true
        // Bundled Small counts as ready: convert copies it off the UI thread.
        return hasBundledAsset(size)
    }

    fun isOnDisk(size: ModelSize): Boolean {
        val file = modelFile(size)
        return file.exists() && file.length() > minAcceptableBytes(size)
    }

    fun hasBundledAsset(size: ModelSize): Boolean {
        val files = runCatching { context.assets.list(ASSET_DIR) }.getOrNull() ?: return false
        if (size.fileName !in files) return false
        val length = bundledAssetLength(size)
        // openFd fails for compressed assets; still treat a listed ONNX as bundled.
        return length < 0L || length > minAcceptableBytes(size)
    }

    /**
     * If the ONNX is packaged in APK assets, copy it to internal storage.
     * Safe to call repeatedly; skips when the disk copy is already valid.
     */
    fun ensureFromAssets(size: ModelSize): File? {
        synchronized(assetCopyLock) {
            if (isOnDisk(size)) return modelFile(size)
            if (!hasBundledAsset(size)) return null
            return copyAssetToDisk(size)
        }
    }

    fun resolveExisting(size: ModelSize): File? {
        if (isOnDisk(size)) return modelFile(size)
        return ensureFromAssets(size)
    }

    fun deletePartial(size: ModelSize) {
        val part = File(modelsDir(), size.fileName + ".part")
        if (part.exists()) part.delete()
    }

    /**
     * Prefer a bundled asset (Small) so first launch does not need the network.
     * Base / Large still download on demand.
     */
    fun download(
        size: ModelSize,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): File {
        resolveExisting(size)?.let { return it }
        val dest = modelFile(size)
        val part = File(modelsDir(), size.fileName + ".part")
        var lastError: Exception? = null
        for (url in size.downloadUrls) {
            if (isCancelled()) throw InterruptedException("cancelled")
            try {
                downloadUrl(url, part, dest, size, onProgress, isCancelled)
                return dest
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("模型下载失败")
    }

    private fun bundledAssetLength(size: ModelSize): Long {
        return try {
            context.assets.openFd(size.assetPath).use { it.length }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun copyAssetToDisk(size: ModelSize): File? {
        val dest = modelFile(size)
        val part = File(modelsDir(), size.fileName + ".asset.part")
        return try {
            context.assets.open(size.assetPath).use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            if (part.length() < minAcceptableBytes(size)) {
                part.delete()
                return null
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            dest.takeIf { it.exists() && it.length() > minAcceptableBytes(size) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy bundled ${size.fileName}", e)
            part.delete()
            null
        }
    }

    private fun downloadUrl(
        urlSpec: String,
        part: File,
        dest: File,
        size: ModelSize,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val existing = if (part.exists()) part.length() else 0L
        val connection = (URL(urlSpec).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "DepthWhiteModelAndroid/1.0")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..206) {
            connection.disconnect()
            throw IllegalStateException("下载失败 HTTP $code")
        }
        val totalFromHeader = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        val total = when {
            code == 206 && totalFromHeader > 0 -> existing + totalFromHeader
            code == 200 && totalFromHeader > 0 -> totalFromHeader
            else -> size.approxBytes
        }
        if (code == 200 && existing > 0) {
            part.delete()
        }
        val append = code == 206 && existing > 0
        connection.inputStream.use { input ->
            FileOutputStream(part, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = if (append) existing else 0L
                while (true) {
                    if (isCancelled()) throw InterruptedException("cancelled")
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
                output.flush()
            }
        }
        connection.disconnect()
        if (part.length() < minAcceptableBytes(size)) {
            part.delete()
            throw IllegalStateException("模型文件不完整，请重试下载")
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
    }

    companion object {
        const val ASSET_DIR = "models"
        private const val TAG = "ModelStore"
        private val warmupStarted = AtomicBoolean(false)
        private val assetCopyLock = Any()

        fun minAcceptableBytes(size: ModelSize): Long = (size.approxBytes * 0.6).toLong()

        /** Copy bundled Small off the main thread so first convert is offline-ready. */
        fun warmupBundledSmall(context: Context) {
            if (!warmupStarted.compareAndSet(false, true)) return
            Thread({
                runCatching { ModelStore(context.applicationContext).ensureFromAssets(ModelSize.SMALL) }
            }, "model-asset-warmup").apply {
                isDaemon = true
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, t ->
                    Log.w(TAG, "warmup failed", t)
                }
                start()
            }
        }
    }

    private fun minAcceptableBytes(size: ModelSize): Long = Companion.minAcceptableBytes(size)
}
