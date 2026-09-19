package com.chaoqun.depthwhite.video

object Errors {
    fun chinese(throwable: Throwable): String {
        if (throwable is InterruptedException || throwable.message == "cancelled") {
            return "已取消"
        }
        val chain = generateSequence(throwable) { it.cause }
        val oom = chain.any { it is OutOfMemoryError } ||
            chain.any {
                val msg = it.message.orEmpty()
                msg.contains("OutOfMemory", ignoreCase = true) ||
                    msg.contains("OOM", ignoreCase = true) ||
                    msg.contains("Failed to allocate", ignoreCase = true)
            }
        if (oom) {
            return "内存不足（OOM）。请改用 Small 模型、推理分辨率「省显存 384」、导出 360p/480p，并缩短输出时长后重试。"
        }
        val joined = chain.joinToString(" ") { it.javaClass.simpleName + " " + it.message.orEmpty() }
        if (
            joined.contains("ForegroundService", ignoreCase = true) ||
            joined.contains("setForeground", ignoreCase = true) ||
            joined.contains("POST_NOTIFICATIONS", ignoreCase = true)
        ) {
            return "无法显示通知进度。请在系统设置中允许「白模构建」通知后重试；转换不会因此闪退。"
        }
        val msg = throwable.message?.trim().orEmpty()
        if (msg.contains("模型") || msg.contains("权重")) return msg
        return msg.ifBlank { "转换失败：${throwable.javaClass.simpleName}" }
    }
}
