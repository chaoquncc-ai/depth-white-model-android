package com.chaoqun.depthwhite.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.chaoqun.depthwhite.core.DepthPostProcess
import com.chaoqun.depthwhite.core.RangeEmaState
import com.chaoqun.depthwhite.core.VideoSizing
import com.chaoqun.depthwhite.data.ConvertOptions
import com.chaoqun.depthwhite.data.ConversionProgress
import com.chaoqun.depthwhite.ml.DepthAnythingEngine
import com.chaoqun.depthwhite.ml.ModelStore
import java.io.File

class ConversionPipeline(private val context: Context) {

    data class Result(
        val file: File,
        val mediaStoreUri: String?,
        val frameCount: Int,
        val usedNnapi: Boolean,
        val audioKept: Boolean,
        val message: String,
    )

    fun convert(
        input: Uri,
        options: ConvertOptions,
        onProgress: (ConversionProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): Result {
        val store = ModelStore(context)
        if (!store.isReady(options.modelSize)) {
            onProgress(
                ConversionProgress(
                    phase = ConversionProgress.Phase.DOWNLOAD,
                    message = "正在下载 ${options.modelSize.fileName} …",
                    downloadPercent = 0,
                ),
            )
            store.download(options.modelSize, { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 99) else 0
                onProgress(
                    ConversionProgress(
                        phase = ConversionProgress.Phase.DOWNLOAD,
                        message = "正在下载模型 ${percent}%",
                        downloadPercent = percent,
                    ),
                )
            }, isCancelled)
        }
        val modelFile = store.resolveExisting(options.modelSize)
            ?: throw IllegalStateException("模型文件缺失，请先下载 Depth Anything V2 权重")

        onProgress(
            ConversionProgress(
                phase = ConversionProgress.Phase.CONVERT,
                message = "正在加载模型…",
            ),
        )

        val outputsDir = File(context.filesDir, "outputs").apply { mkdirs() }
        val outFile = File(outputsDir, "depth_${System.currentTimeMillis()}.mp4")

        var engine: DepthAnythingEngine? = null
        var decoder: FrameDecoder? = null
        var encoder: DepthVideoEncoder? = null
        var inferBitmap: Bitmap? = null
        var outBitmap: Bitmap? = null
        var inferPixels: IntArray? = null
        var outPixels: IntArray? = null
        var prevDepth: FloatArray? = null
        var grayInfer: ByteArray? = null
        var grayOut: ByteArray? = null
        var audioKept = false
        var frames = 0
        var engineUsedNnapi = false
        val rangeState = RangeEmaState()

        try {
            engine = DepthAnythingEngine(modelFile.absolutePath, preferNnapi = true).also { it.prepare() }
            engineUsedNnapi = engine.usingNnapi
            decoder = FrameDecoder(context, input)
            val meta = decoder.meta
            val limitUs = VideoSizing.maxDurationUs(meta.durationUs, options.durationLimit)
            val totalGuess = VideoSizing.estimatedFrameCount(limitUs.takeIf { it < Long.MAX_VALUE } ?: meta.durationUs, meta.fps)
            val (outW, outH) = options.outputSize(meta.displayWidth, meta.displayHeight)
            val requested = options.inferSize(meta.displayWidth, meta.displayHeight, engine.forceSquare)
            val (inferW, inferH) = engine.resolveInputSize(requested.first, requested.second)

            inferBitmap = Bitmap.createBitmap(inferW, inferH, Bitmap.Config.ARGB_8888)
            outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            inferPixels = IntArray(inferW * inferH)
            outPixels = IntArray(outW * outH)

            val (contentL, contentT, contentW, contentH) = letterbox(
                inferW,
                inferH,
                meta.displayWidth,
                meta.displayHeight,
            )

            val audioFormat = if (options.keepAudio && meta.hasAudio) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(context, input, null)
                    ex.getTrackFormat(meta.audioTrack)
                } catch (_: Exception) {
                    null
                } finally {
                    ex.release()
                }
            } else {
                null
            }

            encoder = DepthVideoEncoder(
                width = outW,
                height = outH,
                bitrate = VideoSizing.bitrate(outW, outH, options.quality),
                fps = meta.fps,
                outputFile = outFile,
                audioFormat = audioFormat,
            )

            onProgress(
                ConversionProgress(
                    phase = ConversionProgress.Phase.CONVERT,
                    frame = 0,
                    total = totalGuess,
                    message = "开始逐帧推理（${inferW}×${inferH} → ${outW}×${outH}）",
                ),
            )

