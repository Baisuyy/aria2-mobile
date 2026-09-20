package com.aria2.mobile.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aria2.mobile.data.SettingsStore
import com.aria2.mobile.ui.theme.parseHexColor
import com.aria2.mobile.viewmodel.DownloadViewModel
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String, val subtitle: String, val icon: ImageVector) {
    Service("下载服务", "服务开关 · 保存位置", Icons.Filled.Build),
    Download("下载", "保持常亮 · User-Agent", Icons.Filled.Settings),
    Appearance("外观", "主题 · 颜色 · 背景图", Icons.Filled.Star),
    Browser("浏览器", "内置主页（闪链）", Icons.Filled.Public),
    Logs("日志", "查看 · 复制 · 分享", Icons.Filled.FileCopy),
    About("关于", "版本与说明", Icons.Filled.Info),
}

/**
 * 设置页：两级分级菜单。先展示分类列表（[SettingsSection]），点进分类后进入该分类的子设置页。
 */
@Composable
fun SettingsScreen(viewModel: DownloadViewModel, onBack: () -> Unit) {
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sectionName?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } }

    if (section == null) {
        SettingsMenu(
            onBack = onBack,
            onOpen = { sectionName = it.name },
        )
    } else {
        SubHeader(title = section.title, onBack = { sectionName = null })
        when (section) {
            SettingsSection.Service -> ServiceSettings(viewModel)
            SettingsSection.Download -> DownloadSettings(viewModel)
            SettingsSection.Appearance -> AppearanceSettings()
            SettingsSection.Browser -> BrowserSettings()
            SettingsSection.Logs -> LogsSettings(viewModel)
            SettingsSection.About -> AboutSettings()
        }
    }
}

// ---------------------------------------------------------------------------
// 一级：分类菜单
// ---------------------------------------------------------------------------
@Composable
private fun SettingsMenu(onBack: () -> Unit, onOpen: (SettingsSection) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(4.dp))
        SettingsSection.entries.forEach { sec ->
            Surface(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onOpen(sec) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Icon(
                            sec.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(sec.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            sec.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "进入",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// 二级页面公共顶栏
// ---------------------------------------------------------------------------
@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp, start = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ScreenScaffold(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) { content() }
}

// ---------------------------------------------------------------------------
// 下载服务
// ---------------------------------------------------------------------------
@Composable
private fun ServiceSettings(viewModel: DownloadViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold {
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
        Spacer(Modifier.padding(top = 12.dp))
        Text("保存位置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
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
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// 下载
// ---------------------------------------------------------------------------
@Composable
private fun DownloadSettings(viewModel: DownloadViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold {
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

        Spacer(Modifier.padding(top = 8.dp))
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// 浏览器
// ---------------------------------------------------------------------------
@Composable
private fun BrowserSettings() {
    ScreenScaffold {
        Text("内置主页", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(HOME_URL, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "打开「浏览器」标签时自动进入该主页（闪链）。点击顶栏首页图标可随时返回。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// 日志
// ---------------------------------------------------------------------------
@Composable
private fun LogsSettings(viewModel: DownloadViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    ScreenScaffold {
        Surface(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(top = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (state.logs.isEmpty()) {
                Text(
                    "（暂无日志）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 120.dp),
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
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(logsText(state.logs))) },
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
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// 关于
// ---------------------------------------------------------------------------
@Composable
private fun AboutSettings() {
    ScreenScaffold {
        Text("aria2 Mobile · 简化下载版", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "v1.4.0\n监听下载请求，内置引擎完成下载并保存到系统「下载」目录。内置浏览器默认打开「闪链」主页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(16.dp))
    }
}

private fun logsText(logs: List<String>): String =
    if (logs.isEmpty()) "（暂无日志）" else logs.joinToString("\n")

// ---------------------------------------------------------------------------
// 外观：主题模式 / 主色调 / 文字颜色 / 背景图 + 透明蒙版
// ---------------------------------------------------------------------------
private val ACCENT_SWATCHES = listOf(
    "#10B981" to "薄荷绿",
    "#3B82F6" to "蓝",
    "#8B5CF6" to "紫",
    "#EC4899" to "粉",
    "#F59E0B" to "橙",
    "#EF4444" to "红",
)

private val TEXT_SWATCHES = listOf(
    "#13161C" to "深灰",
    "#3A404C" to "中灰",
    "#EDEFF2" to "亮白",
    "#7A8494" to "浅灰",
)

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val contentResolver = context.contentResolver
    val scope = rememberCoroutineScope()

    val themeMode by store.themeMode.collectAsStateWithLifecycle(initialValue = "system")
    val primaryHex by store.primaryColor.collectAsStateWithLifecycle(initialValue = "")
    val textHex by store.textColor.collectAsStateWithLifecycle(initialValue = "")
    val bgImageUri by store.bgImage.collectAsStateWithLifecycle(initialValue = "")
    val overlayAlpha by store.overlayAlpha.collectAsStateWithLifecycle(initialValue = 0f)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // 声明持久化读权限，重启后仍能读取所选背景图
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch { store.setBgImage(uri.toString()) }
        }
    }

    ScreenScaffold {
        Text("主题模式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeButton("跟随系统", themeMode == "system") { scope.launch { store.setThemeMode("system") } }
            ModeButton("浅色", themeMode == "light") { scope.launch { store.setThemeMode("light") } }
            ModeButton("深色", themeMode == "dark") { scope.launch { store.setThemeMode("dark") } }
        }

        Spacer(Modifier.height(14.dp))
        Text("主色调", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SwatchRow(
            colors = ACCENT_SWATCHES,
            selected = primaryHex,
            onPick = { scope.launch { store.setPrimaryColor(it) } },
            onReset = { scope.launch { store.setPrimaryColor("") } },
        )

        Spacer(Modifier.height(14.dp))
        Text("文字颜色", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SwatchRow(
            colors = TEXT_SWATCHES,
            selected = textHex,
            onPick = { scope.launch { store.setTextColor(it) } },
            onReset = { scope.launch { store.setTextColor("") } },
        )

        Spacer(Modifier.height(14.dp))
        Text("背景图", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(16.dp)) {
                if (bgImageUri.isBlank()) {
                    Text("当前使用纯色背景", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "从相册选择图片作为应用背景，可用下方透明蒙版调节明暗以保证文字可读。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text("已启用自定义背景图", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (bgImageUri.isBlank()) "选择背景图" else "更换背景图") }
                    if (bgImageUri.isNotBlank()) {
                        OutlinedButton(
                            onClick = { scope.launch { store.setBgImage("") } },
                            modifier = Modifier.weight(1f),
                        ) { Text("移除背景") }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("透明蒙版", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "亮度 ${(overlayAlpha * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Slider(
            value = overlayAlpha,
            onValueChange = { scope.launch { store.setOverlayAlpha(it) } },
            valueRange = 0f..1f,
            enabled = bgImageUri.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "0% 完全不透明蒙版、100% 全不透明；建议选图后调到 40%~70% 兼顾观感与可读性。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    if (selected) {
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SwatchRow(
    colors: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { (hex, name) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val color = parseHexColor(hex) ?: Color.Gray
                val isSel = hex == selected
                Box(
                    Modifier
                        .size(if (isSel) 34.dp else 30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onPick(hex) },
                )
                Spacer(Modifier.height(2.dp))
                Text(name, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onReset) { Text("默认") }
    }
}