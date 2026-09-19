package com.chaoqun.depthwhite.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.max

class DepthVideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    fps: Float,
    outputFile: File,
    private val audioFormat: MediaFormat? = null,
) : AutoCloseable {

    private val frameRate = max(1, fps.toInt().coerceIn(1, 60))
    private val encoder: MediaCodec
    private val muxer: MediaMuxer
    private val colorFormat: Int
    private val nv21: Boolean
    private val bufferInfo = MediaCodec.BufferInfo()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var yuv: ByteArray = ByteArray(width * height * 3 / 2)
    private var pixels: IntArray = IntArray(width * height)

    init {
        require(width % 2 == 0 && height % 2 == 0) { "编码器宽高必须为偶数" }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val caps = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        colorFormat = pickColorFormat(caps.colorFormats)
        nv21 = isNv21(codec.codecInfo.name, colorFormat)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        format.setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
        )
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        encoder = codec
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    val audioTrackIndex: Int get() = audioTrack
    val isMuxerStarted: Boolean get() = muxerStarted
    val mediaMuxer: MediaMuxer get() = muxer

    fun encodeGrayBitmap(bitmap: Bitmap, ptsUs: Long) {
        require(bitmap.width == width && bitmap.height == height)
        fillYuv(bitmap)
        var submitted = false
        while (!submitted) {
            drain(endOfStream = false)
            val inIndex = encoder.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val input = encoder.getInputBuffer(inIndex) ?: continue
                input.clear()
                val limit = yuv.size.coerceAtMost(input.remaining())
                input.put(yuv, 0, limit)
                encoder.queueInputBuffer(inIndex, 0, limit, ptsUs, 0)
                submitted = true
            }
        }
        drain(endOfStream = false)
    }

    fun finish() {
        var eosQueued = false
        while (true) {
            if (!eosQueued) {
                val inIndex = encoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    encoder.queueInputBuffer(
                        inIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    eosQueued = true
                }
            }
            if (!drain(endOfStream = true)) break
        }
    }

    private fun drain(endOfStream: Boolean): Boolean {
        while (true) {
            when (val outIndex = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return !endOfStream
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> startMuxerIfNeeded()
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outIndex >= 0) {
                    startMuxerIfNeeded()
                    val encoded = encoder.getOutputBuffer(outIndex)
                    if (encoded != null && bufferInfo.size > 0 && videoTrack >= 0 && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return false
                    }
                }
            }
        }
    }

    private fun startMuxerIfNeeded() {
        if (muxerStarted) return
        if (videoTrack < 0) {
            videoTrack = muxer.addTrack(encoder.outputFormat)
            val audio = audioFormat
            if (audio != null) {
                audioTrack = muxer.addTrack(audio)
            }
        }
        muxer.start()
        muxerStarted = true
    }

    private fun fillYuv(bitmap: Bitmap) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val ySize = width * height
        for (i in 0 until ySize) {
            yuv[i] = (pixels[i] and 0xFF).toByte()
        }
        val chromaH = height / 2
        val chromaW = width / 2
        when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> {
                val uStart = ySize
                val vStart = ySize + chromaW * chromaH
                var ui = uStart
                var vi = vStart
                repeat(chromaH * chromaW) {
                    yuv[ui++] = 128.toByte()
                    yuv[vi++] = 128.toByte()
                }
            }
            else -> {
                var uv = ySize
                for (row in 0 until chromaH) {
                    for (col in 0 until chromaW) {
                        if (nv21) {
                            yuv[uv++] = 128.toByte()
                            yuv[uv++] = 128.toByte()
                        } else {
                            yuv[uv++] = 128.toByte()
                            yuv[uv++] = 128.toByte()
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        try {
            encoder.stop()
        } catch (_: Throwable) {
        }
        try {
            encoder.release()
        } catch (_: Throwable) {
        }
        try {
            if (muxerStarted) muxer.stop()
        } catch (_: Throwable) {
        }
        try {
            muxer.release()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private fun pickColorFormat(formats: IntArray): Int {
            val preferred = intArrayOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            )
            for (p in preferred) {
                if (formats.contains(p)) return p
            }
            if (formats.isNotEmpty()) return formats[0]
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }

        private fun isNv21(codecName: String, colorFormat: Int): Boolean {
            if (colorFormat == 0x7FA30C00 || colorFormat == 0x7FA30C04) return true
            return codecName.contains("qcom", true) || codecName.contains("qti", true)
        }
    }
}
