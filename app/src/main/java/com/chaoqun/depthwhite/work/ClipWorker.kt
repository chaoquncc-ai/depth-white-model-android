package com.chaoqun.depthwhite.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chaoqun.depthwhite.data.WorkKeys
import com.chaoqun.depthwhite.video.Errors
import com.chaoqun.depthwhite.video.MediaStorePublisher
import com.chaoqun.depthwhite.video.VideoClipper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ClipWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() = NotificationHelper.foregroundInfo(
        applicationContext,
        applicationContext.getString(com.chaoqun.depthwhite.R.string.notification_clip_title),
        applicationContext.getString(com.chaoqun.depthwhite.R.string.notification_clip_running),
        0,
        0,
        NotificationHelper.CLIP_ID,
    )

    override suspend fun doWork(): Result {
        return try {
            try {
                trySetForeground(getForegroundInfo())
            } catch (t: Throwable) {
                Log.w(TAG, "getForegroundInfo failed", t)
            }
            val inputPath = inputData.getString(WorkKeys.CLIP_INPUT)
                ?: return Result.failure(workDataOf(WorkKeys.ERROR to "没有可裁剪的输出视频"))
            val startMs = inputData.getLong(WorkKeys.CLIP_START_MS, 0L)
            val durationMs = inputData.getLong(WorkKeys.CLIP_DURATION_MS, 5_000L)
            val input = File(inputPath)
            if (!input.exists()) {
                return Result.failure(workDataOf(WorkKeys.ERROR to "原转换文件已丢失，请重新转换"))
            }
            val output = File(input.parentFile, "clip_${System.currentTimeMillis()}.mp4")
            withContext(Dispatchers.Default) {
                VideoClipper.clipFile(
                    input = input,
                    output = output,
                    startUs = startMs * 1000,
                    durationUs = durationMs * 1000,
                    isCancelled = { isStopped },
                )
            }
            val published = MediaStorePublisher.publishMp4(applicationContext, output, output.name)
            Result.success(
                workDataOf(
                    WorkKeys.OUTPUT_PATH to output.absolutePath,
                    WorkKeys.OUTPUT_URI to (published?.toString() ?: ""),
                    WorkKeys.MESSAGE to "片段已导出",
                ),
            )
        } catch (t: Throwable) {
            Result.failure(workDataOf(WorkKeys.ERROR to Errors.chinese(t)))
        }
    }

    companion object {
        private const val TAG = "ClipWorker"
    }
}
