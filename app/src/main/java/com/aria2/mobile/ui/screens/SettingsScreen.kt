package com.aria2.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria2.mobile.data.Aria2Client
import com.aria2.mobile.data.Aria2Server
import com.aria2.mobile.data.DownloadPrefs
import com.aria2.mobile.viewmodel.DownloadViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: DownloadViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val client = remember { Aria2Client() }

    var rpc by rememberSaveable { mutableStateOf(state.server.rpcUrl) }
    var secret by rememberSaveable { mutableStateOf(state.server.secret) }
    var dir by rememberSaveable { mutableStateOf(state.prefs.dir) }
    var options by rememberSaveable { mutableStateOf(state.prefs.extraOptions) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

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

        SectionTitle("aria2 服务器")
        SettingsField("RPC 地址  ·  http(s)://主机:端口/jsonrpc") {
            OutlinedTextField(
                value = rpc,
                onValueChange = { rpc = it },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = sFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsField("RPC 密钥（可选）") {
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = sFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = {
                val server = Aria2Server(rpc, secret)
                testing = true
                testResult = null
                scope.launch {
                    testResult = try {
                        val v = client.getVersion(server)
                        if (v.isBlank()) "已连接（无法读取版本）" else "已连接 · aria2 $v"
                    } catch (e: Exception) {
                        "连接失败：${e.message}"
                    }
                    testing = false
                }
            },
            enabled = !testing,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            if (testing) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (testing) "测试中…" else "测试连接")
        }
        testResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        TextButtonCentered({ viewModel.saveServer(rpc, secret) }) { Text("保存服务器配置") }

        Spacer(Modifier.height(16.dp))
        SectionTitle("下载偏好（aria2c 参数）")
        SettingsField("默认保存目录（留在服务器端）") {
            OutlinedTextField(
                value = dir,
                onValueChange = { dir = it },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = sFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsField("默认参数 · 每行 key=value") {
            OutlinedTextField(
                value = options,
                onValueChange = { options = it },
                minLines = 2,
                maxLines = 6,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = sFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButtonCentered({ viewModel.savePrefs(dir, options) }) { Text("保存下载偏好") }

        Spacer(Modifier.height(16.dp))
        SectionTitle("外观与行为")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable { viewModel.setKeepScreenOn(!state.keepScreenOn) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("连接服务器时保持屏幕常亮", style = MaterialTheme.typography.titleSmall)
                Text("下载时监控屏幕是否息屏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Switch(checked = state.keepScreenOn, onCheckedChange = { viewModel.setKeepScreenOn(it) })
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("刷新间隔（秒）")
        PollSelector(current = state.pollInterval, onChange = { viewModel.setPollInterval(it) })
    }
}

@Composable
private fun PollSelector(current: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton("-") { onChange((current - 1).coerceAtLeast(1)) }
        Box(
            Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text("$current 秒", style = MaterialTheme.typography.titleMedium)
        }
        StepperButton("+") { onChange((current + 1).coerceAtMost(60)) }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth(0.28f)
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TextButtonCentered(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
        androidx.compose.material3.TextButton(onClick = onClick) {
            content()
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

@Composable
private fun SettingsField(label: String, field: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        field()
    }
}

@Composable
private fun sFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
)