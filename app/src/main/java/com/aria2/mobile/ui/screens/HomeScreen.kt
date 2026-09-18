package com.aria2.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria2.mobile.data.DownloadItem
import com.aria2.mobile.data.DownloadStatus
import com.aria2.mobile.ui.formatSpeed
import com.aria2.mobile.ui.components.ConnectionChip
import com.aria2.mobile.ui.components.DownloadCard
import com.aria2.mobile.viewmodel.DownloadViewModel

@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    onOpenAdd: (initialUrl: String?) -> Unit,
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

    val serviceOn = state.serviceOn

    val subtitle = if (serviceOn) "下载服务运行中" else "下载服务未启动"

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                serviceOn = serviceOn,
                subtitle = subtitle,
            )
            if (!serviceOn) {
                ServiceSetupCard(onOpenSettings = onOpenSettings)
            }
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
    if (item.status == DownloadStatus.Active) vm.pause(item.gid) else vm.resume(item.gid)
}

@Composable
private fun TopBar(
    serviceOn: Boolean,
    subtitle: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("下载", style = MaterialTheme.typography.headlineMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            ConnectionChip(connected = serviceOn, connecting = false, error = null)
        }
    }
}

@Composable
private fun ServiceSetupCard(
    onOpenSettings: () -> Unit,
) {
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)),
        color = container,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Dns,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = onContainer,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "下载服务未启动",
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                )
                Text(
                    "重新进入应用会启动下载服务并恢复未完成任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("去设置")
            }
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
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 总速度
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        Icons.Filled.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "总速度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    formatSpeed(totalSpeed),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            VerticalDivider(Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StatItem(activeCount, "进行中", MaterialTheme.colorScheme.primary)
            VerticalDivider(Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StatItem(waitingCount, "排队中", MaterialTheme.colorScheme.secondary)
            VerticalDivider(Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StatItem(completeCount, "已完成", MaterialTheme.colorScheme.secondary)
            if (errorCount > 0) {
                VerticalDivider(Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StatItem(errorCount, "出错", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String, color: Color) {
    Column(
        Modifier.padding(start = 14.dp),
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