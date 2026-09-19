package com.chaoqun.baimo.remote.picker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChooserStrategyTest {
    @Test
    fun primaryPathIsPhotoPickerVideoOnly() {
        assertEquals("photo_picker_video_only", FileChooserStrategy.PRIMARY)
        assertEquals(FileChooserStrategy.PRIMARY_PHOTO_PICKER_VIDEO_ONLY, FileChooserStrategy.PRIMARY)
    }

    @Test
    fun fallbackIsGalleryPickThenGetContentVideoOnly() {
        assertEquals(
            listOf("action_pick_video", "action_get_content_video"),
            FileChooserStrategy.fallbackOrder,
        )
        assertFalse(FileChooserStrategy.fallbackOrder.contains("action_open_document"))
        assertFalse(FileChooserStrategy.fallbackOrder.first() == "action_get_content_video")
    }

    @Test
    fun mimeIsVideoOnly() {
        assertEquals("video/*", FileChooserStrategy.MIME_VIDEO)
        assertArrayEquals(arrayOf("video/*"), FileChooserStrategy.extraMimeTypes())
    }

    @Test
    fun multipleModeMatchesFileChooserParams() {
        assertTrue(FileChooserStrategy.allowMultiple(FileChooserStrategy.MODE_OPEN_MULTIPLE))
        assertFalse(FileChooserStrategy.allowMultiple(0))
    }
}
