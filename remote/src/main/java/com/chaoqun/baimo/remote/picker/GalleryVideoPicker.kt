package com.chaoqun.baimo.remote.picker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * Wires Gradio's `<input type=file>` to the system photo album video picker via
 * [WebChromeClient.onShowFileChooser].
 */
class GalleryVideoPicker(private val activity: ComponentActivity) {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val pickSingleVideo = activity.registerForActivityResult(PickVisualMedia()) { uri ->
        deliver(listOfNotNull(uri))
    }

    private val pickMultipleVideo = activity.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        deliver(uris)
    }

    private val fallbackPicker = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        deliver(extractUris(result))
    }

    fun launch(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?,
    ): Boolean {
        cancelPending()
        filePathCallback = callback
        val multiple = FileChooserStrategy.allowMultiple(params?.mode ?: 0)
        if (launchPhotoPicker(multiple)) {
            return true
        }
        return launchFallback(multiple)
    }

    fun cancelPending() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }

    private fun launchPhotoPicker(multiple: Boolean): Boolean {
        if (!PickVisualMedia.isPhotoPickerAvailable(activity)) {
            return false
        }
        val request = PickVisualMediaRequest.Builder()
            .setMediaType(PickVisualMedia.VideoOnly)
            .build()
        return try {
            if (multiple) {
                pickMultipleVideo.launch(request)
            } else {
                pickSingleVideo.launch(request)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchFallback(multiple: Boolean): Boolean {
        val pick = videoIntent(Intent.ACTION_PICK, multiple, openable = false)
        if (pick.resolveActivity(activity.packageManager) != null) {
            fallbackPicker.launch(pick)
            return true
        }
        val getContent = videoIntent(Intent.ACTION_GET_CONTENT, multiple, openable = true)
        fallbackPicker.launch(getContent)
        return true
    }

    private fun videoIntent(action: String, multiple: Boolean, openable: Boolean): Intent {
        return Intent(action).apply {
            type = FileChooserStrategy.MIME_VIDEO
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (openable) {
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            putExtra(Intent.EXTRA_MIME_TYPES, FileChooserStrategy.extraMimeTypes())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }
    }

    private fun deliver(uris: List<Uri>) {
        val callback = filePathCallback ?: return
        filePathCallback = null
        if (uris.isEmpty()) {
            callback.onReceiveValue(null)
            return
        }
        uris.forEach { uri ->
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Photo Picker grants are typically not persistable.
            }
        }
        callback.onReceiveValue(uris.toTypedArray())
    }

    private fun extractUris(result: ActivityResult): List<Uri> {
        if (result.resultCode != Activity.RESULT_OK) return emptyList()
        val intent = result.data ?: return emptyList()
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) {
            return buildList {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { add(it) }
                }
            }
        }
        return listOfNotNull(intent.data)
    }
}
