package com.chaoqun.depthwhite.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorsTest {
    @Test
    fun cancelledIsChinese() {
        assertEquals("已取消", Errors.chinese(InterruptedException("cancelled")))
    }

    @Test
    fun oomIsChineseHint() {
        val msg = Errors.chinese(OutOfMemoryError("Failed to allocate a 64 byte allocation"))
        assertTrue(msg.contains("内存不足"))
        assertTrue(msg.contains("Small"))
    }

    @Test
    fun missingModelMessageIsKept() {
        val msg = Errors.chinese(IllegalStateException("模型文件缺失。Small 应已随 APK 内置；Base/Large 需联网下载后再试。"))
        assertTrue(msg.contains("模型文件缺失"))
    }

    @Test
    fun foregroundFailureIsSoftError() {
        val msg = Errors.chinese(IllegalStateException("Not allowed to start foreground service / setForeground"))
        assertTrue(msg.contains("通知"))
        assertTrue(msg.contains("白模构建"))
    }
}
