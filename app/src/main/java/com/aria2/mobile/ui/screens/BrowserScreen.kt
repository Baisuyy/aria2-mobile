package com.aria2.mobile.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 内置浏览器：基于系统 WebView（Chromium 内核）。
 * - 修正常见显示问题：开启宽视口/总览模式、允许 HTTPS 页内 http 子资源、支持双指缩放。
 * - 捕获资源：拦截第三方下载协议（magnet/ed2k/thunder…）与网页下载文件，交给下载引擎。
 */
@Composable
fun BrowserScreen(
    initialUrl: String?,
    onBack: () -> Unit,
    onCapture: (String) -> Unit,
) {
    val context = LocalContext.current
    var urlText by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var hasLoaded by remember { mutableStateOf(initialUrl != null) }
    var progress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 捕获下载链接：给用户即时反馈（Snackbar），再跳转去新建下载
    val capture: (String) -> Unit = remember(onCapture) {
        { captured: String ->
            scope.launch { snackbar.showSnackbar("已捕获下载链接") }
            onCapture(captured)
        }
    }

    val webView = remember(context) {
        configuredWebView(
            context = context,
            url = initialUrl,
            onUrlChange = { urlText = it },
            onProgress = { progress = it },
            onStart = { loadError = null },
            onError = { loadError = it },
            onCapture = capture,
        )
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶栏：返回 + 地址栏 + 刷新
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                placeholder = {
                    Text(
                        "输入网址…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                keyboardActions = KeyboardActions(onGo = { load(webView, urlText) { hasLoaded = true } }),
                colors = addressBarColors(),
            )
            IconButton(onClick = { load(webView, urlText) { hasLoaded = true } }) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "前往",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = { webView.reload() }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (hasLoaded && progress in 1 until 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                loadError != null -> LoadErrorView(
                    message = loadError.orEmpty(),
                    canGoBack = webView.canGoBack(),
                    onBack = { if (webView.canGoBack()) webView.goBack() },
                    onRetry = { loadError = null; webView.reload() },
                    onExternal = { openExternally(context, urlText) },
                )
                hasLoaded -> AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                else -> BrowserStart(onGo = { load(webView, urlText) { hasLoaded = true } })
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun openExternally(context: Context, rawUrl: String) {
    val t = rawUrl.trim()
    if (t.isBlank()) return
    val uri = Uri.parse(if (t.contains("://")) t else "https://$t")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

private fun load(view: WebView, url: String, onStarted: () -> Unit) {
    val t = url.trim()
    if (t.isBlank()) return
    val normalized = if (t.contains("://")) t else "https://$t"
    view.loadUrl(normalized)
    onStarted()
}

/** 构造并初始化 WebView，返回可复用的实例（记住在 Compose 中，避免重复创建）。 */
@SuppressLint("SetJavaScriptEnabled")
private fun configuredWebView(
    context: android.content.Context,
    url: String?,
    onUrlChange: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onStart: () -> Unit,
    onError: (String) -> Unit,
    onCapture: (String) -> Unit,
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true           // 桌面站按桌面布局渲染
        settings.loadWithOverviewMode = true      // 首次加载显示整页，避免手机站被放大截断
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // 允许 https 页内 http 资源，避免白屏
        settings.mediaPlaybackRequiresUserGesture = false
        settings.blockNetworkLoads = false          // 明确允许网络加载，避免误被当作离线模式
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // 不强制 LAYER_TYPE_HARDWARE：部分设备上会导致整页白屏/渲染异常

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                // 1) magnet/ed2k/thunder 等下载协议 → 交给下载引擎
                // 2) http/https 里“明显是文件下载”的直链（按扩展名）→ 交给下载引擎，
                //    否则很多不带 Content-Disposition 头的站点点击下载时 WebView 只会去渲染，永远不产生下载事件
                // 其余交给 WebView 正常渲染
                return when {
                    isExternalOrDownloadScheme(u) || looksLikeDownload(u) -> {
                        onCapture(u)
                        true
                    }
                    else -> false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let(onUrlChange)
                onStart()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request != null && request.isForMainFrame) {
                    onError(error?.description?.toString() ?: "页面加载失败")
                }
            }

            @Deprecated("API < 23")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (failingUrl == view?.url) onError(description ?: "页面加载失败")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                onUrlChange(view?.url?.toString().orEmpty())
            }

            // 渲染进程崩溃（常见于系统 WebView 损坏或设备内存不足）：给出明确提示而非停留在白屏
            @android.annotation.SuppressLint("WebViewRenderProcessGone")
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                onError("网页渲染进程已结束（系统 WebView 异常或内存不足），请重试")
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
            }
        }

        setDownloadListener(DownloadListener { url, _, _, _, _ -> onCapture(url) })

        if (url != null) loadUrl(url)
    }
}

/** 是否非 http/https 的可捕获协议（magnet/ed2k/thunder/qqdl/ftp…）。 */
private fun isExternalOrDownloadScheme(url: String): Boolean = when {
    url.startsWith("http://") || url.startsWith("https://") -> false
    url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("file:") -> false
    else -> true
}

/** 常见可直接下载的文件扩展名；命中即视为下载链接，交给下载引擎以便支持断点/后台下载。 */
private val DOWNLOAD_EXT = setOf(
    "apk", "zip", "rar", "7z", "gz", "bz2", "xz", "tgz", "tar", "zst",
    "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "mpg", "mpeg",
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus",
    "torrent", "exe", "msi", "dmg", "iso", "bin",
    "pdf", "epub", "mobi", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "cab", "pkg", "deb", "rpm", "apfs", "crx", "ttf", "woff2", "otf",
)

private fun looksLikeDownload(url: String): Boolean {
    val u = url.lowercase()
    if (!u.startsWith("http://") && !u.startsWith("https://")) return false
    val path = u.substringAfter("://").substringBefore('?').substringBefore('#')
    val ext = path.substringAfterLast('.', "").takeIf { it.isNotBlank() }
    return ext != null && ext in DOWNLOAD_EXT
}

@Composable
private fun LoadErrorView(
    message: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onExternal: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.padding(20.dp).size(38.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("网页加载失败", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            message.ifBlank { "可能没有网络连接，或该网站阻止了内嵌浏览器访问" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canGoBack) {
                TextButton(onClick = onBack) { Text("返回") }
            }
            OutlinedButton(onClick = onExternal) { Text("外部浏览器打开") }
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun BrowserStart(onGo: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(
                Icons.Filled.Public,
                contentDescription = null,
                modifier = Modifier.padding(22.dp).size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("输入网址开始浏览", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "在地址栏输入网址，或直接粘贴链接后按「前往」。\n页面里的磁力/ed2k 等链接或下载文件会自动加入下载任务。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun addressBarColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)