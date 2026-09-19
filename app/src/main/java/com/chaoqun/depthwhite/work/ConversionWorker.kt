package com.chaoqun.depthwhite.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chaoqun.depthwhite.core.DurationLimit
import com.chaoqun.depthwhite.core.ExportMode
import com.chaoqun.depthwhite.core.InferPreset
import com.chaoqun.depthwhite.core.ModelSize
import com.chaoqun.depthwhite.core.QualityLevel
import com.chaoqun.depthwhite.data.ConvertOptions
import com.chaoqun.depthwhite.data.WorkKeys
import com.chaoqun.depthwhite.video.ConversionPipeline
import com.chaoqun.depthwhite.video.Errors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConversionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() = NotificationHelper.foregroundInfo(
        applicationContext,
        applicationContext.getString(com.chaoqun.depthwhite.R.string.notification_title),
        applicationContext.getString(com.chaoqun.depthwhite.R.string.notification_preparing),
        0,
        0,
    )

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        val uriStr = inputData.getString(WorkKeys.INPUT_URI)
            ?: return Result.failure(workDataOf(WorkKeys.ERROR to "请先选择视频"))
        val uri = Uri.parse(uriStr)
        val options = ConvertOptions(
            modelSize = enumValueOf(inputData.getString(WorkKeys.MODEL) ?: ModelSize.SMALL.name),
            inferPreset = enumValueOf(inputData.getString(WorkKeys.INFER) ?: InferPreset.SAVE.name),
            exportMode = enumValueOf(inputData.getString(WorkKeys.EXPORT) ?: ExportMode.P480.name),
            customMaxWidth = inputData.getInt(WorkKeys.CUSTOM_WIDTH, 720),
            quality = enumValueOf(inputData.getString(WorkKeys.QUALITY) ?: QualityLevel.SMALL.name),
            invertDepth = inputData.getBoolean(WorkKeys.INVERT, false),
            emaEnabled = inputData.getBoolean(WorkKeys.EMA, true),
            emaAlpha = inputData.getFloat(WorkKeys.EMA_ALPHA, 0.45f),
            keepAudio = inputData.getBoolean(WorkKeys.KEEP_AUDIO, true),
            durationLimit = enumValueOf(inputData.getString(WorkKeys.DURATION) ?: DurationLimit.S10.name),
        )
        return try {
            val result = withContext(Dispatchers.Default) {
                ConversionPipeline(applicationContext).convert(
                    input = uri,
                    options = options,
                    onProgress = { progress ->
                        setProgressAsync(
                            workDataOf(
                                WorkKeys.PHASE to progress.phase.name,
                                WorkKeys.FRAME to progress.frame,
                                WorkKeys.TOTAL to progress.total,
                                WorkKeys.MESSAGE to progress.message,
                                WorkKeys.DOWNLOAD to progress.downloadPercent,
                            ),
                        )
                        val max = progress.total.coerceAtLeast(0)
                        val current = progress.frame.coerceAtLeast(0)
                        setForegroundAsync(
                            NotificationHelper.foregroundInfo(
                                applicationContext,
                                applicationContext.getString(com.chaoqun.depthwhite.R.string.notification_title),
                                progress.message.ifBlank { "第 $current / $max 帧" },
                                current,
                                max,
                            ),
                        )
                    },
                    isCancelled = { isStopped },
                )
            }
            Result.success(
                workDataOf(
                    WorkKeys.OUTPUT_PATH to result.file.absolutePath,
                    WorkKeys.OUTPUT_URI to (result.mediaStoreUri ?: ""),
                    WorkKeys.MESSAGE to result.message,
                    WorkKeys.FRAME to result.frameCount,
                ),
            )
        } catch (t: Throwable) {
            Result.failure(workDataOf(WorkKeys.ERROR to Errors.chinese(t)))
        }
    }
}
