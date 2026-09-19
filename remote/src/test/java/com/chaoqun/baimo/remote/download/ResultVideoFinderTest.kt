package com.chaoqun.baimo.remote.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultVideoFinderTest {
    @Test
    fun prefersHttpGradioFileOverBlob() {
        val best = ResultVideoFinder.pickBest(
            listOf(
                "blob:http://103.47.82.57:47860/abc",
                "http://103.47.82.57:47860/gradio_api/file=/tmp/gradio/out.mp4",
            ),
        )
        assertEquals(
            "http://103.47.82.57:47860/gradio_api/file=/tmp/gradio/out.mp4",
            best,
        )
    }

    @Test
    fun lastHttpVideoWins() {
        val best = ResultVideoFinder.pickBest(
            listOf(
                "http://host/a.mp4",
                "http://host/file=/tmp/b.mp4",
            ),
        )
        assertEquals("http://host/file=/tmp/b.mp4", best)
    }

    @Test
    fun ignoresCssAndEmpty() {
        assertNull(ResultVideoFinder.pickBest(listOf("http://host/app.css", "  ")))
        assertFalse(ResultVideoFinder.isLikelyResultUrl("http://host/theme.css"))
        assertTrue(ResultVideoFinder.isLikelyResultUrl("http://host/file=/tmp/x"))
    }

    @Test
    fun parsesEvaluateJavascriptArray() {
        val inner = """["http://h/a.mp4","blob:http://h/1"]"""
        assertEquals(
            listOf("http://h/a.mp4", "blob:http://h/1"),
            ResultVideoFinder.parseEvaluateJsonArray(inner),
        )
        val wrapped = "\"" + inner.replace("\"", "\\\"") + "\""
        assertEquals(
            listOf("http://h/a.mp4", "blob:http://h/1"),
            ResultVideoFinder.parseEvaluateJsonArray(wrapped),
        )
    }

    @Test
    fun albumMimeForcesVideo() {
        assertEquals("video/mp4", ResultVideoFinder.albumMime("application/octet-stream", "out.mp4", "http://x/out.mp4"))
        assertEquals("video/webm", ResultVideoFinder.albumMime("video/webm; charset=binary", "a", "blob:x"))
        assertEquals("clip.mp4", ResultVideoFinder.displayName(null, "http://h/clip.mp4", null))
    }
}
