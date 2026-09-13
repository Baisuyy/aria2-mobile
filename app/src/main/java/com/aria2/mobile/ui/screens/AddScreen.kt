package com.aria2.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria2.mobile.viewmodel.DownloadViewModel

@Composable
fun AddScreen(
    viewModel: DownloadViewModel,
    initialUrl: String?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    var filename by rememberSaveable { mutableStateOf("") }
    var optionsRaw by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.lastError) {
        state.lastError?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("新建下载", style = MaterialTheme.typography.headlineMedium)
        }

        FormField(
            label = "链接",
            isSingleLine = false,
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://… 或 magnet:?xt=…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                minLines = 2,
                maxLines = 5,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                colors = fieldColors(),
            )
        }

        FormField("保存文件名（可选）") {
            OutlinedTextField(
                value = filename,
                onValueChange = { filename = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = fieldColors(),
            )
        }

        FormField("aria2 参数（可选，每行一个）") {
            OutlinedTextField(
                value = optionsRaw,
                onValueChange = { optionsRaw = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("split=16\nseed-time=0", style = MaterialTheme.typography.bodyMedium) },
                minLines = 2,
                maxLines = 6,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = fieldColors(),
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.addDownload(url, filename, optionsRaw)
                if (url.isNotBlank()) onBack()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large,
            enabled = url.isNotBlank(),
        ) {
            Text("开始下载", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = if (state.server.rpcUrl.isNotBlank()) "将推送到 ${state.server.rpcUrl}" else "请先到设置中配置 aria2 服务器",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
        SnackbarHost(snackbar)
    }
}

@Composable
private fun FormField(label: String, isSingleLine: Boolean = true, field: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        field()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
)