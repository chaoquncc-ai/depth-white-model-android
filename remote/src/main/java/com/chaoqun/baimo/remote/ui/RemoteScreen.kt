package com.chaoqun.baimo.remote.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chaoqun.baimo.remote.R
import com.chaoqun.baimo.remote.RemoteViewModel
import com.chaoqun.baimo.remote.ServerUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    vm: RemoteViewModel,
    ensureWebView: (android.content.Context) -> WebView,
    onReload: () -> Unit,
    onOpenBrowser: () -> Unit,
    onClearCache: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onResetUrl: () -> Unit,
    onApplyKeepScreenOn: (Boolean) -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(vm.keepScreenOn) {
        onApplyKeepScreenOn(vm.keepScreenOn)
    }
    LaunchedEffect(vm.snackbar) {
        val message = vm.snackbar ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message)
        vm.consumeSnackbar()
    }

    BackHandler(enabled = vm.showSettings) {
        vm.showSettings = false
    }

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
                actions = {
                    IconButton(onClick = onReload) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reload))
                    }
                    IconButton(onClick = { vm.showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_browser)) },
                            onClick = {
                                menuOpen = false
                                onOpenBrowser()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (vm.keepScreenOn) R.string.keep_screen_on_on
                                        else R.string.keep_screen_on_off,
                                    ),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                vm.setKeepScreenOn(!vm.keepScreenOn)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clear_cache)) },
                            onClick = {
                                menuOpen = false
                                onClearCache()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = { context -> ensureWebView(context) },
                modifier = Modifier.fillMaxSize(),
            )
            if (vm.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { vm.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
            vm.pageError?.let {
                ErrorCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    onRetry = onReload,
                )
            }
            if (vm.showSettings) {
                SettingsCard(
                    currentUrl = vm.serverUrl,
                    keepScreenOn = vm.keepScreenOn,
                    onSave = onSaveUrl,
                    onReset = onResetUrl,
                    onKeepScreenOn = vm::setKeepScreenOn,
                    onDismiss = { vm.showSettings = false },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.page_error), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    currentUrl: String,
    keepScreenOn: Boolean,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(currentUrl) { mutableStateOf(currentUrl) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.compute_note),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.server_url)) },
                placeholder = { Text(ServerUrl.DEFAULT) },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(draft) }) {
                    Text(stringResource(R.string.save_url))
                }
                OutlinedButton(onClick = {
                    onReset()
                    draft = ServerUrl.DEFAULT
                }) {
                    Text(stringResource(R.string.reset_default))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
