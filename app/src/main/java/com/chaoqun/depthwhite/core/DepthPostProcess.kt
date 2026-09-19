package com.chaoqun.depthwhite.core

class RangeEmaState {
    var lo: Float? = null
    var hi: Float? = null
}

object DepthPostProcess {
    const val DEFAULT_EMA_ALPHA = 0.45f
    const val RANGE_EMA = 0.90f

    fun applyTemporalEma(current: FloatArray, previous: FloatArray?, alpha: Float) {
        if (previous == null || previous.size != current.size) return
        val a = alpha.coerceIn(0.05f, 0.95f)
        val inv = 1f - a
        for (i in current.indices) {
            current[i] = a * previous[i] + inv * current[i]
        }
    }

    fun normalizeToGray(
        depth: FloatArray,
        invert: Boolean,
        rangeState: RangeEmaState?,
        useRangeEma: Boolean,
        outGray: ByteArray,
    ) {
        require(outGray.size >= depth.size)
        val loRaw = percentile(depth, 1.0)
        val hiRaw = percentile(depth, 99.0)
        var lo = loRaw
        var hi = hiRaw
        if (useRangeEma && rangeState != null) {
            val prevLo = rangeState.lo
            val prevHi = rangeState.hi
            if (prevLo != null && prevHi != null) {
                lo = RANGE_EMA * prevLo + (1f - RANGE_EMA) * lo
                hi = RANGE_EMA * prevHi + (1f - RANGE_EMA) * hi
            }
            rangeState.lo = lo
            rangeState.hi = hi
        }
        val span = (hi - lo).coerceAtLeast(1e-6f)
        for (i in depth.indices) {
            var norm = ((depth[i] - lo) / span).coerceIn(0f, 1f)
            if (invert) norm = 1f - norm
            outGray[i] = (norm * 255f).toInt().coerceIn(0, 255).toByte()
        }
    }

    fun percentile(data: FloatArray, percent: Double): Float {
        if (data.isEmpty()) return 0f
        val copy = data.copyOf()
        copy.sort()
        val idx = ((copy.size - 1) * (percent / 100.0)).toInt().coerceIn(0, copy.lastIndex)
        return copy[idx]
    }

    fun copyInto(source: FloatArray, dest: FloatArray?) {
        if (dest != null && dest.size == source.size) {
            System.arraycopy(source, 0, dest, 0, source.size)
        }
    }
}
