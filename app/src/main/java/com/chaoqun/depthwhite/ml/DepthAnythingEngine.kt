package com.chaoqun.depthwhite.ml

import android.graphics.Bitmap
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.max

class DepthAnythingEngine(
    private val modelPath: String,
    private val preferNnapi: Boolean = true,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    var usingNnapi: Boolean = false
        private set

    private lateinit var inputName: String
    private var staticHeight: Int = -1
    private var staticWidth: Int = -1
    private var isNhwc: Boolean = false
    private var isUint8: Boolean = false
    var forceSquare: Boolean = false
        private set

    private var nchwBuffer: FloatArray? = null
    private var pixelBuffer: IntArray? = null

    fun prepare() {
        session = createSession(preferNnapi)
        inspectGraph()
    }

    fun ensureSession() {
        if (session == null) prepare()
    }

    fun resolveInputSize(requestedW: Int, requestedH: Int): Pair<Int, Int> {
        if (staticWidth > 0 && staticHeight > 0) return staticWidth to staticHeight
        return requestedW to requestedH
    }

    fun infer(bitmap: Bitmap): FloatArray {
        val sess = session ?: throw IllegalStateException("模型未加载")
        val width = bitmap.width
        val height = bitmap.height
        val pixels = reusablePixels(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return try {
            runOnce(sess, pixels, width, height)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (e: OrtException) {
            if (usingNnapi) {
                recreateOnCpu()
                val cpu = session ?: throw e
                runOnce(cpu, pixels, width, height)
            } else {
                throw e
            }
        }
    }

    private fun runOnce(sess: OrtSession, pixels: IntArray, width: Int, height: Int): FloatArray {
        if (isUint8 || isNhwc) {
            throw IllegalStateException("当前模型输入格式不受支持，请使用 Depth Anything V2 ONNX（NCHW float）")
        }
        val plane = width * height
        val nchw = reusableNchw(plane * 3)
        for (i in 0 until plane) {
            val p = pixels[i]
            val r = ((p ushr 16) and 0xFF) / 255f
            val g = ((p ushr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            nchw[i] = (r - 0.485f) / 0.229f
            nchw[plane + i] = (g - 0.456f) / 0.224f
            nchw[plane * 2 + i] = (b - 0.406f) / 0.225f
        }
        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        val buffer = FloatBuffer.wrap(nchw)
        buffer.rewind()
        OnnxTensor.createTensor(env, buffer, shape).use { tensor ->
            sess.run(mapOf(inputName to tensor)).use { result ->
                val value = result[0] as OnnxTensor
                return flattenDepth(value, width, height)
            }
        }
    }

    private fun flattenDepth(tensor: OnnxTensor, fallbackW: Int, fallbackH: Int): FloatArray {
        val fb = tensor.floatBuffer.duplicate()
        fb.rewind()
        val data = FloatArray(fb.remaining())
        fb.get(data)
        if (data.size == fallbackW * fallbackH) return data
        val shape = tensor.info.shape
        val h = if (shape.size >= 2) shape[shape.size - 2].toInt() else fallbackH
        val w = if (shape.size >= 1) shape[shape.size - 1].toInt() else fallbackW
        if (h > 0 && w > 0 && data.size >= h * w) {
            if (data.size == h * w) return data
            if (data.size == h * w) return data
            val out = FloatArray(h * w)
            System.arraycopy(data, 0, out, 0, out.size)
            return out
        }
        return data
    }

    private fun inspectGraph() {
        val sess = session ?: return
        inputName = sess.inputNames.iterator().next()
        val info = sess.inputInfo[inputName]?.info as? TensorInfo ?: return
        isUint8 = info.type == OnnxJavaType.UINT8
        val shape = info.shape
        if (shape.size == 4) {
            isNhwc = shape[3] == 3L
            val h = shape[if (isNhwc) 1 else 2].toInt()
            val w = shape[if (isNhwc) 2 else 3].toInt()
            if (h > 0 && w > 0) {
                staticHeight = h
                staticWidth = w
                forceSquare = h == w
            } else {
                forceSquare = false
            }
        }
    }

    private fun createSession(nnapi: Boolean): OrtSession {
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(2)
        opts.setInterOpNumThreads(1)
        opts.setMemoryPatternOptimization(false)
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        var enabledNnapi = false
        if (nnapi) {
            try {
                opts.addNnapi()
                enabledNnapi = true
            } catch (_: Throwable) {
                enabledNnapi = false
            }
        }
        return try {
            val created = env.createSession(modelPath, opts)
            usingNnapi = enabledNnapi
            created
        } catch (e: Exception) {
            if (enabledNnapi) {
                createSession(nnapi = false)
            } else {
                throw e
            }
        } finally {
            try {
                opts.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun recreateOnCpu() {
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = createSession(nnapi = false)
        inspectGraph()
    }

    private fun reusablePixels(size: Int): IntArray {
        val current = pixelBuffer
        if (current != null && current.size == size) return current
        val next = IntArray(size)
        pixelBuffer = next
        return next
    }

    private fun reusableNchw(size: Int): FloatArray {
        val current = nchwBuffer
        if (current != null && current.size == size) return current
        val next = FloatArray(size)
        nchwBuffer = next
        return next
    }

    override fun close() {
        nchwBuffer = null
        pixelBuffer = null
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
    }

    companion object {
        fun outputDepthSize(depth: FloatArray, inferW: Int, inferH: Int): Pair<Int, Int> {
            if (depth.size == inferW * inferH) return inferW to inferH
            val side = kotlin.math.sqrt(depth.size.toDouble()).toInt()
            if (side * side == depth.size) return side to side
            return inferW to max(1, depth.size / inferW.coerceAtLeast(1))
        }
    }
}
