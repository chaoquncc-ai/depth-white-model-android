package com.chaoqun.depthwhite.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object VideoClipper {
    fun clipFile(
        input: File,
        output: File,
        startUs: Long,
        durationUs: Long,
        isCancelled: () -> Boolean = { false },
    ) {
        if (output.exists()) output.delete()
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(input.absolutePath)
            val trackMap = IntArray(extractor.trackCount) { -1 }
            var added = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                    added++
                }
            }
            if (added == 0) throw IllegalStateException("片段导出失败：没有音视频轨道")
            muxer.start()
            val endUs = startUs + durationUs
            extractor.seekTo(startUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (isCancelled()) throw InterruptedException("cancelled")
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time > endUs) {
                    if (!extractor.advance()) break
                    continue
                }
                val srcTrack = extractor.sampleTrackIndex
                val dstTrack = if (srcTrack in trackMap.indices) trackMap[srcTrack] else -1
                if (dstTrack >= 0 && time >= startUs - 1_000_000L) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = (time - startUs).coerceAtLeast(0L)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, info)
                }
                if (!extractor.advance()) break
            }
        } finally {
            try {
                muxer.stop()
            } catch (_: Throwable) {
            }
            muxer.release()
            extractor.release()
        }
        if (!output.exists() || output.length() < 64) {
            throw IllegalStateException("片段导出失败，请尝试把起始秒数设为 0")
        }
    }
}
