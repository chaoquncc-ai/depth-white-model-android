package com.chaoqun.depthwhite.video

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.max

object YuvBitmapConverter {
    private const val PAD_COLOR = (0xFF shl 24) or (123 shl 16) or (116 shl 8) or 103

    fun sampleToBitmap(
        image: Image,
        rotation: Int,
        dst: Bitmap,
        pixels: IntArray,
        contentLeft: Int = 0,
        contentTop: Int = 0,
        contentWidth: Int = dst.width,
        contentHeight: Int = dst.height,
    ) {
        val srcW = image.width
        val srcH = image.height
        val dstW = dst.width
        val dstH = dst.height
        require(pixels.size >= dstW * dstH)

        if (contentWidth != dstW || contentHeight != dstH || contentLeft != 0 || contentTop != 0) {
            pixels.fill(PAD_COLOR)
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer.duplicate()
        val uBuf = uPlane.buffer.duplicate()
        val vBuf = vPlane.buffer.duplicate()
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        val uRow = uPlane.rowStride
        val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride
        val vPix = vPlane.pixelStride
        val rot = ((rotation % 360) + 360) % 360

        val xEnd = (contentLeft + contentWidth).coerceAtMost(dstW)
        val yEnd = (contentTop + contentHeight).coerceAtMost(dstH)
        val cw = max(1, contentWidth)
        val ch = max(1, contentHeight)

        for (dy in contentTop until yEnd) {
            val ly = dy - contentTop
            for (dx in contentLeft until xEnd) {
                val lx = dx - contentLeft
                val (sxRaw, syRaw) = when (rot) {
                    90 -> (ly.toFloat() / ch * (srcW - 1)).toInt() to
                        ((cw - 1 - lx).toFloat() / cw * (srcH - 1)).toInt()
                    180 -> ((cw - 1 - lx).toFloat() / cw * (srcW - 1)).toInt() to
                        ((ch - 1 - ly).toFloat() / ch * (srcH - 1)).toInt()
                    270 -> ((ch - 1 - ly).toFloat() / ch * (srcW - 1)).toInt() to
                        (lx.toFloat() / cw * (srcH - 1)).toInt()
                    else -> (lx.toFloat() / cw * (srcW - 1)).toInt() to
                        (ly.toFloat() / ch * (srcH - 1)).toInt()
                }
                val sx = sxRaw.coerceIn(0, srcW - 1)
                val sy = syRaw.coerceIn(0, srcH - 1)
                val y = yBuf.getAbs(sy * yRow + sx * yPix).toInt() and 0xFF
                val uvx = sx / 2
                val uvy = sy / 2
                val u = uBuf.getAbs(uvy * uRow + uvx * uPix).toInt() and 0xFF
                val v = vBuf.getAbs(uvy * vRow + uvx * vPix).toInt() and 0xFF
                pixels[dy * dstW + dx] = yuvToArgb(y, u, v)
            }
        }
        dst.setPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
    }

    fun grayToArgbBitmap(gray: ByteArray, width: Int, height: Int, dst: Bitmap, pixels: IntArray) {
        val count = width * height
        require(gray.size >= count)
        require(pixels.size >= count)
        require(dst.width == width && dst.height == height)
        for (i in 0 until count) {
            val g = gray[i].toInt() and 0xFF
            pixels[i] = Color.argb(255, g, g, g)
        }
        dst.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun scaleGray(
        src: ByteArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        dst: ByteArray,
    ) {
        require(dst.size >= dstW * dstH)
        if (srcW == dstW && srcH == dstH) {
            System.arraycopy(src, 0, dst, 0, dstW * dstH)
            return
        }
        for (y in 0 until dstH) {
            val sy = (y.toFloat() / dstH * (srcH - 1)).toInt().coerceIn(0, srcH - 1)
            for (x in 0 until dstW) {
                val sx = (x.toFloat() / dstW * (srcW - 1)).toInt().coerceIn(0, srcW - 1)
                dst[y * dstW + x] = src[sy * srcW + sx]
            }
        }
    }

    private fun ByteBuffer.getAbs(index: Int): Byte {
        val i = index.coerceIn(0, capacity() - 1)
        return get(i)
    }

    private fun yuvToArgb(yIn: Int, uIn: Int, vIn: Int): Int {
        val y = yIn
        val u = uIn - 128
        val v = vIn - 128
        val r = (y + (1.402f * v)).toInt().coerceIn(0, 255)
        val g = (y - (0.344136f * u) - (0.714136f * v)).toInt().coerceIn(0, 255)
        val b = (y + (1.772f * u)).toInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }
}
