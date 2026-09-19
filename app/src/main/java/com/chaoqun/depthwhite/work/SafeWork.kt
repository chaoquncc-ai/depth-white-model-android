package com.chaoqun.depthwhite.work

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo

internal suspend fun CoroutineWorker.trySetForeground(info: ForegroundInfo): Boolean {
    return try {
        setForeground(info)
        true
    } catch (t: Throwable) {
        Log.w("DepthWhiteWork", "Foreground service skipped: ${t.message}")
        false
    }
}

internal fun CoroutineWorker.trySetForegroundAsync(info: ForegroundInfo) {
    try {
        setForegroundAsync(info)
    } catch (t: Throwable) {
        Log.w("DepthWhiteWork", "Foreground update skipped: ${t.message}")
    }
}
