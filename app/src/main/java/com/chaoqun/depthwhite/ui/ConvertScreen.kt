package com.chaoqun.depthwhite.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoqun.depthwhite.R
import com.chaoqun.depthwhite.core.ClipLength
import com.chaoqun.depthwhite.core.DurationLimit
import com.chaoqun.depthwhite.core.ExportMode
import com.chaoqun.depthwhite.core.InferPreset
import com.chaoqun.depthwhite.core.ModelSize
import com.chaoqun.depthwhite.core.QualityLevel
import com.chaoqun.depthwhite.data.ConversionProgress
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConvertScreen(vm: ConvertViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { vm.onVideoPicked(context, it) } }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { vm.onVideoPicked(context, it) } }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.startConvert() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "1. 选择视频") {
                Text(
                    state.videoName ?: "尚未选择。支持相册或文件中的 MP4 / MOV。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("相册")
                    }
                    OutlinedButton(onClick = { pickFile.launch("video/*") }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("文件")
                    }
                }
            }

            SectionCard(title = "2. 模型大小") {
                ChipRow(
                    items = listOf(
                        ModelSize.SMALL to "Small（默认）",
                        ModelSize.BASE to "Base",
                        ModelSize.LARGE to "Large",
                    ),
                    selected = state.options.modelSize,
                    onSelect = { size -> vm.updateOptions { opt -> opt.copy(modelSize = size) } },
                )
                Text(
                    state.modelHint.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.options.modelSize == ModelSize.LARGE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!state.modelReady) {
                    Text("模型未下载。开始转换时会自动下载并显示进度；也可将 ONNX 放到应用 files/models/。")
                }
            }

            SectionCard(title = "3. 推理分辨率（转换前缩小，省内存）") {
                ChipRow(
                    items = listOf(
                        InferPreset.SAVE to "省显存 384",
                        InferPreset.BALANCED to "平衡 518",
                        InferPreset.HIGH to "较高 756",
                    ),
                    selected = state.options.inferPreset,
                    onSelect = { vm.updateOptions { opt -> opt.copy(inferPreset = it) } },
                )
            }

            SectionCard(title = "4. 导出分辨率") {
                ChipRow(
                    items = listOf(
                        ExportMode.P360 to "360p",
                        ExportMode.P480 to "480p（默认）",
                        ExportMode.P640 to "640",
                        ExportMode.ORIGINAL to "原始",
                        ExportMode.CUSTOM to "自定义宽",
                    ),
                    selected = state.options.exportMode,
                    onSelect = { vm.updateOptions { opt -> opt.copy(exportMode = it) } },
                )
                if (state.options.exportMode == ExportMode.CUSTOM) {
                    OutlinedTextField(
                        value = state.options.customMaxWidth.toString(),
                        onValueChange = { raw ->
                            val v = raw.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 720
                            vm.updateOptions { it.copy(customMaxWidth = v.coerceIn(160, 1920)) }
                        },
                        label = { Text("自定义最大宽度") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionCard(title = "5. 输出画质") {
                ChipRow(
                    items = listOf(
                        QualityLevel.TINY to "极小",
                        QualityLevel.SMALL to "小（默认）",
                        QualityLevel.MEDIUM to "中",
                    ),
                    selected = state.options.quality,
                    onSelect = { vm.updateOptions { opt -> opt.copy(quality = it) } },
                )
            }

            SectionCard(title = "6. 深度与时序") {
                ToggleRow("反转深度（近黑远白）", state.options.invertDepth) {
                    vm.updateOptions { opt -> opt.copy(invertDepth = it) }
                }
                ToggleRow("时序平滑 EMA（减闪烁）", state.options.emaEnabled) {
                    vm.updateOptions { opt -> opt.copy(emaEnabled = it) }
                }
                if (state.options.emaEnabled) {
                    Text("EMA α = ${"%.2f".format(state.options.emaAlpha)}（越大越稳，重影越多）")
                    Slider(
                        value = state.options.emaAlpha,
                        onValueChange = { v -> vm.updateOptions { opt -> opt.copy(emaAlpha = v) } },
                        valueRange = 0.05f..0.90f,
                    )
                }
                ToggleRow("尽可能保留原音频", state.options.keepAudio) {
                    vm.updateOptions { opt -> opt.copy(keepAudio = it) }
                }
            }

            SectionCard(title = "7. 输出时长") {
                ChipRow(
                    items = listOf(
                        DurationLimit.S5 to "5 秒",
                        DurationLimit.S8 to "8 秒",
                        DurationLimit.S10 to "10 秒（默认）",
                        DurationLimit.ALL to "全部",
                    ),
                    selected = state.options.durationLimit,
                    onSelect = { vm.updateOptions { opt -> opt.copy(durationLimit = it) } },
                )
            }

            if (state.running || state.clipping || state.progress.phase == ConversionProgress.Phase.DONE) {
                SectionCard(title = "进度") {
                    val total = state.progress.total
                    val frame = state.progress.frame
                    Text(state.progress.message.ifBlank { "准备中…" })
                    if (total > 0) {
                        Text("第 $frame / $total 帧")
                        LinearProgressIndicator(
                            progress = { (frame.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (state.running || state.clipping) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.progress.downloadPercent >= 0 && state.running) {
                        Text("模型下载 ${state.progress.downloadPercent}%")
                    }
                }
            }

            state.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        err,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val needed = buildList {
                            if (Build.VERSION.SDK_INT >= 33) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                                add(Manifest.permission.READ_MEDIA_VIDEO)
                            } else {
                                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }.toTypedArray()
                        permissionLauncher.launch(needed)
                    },
                    enabled = !state.running && !state.clipping,
                    modifier = Modifier.weight(1f),
                ) { Text("开始转换") }
                OutlinedButton(
                    onClick = { vm.cancel() },
                    enabled = state.running || state.clipping,
                ) { Text("取消") }
            }

            if (!state.outputPath.isNullOrBlank()) {
                SectionCard(title = "8. 转换完成 · 片段导出") {
                    Text(state.outputPath.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.clipStartSeconds,
                            onValueChange = vm::setClipStart,
                            label = { Text("起始秒") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            val path = state.outputPath ?: return@IconButton
                            val file = File(path)
                            val uri = FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                file,
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "分享白模视频"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                    }
                    ChipRow(
                        items = listOf(
                            ClipLength.S5 to "5 秒",
                            ClipLength.S8 to "8 秒",
                            ClipLength.S10 to "10 秒",
                        ),
                        selected = state.clipLength,
                        onSelect = vm::setClipLength,
                    )
                    Button(
                        onClick = { vm.exportClip() },
                        enabled = !state.running && !state.clipping,
                    ) { Text("导出片段") }
                    state.clipPath?.let { Text("片段：$it", style = MaterialTheme.typography.bodySmall) }
                }
            }

            Text(
                "内存提示：中端机请保持 Small + 省显存 384 + 480p + 10 秒。Large 仅供高内存设备。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
