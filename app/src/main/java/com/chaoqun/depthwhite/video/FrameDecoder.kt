package com.chaoqun.depthwhite.video

import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.content.Context
import java.io.Closeable
import kotlin.math.max

data class VideoMeta(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationUs: Long,
    val fps: Float,
    val hasAudio: Boolean,
    val videoTrack: Int,
    val audioTrack: Int,
) {
    val displayWidth: Int get() = if (rotation % 180 != 0) height else width
    val displayHeight: Int get() = if (rotation % 180 != 0) width else height
}

class FrameDecoder(
    context: Context,
    uri: Uri,
) : Closeable {

    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    val meta: VideoMeta

    init {
        extractor.setDataSource(context, uri, null)
        meta = readMeta(extractor)
        val videoFormat = extractor.getTrackFormat(meta.videoTrack)
        extractor.selectTrack(meta.videoTrack)
        val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        val codec = MediaCodec.createDecoderByType(mime)
        videoFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
        )
        codec.configure(videoFormat, null, null, 0)
        codec.start()
        decoder = codec
    }

    fun forEachFrame(
        maxDurationUs: Long,
        isCancelled: () -> Boolean,
        onFrame: (image: Image, ptsUs: Long) -> Unit,
    ): Int {
        val codec = decoder ?: throw IllegalStateException("解码器未就绪")
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var frames = 0
        val timeoutUs = 10_000L

        while (!outputDone) {
            if (isCancelled()) throw InterruptedException("cancelled")
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer == null) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, 0)
                    } else {
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val pts = extractor.sampleTime
                        val overLimit = maxDurationUs < Long.MAX_VALUE && pts >= 0 && pts > maxDurationUs
                        if (sampleSize < 0 || overLimit) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, max(0L, pts), 0)
                            extractor.advance()
                        }
                    }
                }
            }

            when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)) {
                MediaCodec.INFO_TRY_AGAIN_LATER,
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                -> Unit
                else -> if (outIndex >= 0) {
                    val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val usable = bufferInfo.size > 0 &&
                        (maxDurationUs == Long.MAX_VALUE || bufferInfo.presentationTimeUs <= maxDurationUs)
                    if (usable) {
                        val image = codec.getOutputImage(outIndex)
                        if (image != null && image.format == ImageFormat.YUV_420_888) {
                            try {
                                onFrame(image, bufferInfo.presentationTimeUs)
                                frames++
                            } finally {
                                image.close()
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (eos) outputDone = true
                }
            }
        }
        return frames
    }

    override fun close() {
        try {
            decoder?.stop()
        } catch (_: Throwable) {
        }
        try {
            decoder?.release()
        } catch (_: Throwable) {
        }
        decoder = null
        try {
            extractor.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun readMeta(extractor: MediaExtractor): VideoMeta {
            var videoTrack = -1
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (videoTrack < 0 && mime.startsWith("video/")) videoTrack = i
                if (audioTrack < 0 && mime.startsWith("audio/")) audioTrack = i
            }
            if (videoTrack < 0) throw IllegalStateException("视频中没有可解码的画面轨道")
            val format = extractor.getTrackFormat(videoTrack)
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                format.getInteger(MediaFormat.KEY_ROTATION)
            } else {
                0
            }
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val fps = when {
                format.containsKey(MediaFormat.KEY_FRAME_RATE) -> {
                    try {
                        format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    } catch (_: Exception) {
                        format.getFloat(MediaFormat.KEY_FRAME_RATE)
                    }
                }
                else -> 30f
            }.let { if (it.isFinite() && it > 1f && it <= 120f) it else 30f }
            return VideoMeta(
                width = width,
                height = height,
                rotation = rotation,
                durationUs = durationUs,
                fps = fps,
                hasAudio = audioTrack >= 0,
                videoTrack = videoTrack,
                audioTrack = audioTrack,
            )
        }
    }
}
