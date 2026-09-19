package com.chaoqun.depthwhite.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthPostProcessTest {
    @Test
    fun invertFlipsNearFar() {
        val depth = floatArrayOf(0f, 50f, 100f)
        val gray = ByteArray(3)
        val inverted = ByteArray(3)
        DepthPostProcess.normalizeToGray(depth, invert = false, rangeState = null, useRangeEma = false, outGray = gray)
        DepthPostProcess.normalizeToGray(depth, invert = true, rangeState = null, useRangeEma = false, outGray = inverted)
        assertEquals(255 - (gray[0].toInt() and 0xFF), inverted[0].toInt() and 0xFF)
        assertEquals(255 - (gray[2].toInt() and 0xFF), inverted[2].toInt() and 0xFF)
    }

    @Test
    fun emaBlendsTowardPrevious() {
        val current = floatArrayOf(10f, 10f)
        val previous = floatArrayOf(0f, 0f)
        DepthPostProcess.applyTemporalEma(current, previous, alpha = 0.5f)
        assertEquals(5f, current[0], 0.01f)
    }

    @Test
    fun percentileEnds() {
        val data = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        assertEquals(1f, DepthPostProcess.percentile(data, 0.0), 0.01f)
        assertEquals(5f, DepthPostProcess.percentile(data, 100.0), 0.01f)
        assertTrue(DepthPostProcess.percentile(data, 50.0) in 2f..4f)
    }
}
