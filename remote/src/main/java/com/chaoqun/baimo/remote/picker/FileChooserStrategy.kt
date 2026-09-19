package com.chaoqun.baimo.remote.picker

// Gradio file inputs must open the system album/gallery video picker, never a
// document-file-browser-first flow.
//
// Order:
// 1. Android Photo Picker (PickVisualMedia / PickMultipleVisualMedia, VideoOnly)
// 2. Intent.ACTION_PICK with video MIME (gallery)
// 3. Intent.ACTION_GET_CONTENT with video MIME only if Photo Picker and ACTION_PICK
//    are unavailable. MIME_VIDEO is the video wildcard type.
object FileChooserStrategy {
    const val PRIMARY_PHOTO_PICKER_VIDEO_ONLY = "photo_picker_video_only"
    const val FALLBACK_ACTION_PICK_VIDEO = "action_pick_video"
    const val FALLBACK_ACTION_GET_CONTENT_VIDEO = "action_get_content_video"
    const val PRIMARY = PRIMARY_PHOTO_PICKER_VIDEO_ONLY
    const val MIME_VIDEO = "video/*"

    val fallbackOrder: List<String> = listOf(
        FALLBACK_ACTION_PICK_VIDEO,
        FALLBACK_ACTION_GET_CONTENT_VIDEO,
    )

    fun extraMimeTypes(): Array<String> = arrayOf(MIME_VIDEO)

    fun allowMultiple(fileChooserMode: Int): Boolean = fileChooserMode == MODE_OPEN_MULTIPLE

    // WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
    const val MODE_OPEN_MULTIPLE = 1
}