            try {
                var processed = 0
                frames = decoder.forEachFrame(limitUs, isCancelled) { image, ptsUs ->
                    val infer = inferBitmap ?: return@forEachFrame
                    val pixels = inferPixels ?: return@forEachFrame
                    YuvBitmapConverter.sampleToBitmap(
                        image = image,
                        rotation = meta.rotation,
                        dst = infer,
                        pixels = pixels,
                        contentLeft = contentL,
                        contentTop = contentT,
                        contentWidth = contentW,
                        contentHeight = contentH,
                    )
                    val depth = engine?.infer(infer) ?: return@forEachFrame
                    if (options.emaEnabled) {
                        DepthPostProcess.applyTemporalEma(depth, prevDepth, options.emaAlpha)
                        if (prevDepth == null || prevDepth?.size != depth.size) {
                            prevDepth = FloatArray(depth.size)
                        }
                        DepthPostProcess.copyInto(depth, prevDepth)
                    }
                    val (dw, dh) = DepthAnythingEngine.outputDepthSize(depth, inferW, inferH)
                    val inferGray = (grayInfer?.takeIf { it.size == depth.size } ?: ByteArray(depth.size)).also {
                        grayInfer = it
                    }
                    DepthPostProcess.normalizeToGray(
                        depth = depth,
                        invert = options.invertDepth,
                        rangeState = rangeState,
                        useRangeEma = options.emaEnabled,
                        outGray = inferGray,
                    )
                    val outGray = (grayOut?.takeIf { it.size == outW * outH } ?: ByteArray(outW * outH)).also {
                        grayOut = it
                    }
                    YuvBitmapConverter.scaleGray(inferGray, dw, dh, outW, outH, outGray)
                    val outBmp = outBitmap ?: return@forEachFrame
                    val outPix = outPixels ?: return@forEachFrame
                    YuvBitmapConverter.grayToArgbBitmap(outGray, outW, outH, outBmp, outPix)
                    encoder?.encodeGrayBitmap(outBmp, ptsUs)
                    processed++
                    if (processed == 1 || processed % 2 == 0 || processed == totalGuess) {
                        onProgress(
                            ConversionProgress(
                                phase = ConversionProgress.Phase.CONVERT,
                                frame = processed,
                                total = totalGuess,
                                message = "第 $processed / $totalGuess 帧",
                            ),
                        )
                    }
                }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (first: Exception) {
                if (frames == 0) {
                    frames = processWithRetriever(
                        input = input,
                        engine = engine!!,
                        encoder = encoder!!,
                        options = options,
                        inferBitmap = inferBitmap!!,
                        outBitmap = outBitmap!!,
                        inferPixels = inferPixels!!,
                        outPixels = outPixels!!,
                        inferW = inferW,
                        inferH = inferH,
                        outW = outW,
                        outH = outH,
                        contentL = contentL,
                        contentT = contentT,
                        contentW = contentW,
                        contentH = contentH,
                        fps = meta.fps,
                        limitUs = limitUs,
                        totalGuess = totalGuess,
                        rangeState = rangeState,
                        onProgress = onProgress,
                        isCancelled = isCancelled,
                    )
                } else {
                    throw first
                }
            }

            if (frames == 0) {
                throw IllegalStateException("没有可解码的帧。请换 MP4/MOV，或确认视频未损坏。")
            }

            onProgress(
                ConversionProgress(
                    phase = ConversionProgress.Phase.ENCODE,
                    frame = frames,
                    total = frames,
                    message = "正在封装视频…",
                ),
            )
            encoder.finish()
            if (audioFormat != null && encoder.audioTrackIndex >= 0) {
                try {
                    AudioCopier.copyThrough(
                        context = context,
                        source = input,
                        audioTrackInSource = meta.audioTrack,
                        muxer = encoder.mediaMuxer,
                        muxerAudioTrack = encoder.audioTrackIndex,
                        maxDurationUs = limitUs,
                        isCancelled = isCancelled,
                    )
                    audioKept = true
                } catch (_: Exception) {
                    audioKept = false
                }
            }
        } catch (t: Throwable) {
            try {
                encoder?.close()
            } catch (_: Throwable) {
            }
            encoder = null
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            try {
                encoder?.close()
            } catch (_: Throwable) {
            }
            try {
                decoder?.close()
            } catch (_: Throwable) {
            }
            try {
                engine?.close()
            } catch (_: Throwable) {
            }
            inferBitmap?.recycle()
            outBitmap?.recycle()
            inferPixels = null
            outPixels = null
            prevDepth = null
            grayInfer = null
            grayOut = null
            System.gc()
        }

