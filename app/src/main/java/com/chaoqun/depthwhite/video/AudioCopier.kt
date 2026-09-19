package com.chaoqun.depthwhite.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

object AudioCopier {
    fun copyThrough(
        context: Context,
        source: Uri,
        audioTrackInSource: Int,
        muxer: MediaMuxer,
        muxerAudioTrack: Int,
        maxDurationUs: Long,
        isCancelled: () -> Boolean,
    ) {
        if (muxerAudioTrack < 0 || audioTrackInSource < 0) return
        val extractor = MediaExtractor()
        val buffer = ByteBuffer.allocate(256 * 1024)
        val info = MediaCodec.BufferInfo()
        try {
            extractor.setDataSource(context, source, null)
            extractor.selectTrack(audioTrackInSource)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                if (isCancelled()) throw InterruptedException("cancelled")
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (maxDurationUs < Long.MAX_VALUE && time > maxDurationUs) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = time.coerceAtLeast(0L)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            extractor.release()
        }
    }
}
