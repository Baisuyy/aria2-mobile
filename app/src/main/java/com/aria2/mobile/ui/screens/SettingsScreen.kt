package com.aria2.mobile.ui.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria2.mobile.viewmodel.DownloadViewModel

@Composable
fun SettingsScreen(viewModel: DownloadViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineMedium)
        }

        SectionTitle("下载服务")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("下载服务（前台常驻）", style = MaterialTheme.typography.titleSmall)
                val statusText = if (state.serviceOn) "运行中 · 正在监听下载请求" else "未启动"
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.serviceOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                )
            }
            Switch(checked = state.serviceOn, onCheckedChange = {})
        }
        Text(
            "App 启动后自动开启下载服务，接收分享/打开的链接或内置浏览器捕获的下载请求。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.padding(top = 4.dp))

        SectionTitle("保存位置")
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(16.dp)) {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                Text(dir, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "下载完成后会保存到系统「下载」文件夹，可在系统文件管理器/下载 App 中查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.padding(top = 4.dp))
        SectionTitle("下载设置")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable { viewModel.setKeepScreenOn(!state.keepScreenOn) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("下载时保持屏幕常亮", style = MaterialTheme.typography.titleSmall)
                Text("避免下载过程中屏幕息屏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Switch(checked = state.keepScreenOn, onCheckedChange = { viewModel.setKeepScreenOn(it) })
        }

        Spacer(Modifier.padding(top = 4.dp))
        Text("User-Agent（下载请求）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        var uaText by rememberSaveable(state.userAgent) { mutableStateOf(state.userAgent) }
        OutlinedTextField(
            value = uaText,
            onValueChange = { uaText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            singleLine = false,
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
            placeholder = { Text("留空使用默认 User-Agent") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.setUserAgent(uaText) },
                modifier = Modifier.weight(1f),
            ) { Text("保存") }
            TextButton(
                onClick = { uaText = ""; viewModel.resetUserAgent() },
                modifier = Modifier.weight(1f),
            ) { Text("恢复默认") }
        }

        Spacer(Modifier.padding(top = 4.dp))
        SectionTitle("日志")
        Surface(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(vertical = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column {
                if (state.logs.isEmpty()) {
                    Text(
                        "（暂无日志）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 80.dp),
                    )
                } else {
                    Text(
                        state.logs.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(AppLoggerText(state.logs))) },
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Filled.FileCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp)); Text("复制") }
            OutlinedButton(
                onClick = { viewModel.shareLogs() },
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp)); Text("分享") }
            OutlinedButton(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp)); Text("清空") }
        }

        Spacer(Modifier.padding(top = 4.dp))
        SectionTitle("关于")
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("aria2 Mobile · 简化下载版", style = MaterialTheme.typography.bodyMedium)
            Text(
                "监听下载请求，由内置引擎完成下载并保存到系统下载目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

private fun AppLoggerText(logs: List<String>): String =
    if (logs.isEmpty()) "（暂无日志）" else logs.joinToString("\n")

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp, top = 4.dp),
    )
}