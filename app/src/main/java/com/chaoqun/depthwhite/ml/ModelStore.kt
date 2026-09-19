package com.chaoqun.depthwhite.ml

import android.content.Context
import com.chaoqun.depthwhite.core.ModelSize
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelStore(private val context: Context) {

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(size: ModelSize): File = File(modelsDir(), size.fileName)

    fun isReady(size: ModelSize): Boolean {
        val file = resolveExisting(size) ?: return false
        return file.length() > minAcceptableBytes(size)
    }

    fun resolveExisting(size: ModelSize): File? {
        val disk = modelFile(size)
        if (disk.exists() && disk.length() > minAcceptableBytes(size)) return disk
        val assetName = "models/${size.fileName}"
        return try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(disk).use { output -> input.copyTo(output) }
            }
            disk.takeIf { it.exists() && it.length() > minAcceptableBytes(size) }
        } catch (_: Exception) {
            null
        }
    }

    fun deletePartial(size: ModelSize) {
        val part = File(modelsDir(), size.fileName + ".part")
        if (part.exists()) part.delete()
    }

    fun download(
        size: ModelSize,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): File {
        val dest = modelFile(size)
        if (dest.exists() && dest.length() > minAcceptableBytes(size)) return dest
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

    private fun minAcceptableBytes(size: ModelSize): Long = (size.approxBytes * 0.6).toLong()
}
