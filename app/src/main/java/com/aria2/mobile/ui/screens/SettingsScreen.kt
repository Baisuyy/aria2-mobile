package com.aria2.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria2.mobile.viewmodel.DownloadViewModel

@Composable
fun SettingsScreen(viewModel: DownloadViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            "App 启动后自动开启下载服务，接收分享/打开的链接或内置浏览器捕获的下载请求，文件保存到应用专属目录。",
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
                Text("Android/data/com.aria2.mobile/files/Download", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "应用专属目录，卸载应用会一并清除。完成后的文件也可从系统「下载」查看（如需可后续接入 SAF 选择目录）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.padding(top = 4.dp))
        SectionTitle("外观与行为")
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
        SectionTitle("关于")
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("aria2 Mobile · 简化下载版", style = MaterialTheme.typography.bodyMedium)
            Text(
                "仅监听下载请求，由内置引擎完成下载，不再依赖 aria2 服务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp, top = 4.dp),
    )
}