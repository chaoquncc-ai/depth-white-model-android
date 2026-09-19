package com.chaoqun.depthwhite.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSizingTest {
    @Test
    fun evenRoundsDownToEven() {
        assertEquals(10, VideoSizing.even(11))
        assertEquals(2, VideoSizing.even(1))
        assertEquals(8, VideoSizing.even(8))
    }

    @Test
    fun multipleOf14SaveMemoryRoundsDown() {
        assertEquals(378, VideoSizing.multipleOf14(384, roundDown = true))
        assertEquals(518, VideoSizing.multipleOf14(518, roundDown = false))
        assertEquals(756, VideoSizing.multipleOf14(756, roundDown = false))
    }

    @Test
    fun output480pUsesLongEdge854() {
        val (w, h) = VideoSizing.outputSize(1920, 1080, ExportMode.P480, 720)
        assertEquals(854, w)
        assertEquals(480, h)
    }

    @Test
    fun customMaxWidthScalesWidth() {
        val (w, h) = VideoSizing.outputSize(1920, 1080, ExportMode.CUSTOM, 640)
        assertEquals(640, w)
        assertEquals(360, h)
    }

    @Test
    fun durationCap() {
        assertEquals(10_000_000L, VideoSizing.maxDurationUs(60_000_000L, DurationLimit.S10))
        assertEquals(3_000_000L, VideoSizing.maxDurationUs(3_000_000L, DurationLimit.S10))
        assertEquals(20_000_000L, VideoSizing.maxDurationUs(20_000_000L, DurationLimit.ALL))
    }

    @Test
    fun bitrateScalesWithPixels() {
        val small = VideoSizing.bitrate(854, 480, QualityLevel.SMALL)
        val tiny = VideoSizing.bitrate(854, 480, QualityLevel.TINY)
        assertTrue(small > tiny)
        assertEquals(1_000_000, small)
    }

    @Test
    fun inferKeepsAspectAndMultipleOf14() {
        val (w, h) = VideoSizing.inferSize(1920, 1080, 384, forceSquare = false)
        assertEquals(0, w % 14)
        assertEquals(0, h % 14)
        assertTrue(w >= 14 && h >= 14)
        assertTrue(w >= h)
    }
}
