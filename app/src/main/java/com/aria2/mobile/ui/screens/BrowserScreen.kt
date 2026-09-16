package com.aria2.mobile.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val HOME_URL = "https://www.bing.com"

/**
 * 内置浏览器：浏览页面并把「当前页 / 网页下载文件」的链接捕获为 aria2 下载任务。
 * 网页内触发下载时由 [DownloadListener] 拦截真实文件地址，交给 [onCapture] 补录。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String?,
    onBack: () -> Unit,
    onCapture: (String) -> Unit,
) {
    val context = LocalContext.current

    var urlBar by rememberSaveable { mutableStateOf(initialUrl ?: HOME_URL) }
    var loading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val u = request?.url?.toString() ?: return false
                    return if (isExternalScheme(u)) {
                        onCapture(u)
                        true
                    } else {
                        false
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    view?.url?.let { urlBar = it }
                    canGoBack = view?.canGoBack() ?: false
                    canGoForward = view?.canGoForward() ?: false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    view?.url?.let { urlBar = it }
                    canGoBack = view?.canGoBack() ?: false
                    canGoForward = view?.canGoForward() ?: false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loading = newProgress < 100
                }
            }

            setDownloadListener(DownloadListener { url, _, _, _, _ ->
                onCapture(url)
            })
            post { loadUrl(initialUrl ?: HOME_URL) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            url = urlBar,
            loading = loading,
            onUrlChange = { urlBar = it },
            onGo = { loadFromBar(webView, urlBar) },
            onBack = onBack,
        )

        Box(Modifier.weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }

        BottomBar(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onBack = { webView.goBack() },
            onForward = { webView.goForward() },
            onReload = { webView.reload() },
            onCapture = { webView.url?.let(onCapture) },
        )
    }
}

@Composable
private fun TopBar(
    url: String,
    loading: Boolean,
    onUrlChange: (String) -> Unit,
    onGo: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            placeholder = {
                Text(
                    "输入网址…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
                keyboardType = KeyboardType.Uri,
            ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            colors = browserFieldColors(),
        )
        IconButton(onClick = onGo, modifier = Modifier.width(48.dp)) {
            Icon(Icons.Filled.OpenInBrowser, contentDescription = "打开")
        }
    }
}

@Composable
private fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onCapture: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
            }
            IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                    .clickable(onClick = onCapture)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "加入下载",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun isExternalScheme(url: String): Boolean {
    val scheme = Uri.parse(url).scheme?.lowercase()
    return scheme != null && scheme != "http" && scheme != "https" &&
        scheme != "about" && scheme != "data" && scheme != "javascript"
}

private fun loadFromBar(webView: WebView, raw: String) {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return
    val target = when {
        trimmed.startsWith("http", ignoreCase = true) || trimmed.contains("://") -> trimmed
        else -> "https://$trimmed"
    }
    webView.loadUrl(target)
}

@Composable
private fun browserFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
)