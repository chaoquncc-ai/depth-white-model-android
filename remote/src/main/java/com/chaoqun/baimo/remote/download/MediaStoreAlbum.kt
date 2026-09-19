package com.chaoqun.baimo.remote.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object MediaStoreAlbum {
    const val SUBFOLDER = "BaiMoRemote"

    fun insertVideo(
        context: Context,
        filename: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= 29) {
            insertQ(context, filename, mimeType, write)
        } else {
            insertLegacy(context, filename, mimeType, write)
        }
    }

    private fun insertQ(
        context: Context,
        filename: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + SUBFOLDER)
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.DATE_ADDED, now / 1000)
            put(MediaStore.Video.Media.DATE_TAKEN, now)
            put(MediaStore.Video.Media.DATE_MODIFIED, now / 1000)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use(write) ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun insertLegacy(
        context: Context,
        filename: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            SUBFOLDER,
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, filename)
        return try {
            file.outputStream().use(write)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType),
                null,
            )
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }
}
