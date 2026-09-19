package com.chaoqun.baimo.remote.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamesTest {
    @Test
    fun parsesQuotedFilename() {
        assertEquals(
            "out.mp4",
            DownloadNames.fromContentDisposition("""attachment; filename="out.mp4""""),
        )
    }

    @Test
    fun parsesRfc5987Filename() {
        assertEquals(
            "白模.mp4",
            DownloadNames.fromContentDisposition("attachment; filename*=UTF-8''%E7%99%BD%E6%A8%A1.mp4"),
        )
    }

    @Test
    fun videosGoToMovies() {
        assertEquals(
            DownloadNames.PublicDir.MOVIES,
            DownloadNames.choosePublicDirectory("video/mp4", "result.bin"),
        )
        assertEquals(
            DownloadNames.PublicDir.MOVIES,
            DownloadNames.choosePublicDirectory(null, "clip.mp4"),
        )
        assertEquals(
            DownloadNames.PublicDir.DOWNLOADS,
            DownloadNames.choosePublicDirectory("application/zip", "notes.zip"),
        )
    }

    @Test
    fun addsMp4WhenMissingExtension() {
        assertEquals("result.mp4", DownloadNames.ensureExtension("result", "video/mp4"))
        assertTrue(DownloadNames.isVideo("video/mp4", "x"))
    }
}
