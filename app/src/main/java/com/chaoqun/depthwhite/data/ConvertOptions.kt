package com.chaoqun.depthwhite.data

import com.chaoqun.depthwhite.core.ClipLength
import com.chaoqun.depthwhite.core.DurationLimit
import com.chaoqun.depthwhite.core.ExportMode
import com.chaoqun.depthwhite.core.InferPreset
import com.chaoqun.depthwhite.core.ModelSize
import com.chaoqun.depthwhite.core.QualityLevel
import com.chaoqun.depthwhite.core.VideoSizing

data class ConvertOptions(
    val modelSize: ModelSize = ModelSize.SMALL,
    val inferPreset: InferPreset = InferPreset.SAVE,
    val exportMode: ExportMode = ExportMode.P480,
    val customMaxWidth: Int = 720,
    val quality: QualityLevel = QualityLevel.SMALL,
    val invertDepth: Boolean = false,
    val emaEnabled: Boolean = true,
    val emaAlpha: Float = 0.45f,
    val keepAudio: Boolean = true,
    val durationLimit: DurationLimit = DurationLimit.S10,
) {
    fun inferSize(displayWidth: Int, displayHeight: Int, forceSquare: Boolean): Pair<Int, Int> {
        return VideoSizing.inferSize(displayWidth, displayHeight, inferPreset.size, forceSquare)
    }

    fun outputSize(displayWidth: Int, displayHeight: Int): Pair<Int, Int> {
        return VideoSizing.outputSize(displayWidth, displayHeight, exportMode, customMaxWidth)
    }
}

data class ClipExportOptions(
    val startSeconds: Int = 0,
    val length: ClipLength = ClipLength.S5,
)

data class ConversionProgress(
    val phase: Phase = Phase.IDLE,
    val frame: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val downloadPercent: Int = -1,
) {
    enum class Phase { IDLE, DOWNLOAD, CONVERT, ENCODE, CLIP, DONE, ERROR }
}

object WorkKeys {
    const val UNIQUE_WORK = "depth_white_convert"
    const val UNIQUE_CLIP = "depth_white_clip"
    const val INPUT_URI = "input_uri"
    const val MODEL = "model"
    const val INFER = "infer"
    const val EXPORT = "export"
    const val CUSTOM_WIDTH = "custom_width"
    const val QUALITY = "quality"
    const val INVERT = "invert"
    const val EMA = "ema"
    const val EMA_ALPHA = "ema_alpha"
    const val KEEP_AUDIO = "keep_audio"
    const val DURATION = "duration"
    const val PHASE = "phase"
    const val FRAME = "frame"
    const val TOTAL = "total"
    const val MESSAGE = "message"
    const val DOWNLOAD = "download"
    const val OUTPUT_PATH = "output_path"
    const val OUTPUT_URI = "output_uri"
    const val ERROR = "error"
    const val CLIP_INPUT = "clip_input"
    const val CLIP_START_MS = "clip_start_ms"
    const val CLIP_DURATION_MS = "clip_duration_ms"
}
