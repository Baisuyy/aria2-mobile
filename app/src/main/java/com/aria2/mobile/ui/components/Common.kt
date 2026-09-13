package com.aria2.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aria2.mobile.data.DownloadItem
import com.aria2.mobile.data.DownloadStatus
import com.aria2.mobile.ui.formatBytes
import com.aria2.mobile.ui.formatPercent
import com.aria2.mobile.ui.formatSpeed

@Composable
fun ConnectionChip(
    connected: Boolean,
    connecting: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val statusColor = when {
        connecting -> MaterialTheme.colorScheme.secondary
        connected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val label = when {
        connecting -> "连接中…"
        connected -> "已连接"
        error != null -> "异常"
        else -> "未连接"
    }
    val animated by animateColorAsState(statusColor, label = "conn")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(animated.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(animated))
        Text(label, style = MaterialTheme.typography.labelMedium, color = animated)
    }
}

@Composable
fun DownloadCard(
    item: DownloadItem,
    onToggle: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressTarget = animateFloatAsState(item.progress, spring(dampingRatio = 1f), label = "prog")
    val animatedProgress = progressTarget.value
    val isActive = item.status == DownloadStatus.Active
    val showsButton = item.status == DownloadStatus.Active || item.status == DownloadStatus.Paused

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = formatPercent(animatedProgress),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(item.status),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progressTarget.value },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = statusColor(item.status),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StatusLine(item)
                }
                if (showsButton) {
                    IconButton(onClick = { onToggle(item) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isActive) Icons.Filled.Pause
                            else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(item: DownloadItem) {
    when (item.status) {
        DownloadStatus.Active -> Text(
            text = "${formatSpeed(item.downloadSpeed)} · ${formatBytes(item.completedLength)} / ${formatBytes(item.totalLength)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        DownloadStatus.Error -> Text(
            text = item.errorMessage ?: "出错",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            text = statusText(item.status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private fun statusText(s: DownloadStatus): String = when (s) {
    DownloadStatus.Waiting -> "等待中"
    DownloadStatus.Paused -> "已暂停"
    DownloadStatus.Complete -> "已完成"
    DownloadStatus.Removed -> "已移除"
    DownloadStatus.Active, DownloadStatus.Error -> s.name
}

@Composable
private fun statusColor(s: DownloadStatus): Color = when (s) {
    DownloadStatus.Active -> MaterialTheme.colorScheme.primary
    DownloadStatus.Complete -> MaterialTheme.colorScheme.primary
    DownloadStatus.Error -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}