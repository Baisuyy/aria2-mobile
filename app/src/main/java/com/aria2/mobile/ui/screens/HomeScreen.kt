package com.aria2.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria2.mobile.data.DownloadItem
import com.aria2.mobile.data.DownloadStatus
import com.aria2.mobile.ui.formatSpeed
import com.aria2.mobile.ui.components.ConnectionChip
import com.aria2.mobile.ui.components.DownloadCard
import com.aria2.mobile.viewmodel.ConnectionState
import com.aria2.mobile.viewmodel.DownloadViewModel

@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    onOpenAdd: (initialUrl: String?) -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // 展示错误提示
    LaunchedEffect(state.lastError) {
        state.lastError?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val connected = state.connection == ConnectionState.Connected
    val connecting = state.connection == ConnectionState.Connecting
    val error = (state.connection as? ConnectionState.Error)?.message

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                connected = connected,
                connecting = connecting,
                error = error,
                onOpenBrowser = onOpenBrowser,
                onOpenSettings = onOpenSettings,
            )
            if (state.downloads.isEmpty()) {
                EmptyState(onOpenAdd = { onOpenAdd(null) })
            } else {
                Column(Modifier.fillMaxSize()) {
                    StatsBanner(
                        totalSpeed = state.totalSpeed,
                        activeCount = state.activeCount,
                        waitingCount = state.waitingCount,
                        completeCount = state.completeCount,
                        errorCount = state.errorCount,
                    )
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.downloads, key = { it.gid }) { item ->
                            AnimatedVisibility(
                                visible = true,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                DownloadCard(
                                    item = item,
                                    onToggle = { toggle(viewModel, it) },
                                    onRemove = { viewModel.remove(it.gid) },
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        FloatingActionButton(
            onClick = { onOpenAdd(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加下载")
        }
    }
}

private fun toggle(vm: DownloadViewModel, item: DownloadItem) {
    if (item.status == DownloadStatus.Active) vm.pause(item.gid) else vm.unpause(item.gid)
}

@Composable
private fun TopBar(
    connected: Boolean,
    connecting: Boolean,
    error: String?,
    onOpenBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, start = 20.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("下载", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.weight(1f))
        ConnectionChip(connected, connecting, error)
        IconButton(onClick = onOpenBrowser) {
            Icon(Icons.Filled.Public, contentDescription = "内置浏览器", tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun StatsBanner(
    totalSpeed: Long,
    activeCount: Int,
    waitingCount: Int,
    completeCount: Int,
    errorCount: Int,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("总速度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(formatSpeed(totalSpeed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        StatItem(activeCount, "进行中", MaterialTheme.colorScheme.primary)
        StatItem(waitingCount, "排队/暂停", MaterialTheme.colorScheme.secondary)
        StatItem(completeCount, "已完成", MaterialTheme.colorScheme.secondary)
        if (errorCount > 0) {
            StatItem(errorCount, "出错", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.padding(start = 16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text("$count", style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun EmptyState(onOpenAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(
                Icons.Outlined.FileDownloadOff,
                contentDescription = null,
                modifier = Modifier.padding(22.dp).size(40.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("还没有下载任务", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "分享一个链接或用「打开链接」发送给本应用，\n链接会自动出现在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onOpenAdd) {
            Text("手动输入链接")
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}