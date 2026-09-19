package com.chaoqun.depthwhite.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chaoqun.depthwhite.core.ClipLength
import com.chaoqun.depthwhite.core.ModelSize
import com.chaoqun.depthwhite.data.ConvertOptions
import com.chaoqun.depthwhite.data.ConversionProgress
import com.chaoqun.depthwhite.data.WorkKeys
import com.chaoqun.depthwhite.ml.ModelStore
import com.chaoqun.depthwhite.work.ClipWorker
import com.chaoqun.depthwhite.work.ConversionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConvertUiState(
    val videoUri: String? = null,
    val videoName: String? = null,
    val options: ConvertOptions = ConvertOptions(),
    val running: Boolean = false,
    val clipping: Boolean = false,
    val progress: ConversionProgress = ConversionProgress(),
    val outputPath: String? = null,
    val outputUri: String? = null,
    val clipPath: String? = null,
    val error: String? = null,
    val modelReady: Boolean = false,
    val modelHint: String? = null,
    val clipStartSeconds: String = "0",
    val clipLength: ClipLength = ClipLength.S5,
)

class ConvertViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private val store = ModelStore(application)

    private val _state = MutableStateFlow(ConvertUiState())
    val state: StateFlow<ConvertUiState> = _state.asStateFlow()

    init {
        refreshModelStatus()
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WorkKeys.UNIQUE_WORK).collect { infos ->
                handleWork(infos.firstOrNull(), clip = false)
            }
        }
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WorkKeys.UNIQUE_CLIP).collect { infos ->
                handleWork(infos.firstOrNull(), clip = true)
            }
        }
    }

    fun onVideoPicked(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        val name = queryDisplayName(context, uri)
        _state.update {
            it.copy(
                videoUri = uri.toString(),
                videoName = name,
                error = null,
                outputPath = null,
                outputUri = null,
                clipPath = null,
            )
        }
    }

    fun updateOptions(transform: (ConvertOptions) -> ConvertOptions) {
        _state.update { it.copy(options = transform(it.options), error = null) }
        refreshModelStatus()
    }

    fun setClipStart(value: String) {
        _state.update { it.copy(clipStartSeconds = value.filter { ch -> ch.isDigit() }.take(5)) }
    }

    fun setClipLength(length: ClipLength) {
        _state.update { it.copy(clipLength = length) }
    }

    fun refreshModelStatus() {
        val size = _state.value.options.modelSize
        val bundled = store.hasBundledAsset(size)
        val ready = store.isReady(size)
        val hint = when {
            size == ModelSize.SMALL && bundled ->
                "APK 已内置 Small（${size.fileName}），首次启动无需联网。"
            ready -> "模型已就绪：${size.fileName}"
            size == ModelSize.LARGE ->
                "Large 约 1.3GB，中端机极易内存不足，建议 Small / Base。首次转换会自动下载。"
            size == ModelSize.BASE ->
                "Base 约 390MB。首次转换会自动下载（Small 已随 APK 内置）。"
            bundled -> "正在从 APK 复制内置 Small 模型…"
            else -> "Small 约 100MB。当前 APK 未打入该权重，开始转换时会自动下载。"
        }
        _state.update { it.copy(modelReady = ready, modelHint = hint) }
    }

    fun startConvert() {
        val current = _state.value
        val uri = current.videoUri
        if (uri.isNullOrBlank()) {
            _state.update { it.copy(error = "请先选择视频（相册或文件，MP4/MOV）") }
            return
        }
        val infos = workManager.getWorkInfosForUniqueWork(WorkKeys.UNIQUE_WORK).get()
        if (infos.any { !it.state.isFinished }) {
            _state.update { it.copy(error = "已有转换任务在运行") }
            return
        }
        val opt = current.options
        val data = workDataOf(
            WorkKeys.INPUT_URI to uri,
            WorkKeys.MODEL to opt.modelSize.name,
            WorkKeys.INFER to opt.inferPreset.name,
            WorkKeys.EXPORT to opt.exportMode.name,
            WorkKeys.CUSTOM_WIDTH to opt.customMaxWidth,
            WorkKeys.QUALITY to opt.quality.name,
            WorkKeys.INVERT to opt.invertDepth,
            WorkKeys.EMA to opt.emaEnabled,
            WorkKeys.EMA_ALPHA to opt.emaAlpha,
            WorkKeys.KEEP_AUDIO to opt.keepAudio,
            WorkKeys.DURATION to opt.durationLimit.name,
        )
        val request = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(WorkKeys.UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        _state.update {
            it.copy(
                running = true,
                error = null,
                outputPath = null,
                progress = ConversionProgress(
                    phase = ConversionProgress.Phase.CONVERT,
                    message = "任务已提交…",
                ),
            )
        }
    }

    fun cancel() {
        workManager.cancelUniqueWork(WorkKeys.UNIQUE_WORK)
        workManager.cancelUniqueWork(WorkKeys.UNIQUE_CLIP)
        _state.update {
            it.copy(
                running = false,
                clipping = false,
                progress = ConversionProgress(message = "已取消"),
            )
        }
    }

    fun exportClip() {
        val path = _state.value.outputPath
        if (path.isNullOrBlank()) {
            _state.update { it.copy(error = "请先完成转换，再导出片段") }
            return
        }
        val startSec = _state.value.clipStartSeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val durationMs = when (_state.value.clipLength) {
            ClipLength.S5 -> 5_000L
            ClipLength.S8 -> 8_000L
            ClipLength.S10 -> 10_000L
        }
        val request = OneTimeWorkRequestBuilder<ClipWorker>()
            .setInputData(
                workDataOf(
                    WorkKeys.CLIP_INPUT to path,
                    WorkKeys.CLIP_START_MS to startSec * 1000L,
                    WorkKeys.CLIP_DURATION_MS to durationMs,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(WorkKeys.UNIQUE_CLIP, ExistingWorkPolicy.REPLACE, request)
        _state.update {
            it.copy(
                clipping = true,
                error = null,
                progress = ConversionProgress(
                    phase = ConversionProgress.Phase.CLIP,
                    message = "正在导出片段…",
                ),
            )
        }
    }

    private fun handleWork(info: WorkInfo?, clip: Boolean) {
        if (info == null) return
        val progress = info.progress
        val message = progress.getString(WorkKeys.MESSAGE)
            ?: info.outputData.getString(WorkKeys.MESSAGE).orEmpty()
        val frame = progress.getInt(WorkKeys.FRAME, 0)
        val total = progress.getInt(WorkKeys.TOTAL, 0)
        val phaseName = progress.getString(WorkKeys.PHASE)
        val phase = runCatching {
            ConversionProgress.Phase.valueOf(phaseName ?: ConversionProgress.Phase.CONVERT.name)
        }.getOrDefault(ConversionProgress.Phase.CONVERT)
        when (info.state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                _state.update {
                    it.copy(
                        running = !clip,
                        clipping = clip,
                        error = null,
                        progress = ConversionProgress(
                            phase = if (clip) ConversionProgress.Phase.CLIP else phase,
                            frame = frame,
                            total = total,
                            message = message.ifBlank { if (clip) "正在导出片段…" else "转换中…" },
                            downloadPercent = progress.getInt(WorkKeys.DOWNLOAD, -1),
                        ),
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                val path = info.outputData.getString(WorkKeys.OUTPUT_PATH)
                val uri = info.outputData.getString(WorkKeys.OUTPUT_URI)
                _state.update {
                    if (clip) {
                        it.copy(
                            clipping = false,
                            clipPath = path,
                            error = null,
                            progress = ConversionProgress(
                                phase = ConversionProgress.Phase.DONE,
                                message = message.ifBlank { "片段已导出" },
                            ),
                        )
                    } else {
                        it.copy(
                            running = false,
                            outputPath = path ?: it.outputPath,
                            outputUri = uri?.takeIf { s -> s.isNotBlank() },
                            error = null,
                            progress = ConversionProgress(
                                phase = ConversionProgress.Phase.DONE,
                                frame = info.outputData.getInt(WorkKeys.FRAME, frame),
                                total = info.outputData.getInt(WorkKeys.FRAME, total),
                                message = message.ifBlank { "转换完成" },
                            ),
                        )
                    }
                }
                refreshModelStatus()
            }
            WorkInfo.State.FAILED -> {
                val err = info.outputData.getString(WorkKeys.ERROR) ?: "任务失败"
                _state.update {
                    it.copy(
                        running = false,
                        clipping = false,
                        error = err,
                        progress = ConversionProgress(
                            phase = ConversionProgress.Phase.ERROR,
                            message = err,
                        ),
                    )
                }
            }
            WorkInfo.State.CANCELLED -> {
                _state.update {
                    it.copy(
                        running = false,
                        clipping = false,
                        progress = ConversionProgress(message = "已取消"),
                    )
                }
            }
            else -> Unit
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment ?: "video"
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0) ?: fallback
                    } else {
                        fallback
                    }
                } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
