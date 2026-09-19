package com.chaoqun.depthwhite.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object VideoSizing {
    fun even(value: Int): Int = max(2, value - value % 2)

    fun multipleOf14(value: Int, roundDown: Boolean): Int {
        val v = max(14, value)
        return if (roundDown) {
            max(14, (v / 14) * 14)
        } else {
            max(14, ((v + 13) / 14) * 14)
        }
    }

    fun displaySize(width: Int, height: Int, rotation: Int): Pair<Int, Int> {
        return if (rotation % 180 != 0) height to width else width to height
    }

    fun inferSize(
        displayWidth: Int,
        displayHeight: Int,
        requested: Int,
        forceSquare: Boolean,
    ): Pair<Int, Int> {
        val target = multipleOf14(requested, roundDown = requested <= 384)
        if (forceSquare) return target to target
        val longEdge = max(displayWidth, displayHeight).coerceAtLeast(1)
        val scale = target.toFloat() / longEdge
        val w = multipleOf14((displayWidth * scale).roundToInt(), roundDown = false)
        val h = multipleOf14((displayHeight * scale).roundToInt(), roundDown = false)
        return w to h
    }

    fun outputSize(
        displayWidth: Int,
        displayHeight: Int,
        mode: ExportMode,
        customMaxWidth: Int,
    ): Pair<Int, Int> {
        val w = displayWidth.coerceAtLeast(2)
        val h = displayHeight.coerceAtLeast(2)
        return when (mode) {
            ExportMode.P360 -> scaleToLongEdge(w, h, 640)
            ExportMode.P480 -> scaleToLongEdge(w, h, 854)
            ExportMode.P640 -> scaleToMaxWidth(w, h, 640)
            ExportMode.ORIGINAL -> even(w) to even(h)
            ExportMode.CUSTOM -> scaleToMaxWidth(w, h, customMaxWidth.coerceIn(160, 1920))
        }
    }

    fun scaleToLongEdge(width: Int, height: Int, longEdge: Int): Pair<Int, Int> {
        val current = max(width, height)
        if (current <= longEdge) return even(width) to even(height)
        val scale = longEdge.toFloat() / current
        return even((width * scale).roundToInt()) to even((height * scale).roundToInt())
    }

    fun scaleToMaxWidth(width: Int, height: Int, maxWidth: Int): Pair<Int, Int> {
        if (width <= maxWidth) return even(width) to even(height)
        val scale = maxWidth.toFloat() / width
        return even(maxWidth) to even((height * scale).roundToInt())
    }

    fun bitrate(outWidth: Int, outHeight: Int, quality: QualityLevel): Int {
        val pixels = outWidth.toLong() * outHeight
        val ref = 854L * 480L
        val base = when (quality) {
            QualityLevel.TINY -> 400_000L
            QualityLevel.SMALL -> 1_000_000L
            QualityLevel.MEDIUM -> 2_000_000L
        }
        return (base * pixels / ref).toInt().coerceIn(150_000, 8_000_000)
    }

    fun maxDurationUs(totalUs: Long, duration: DurationLimit): Long {
        val cap = when (duration) {
            DurationLimit.S5 -> 5_000_000L
            DurationLimit.S8 -> 8_000_000L
            DurationLimit.S10 -> 10_000_000L
            DurationLimit.ALL -> Long.MAX_VALUE
        }
        return min(totalUs.coerceAtLeast(0L), cap)
    }

    fun estimatedFrameCount(durationUs: Long, fps: Float): Int {
        val safeFps = if (fps.isFinite() && fps > 1f && fps <= 120f) fps else 30f
        return max(1, ((durationUs / 1_000_000.0) * safeFps).roundToInt())
    }
}

enum class ExportMode { P360, P480, P640, ORIGINAL, CUSTOM }

enum class QualityLevel { TINY, SMALL, MEDIUM }

enum class DurationLimit { S5, S8, S10, ALL }

enum class ClipLength { S5, S8, S10 }

enum class InferPreset(val size: Int) {
    SAVE(384),
    BALANCED(518),
    HIGH(756),
}

enum class ModelSize {
    SMALL,
    BASE,
    LARGE,
    ;

    val fileName: String
        get() = when (this) {
            SMALL -> "depth_anything_v2_vits.onnx"
            BASE -> "depth_anything_v2_vitb.onnx"
            LARGE -> "depth_anything_v2_vitl.onnx"
        }

    val approxBytes: Long
        get() = when (this) {
            SMALL -> 101L * 1024 * 1024
            BASE -> 391L * 1024 * 1024
            LARGE -> 1_340L * 1024 * 1024
        }

    val downloadUrls: List<String>
        get() {
            val file = fileName
            return listOf(
                "https://huggingface.co/yuvraj108c/Depth-Anything-2-Onnx/resolve/main/$file",
                "https://hf-mirror.com/yuvraj108c/Depth-Anything-2-Onnx/resolve/main/$file",
            )
        }
}