        val published = MediaStorePublisher.publishMp4(context, outFile, outFile.name)
        val nnapi = engineUsedNnapi
        val msg = buildString {
            append("完成，共 $frames 帧。")
            if (!audioKept && options.keepAudio) append(" 未能保留原音频（片源可能无音轨）。")
            if (nnapi) append(" （NNAPI）")
        }
        return Result(
            file = outFile,
            mediaStoreUri = published?.toString(),
            frameCount = frames,
            usedNnapi = nnapi,
            audioKept = audioKept,
            message = msg,
        )
    }

    private fun processWithRetriever(
        input: Uri,
        engine: DepthAnythingEngine,
        encoder: DepthVideoEncoder,
        options: ConvertOptions,
        inferBitmap: Bitmap,
        outBitmap: Bitmap,
        inferPixels: IntArray,
        outPixels: IntArray,
        inferW: Int,
        inferH: Int,
        outW: Int,
        outH: Int,
        contentL: Int,
        contentT: Int,
        contentW: Int,
        contentH: Int,
        fps: Float,
        limitUs: Long,
        totalGuess: Int,
        rangeState: RangeEmaState,
        onProgress: (ConversionProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): Int {
        val retriever = MediaMetadataRetriever()
        var prevDepth: FloatArray? = null
        var grayInfer: ByteArray? = null
        var grayOut: ByteArray? = null
        var frames = 0
        try {
            retriever.setDataSource(context, input)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.times(1000) ?: limitUs
            val end = if (limitUs < Long.MAX_VALUE) minOf(limitUs, duration) else duration
            val step = (1_000_000.0 / fps).toLong().coerceAtLeast(1L)
            var t = 0L
            while (t <= end) {
                if (isCancelled()) throw InterruptedException("cancelled")
                val scaled = if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(
                        t,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        inferW,
                        inferH,
                    )
                } else {
                    retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                if (scaled != null) {
                    val src = if (scaled.width == inferW && scaled.height == inferH) {
                        scaled
                    } else {
                        val tmp = Bitmap.createScaledBitmap(scaled, inferW, inferH, true)
                        if (tmp != scaled) scaled.recycle()
                        tmp
                    }
                    try {
                        val canvasBmp = inferBitmap
                        val pixels = inferPixels
                        src.getPixels(pixels, 0, inferW, 0, 0, inferW, inferH)
                        if (contentW != inferW || contentH != inferH) {
                            pixels.fill((0xFF shl 24) or (123 shl 16) or (116 shl 8) or 103)
                            // Keep simple path: draw scaled already fills the bitmap.
                            canvasBmp.setPixels(pixels, 0, inferW, 0, 0, inferW, inferH)
                        } else {
                            canvasBmp.setPixels(pixels, 0, inferW, 0, 0, inferW, inferH)
                        }
                        val depth = engine.infer(canvasBmp)
                        if (options.emaEnabled) {
                            DepthPostProcess.applyTemporalEma(depth, prevDepth, options.emaAlpha)
                            if (prevDepth == null || prevDepth?.size != depth.size) {
                                prevDepth = FloatArray(depth.size)
                            }
                            DepthPostProcess.copyInto(depth, prevDepth)
                        }
                        val (dw, dh) = DepthAnythingEngine.outputDepthSize(depth, inferW, inferH)
                        val inferGray = (grayInfer?.takeIf { it.size == depth.size } ?: ByteArray(depth.size)).also {
                            grayInfer = it
                        }
                        DepthPostProcess.normalizeToGray(
                            depth,
                            options.invertDepth,
                            rangeState,
                            options.emaEnabled,
                            inferGray,
                        )
                        val outGray = (grayOut?.takeIf { it.size == outW * outH } ?: ByteArray(outW * outH)).also {
                            grayOut = it
                        }
                        YuvBitmapConverter.scaleGray(inferGray, dw, dh, outW, outH, outGray)
                        YuvBitmapConverter.grayToArgbBitmap(outGray, outW, outH, outBitmap, outPixels)
                        encoder.encodeGrayBitmap(outBitmap, t)
                        frames++
                        onProgress(
                            ConversionProgress(
                                phase = ConversionProgress.Phase.CONVERT,
                                frame = frames,
                                total = totalGuess,
                                message = "第 $frames / $totalGuess 帧",
                            ),
                        )
                    } finally {
                        if (src != inferBitmap) src.recycle()
                    }
                }
                t += step
            }
        } finally {
            retriever.release()
        }
        return frames
    }

    private fun letterbox(
        inferW: Int,
        inferH: Int,
        displayW: Int,
        displayH: Int,
    ): IntArray {
        if (inferW == displayW && inferH == displayH) {
            return intArrayOf(0, 0, inferW, inferH)
        }
        val scale = minOf(inferW.toFloat() / displayW, inferH.toFloat() / displayH)
        val cw = (displayW * scale).toInt().coerceAtLeast(1).coerceAtMost(inferW)
        val ch = (displayH * scale).toInt().coerceAtLeast(1).coerceAtMost(inferH)
        val left = (inferW - cw) / 2
        val top = (inferH - ch) / 2
        return intArrayOf(left, top, cw, ch)
    }
}
