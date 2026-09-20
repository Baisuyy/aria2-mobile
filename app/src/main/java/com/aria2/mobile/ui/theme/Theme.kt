package com.aria2.mobile.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/** 主题配置：由设置持久化得到，驱动动态配色与背景绘制。 */
data class AppThemeConfig(
    val dark: Boolean = false,
    val primary: Color = Accent,
    val textColor: Color = LightOnBg,
    val bgImageUri: String? = null,
    val overlayAlpha: Float = 0f,
)

/** 根据主色在深/浅底上的亮度选择前景（白或黑），保证对比度。 */
private fun contrastOn(c: Color): Color {
    val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return if (l > 0.6f) Color(0xFF000000) else Color(0xFFFFFFFF)
}

/** 动态生成深/浅两套配色：主色、文字色可自定义。 */
fun buildColorScheme(dark: Boolean, primary: Color = Accent, textColor: Color? = null): androidx.compose.material3.ColorScheme {
    val text = textColor ?: if (dark) DarkOnBg else LightOnBg
    val bg = if (dark) DarkBg else LightBg
    val surface = if (dark) DarkSurface else LightSurface
    val surfaceHi = if (dark) DarkSurfaceHi else LightSurfaceHi
    val outline = if (dark) DarkOutline else LightOutline
    val muted = if (dark) DarkMuted else LightMuted
    val onPrimary = contrastOn(primary)
    val primaryContainer = if (dark) primary.copy(alpha = 0.26f) else primary.copy(alpha = 0.15f)

    return if (dark) darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimary,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHi,
        onSurfaceVariant = text,
        outline = outline,
        outlineVariant = outline,
        secondary = muted,
        onSecondary = text,
        error = Danger,
        onError = onPrimary,
    ) else lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimary,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHi,
        onSurfaceVariant = text,
        outline = outline,
        outlineVariant = outline,
        secondary = muted,
        onSecondary = text,
        error = Danger,
        onError = onPrimary,
    )
}

@Composable
fun Aria2Theme(
    config: AppThemeConfig = AppThemeConfig(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = remember(config.dark, config.primary, config.textColor) {
        buildColorScheme(config.dark, config.primary, config.textColor)
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // 背景位图（按 URI 加载一次；不设置则为纯色背景）
        val bitmap = remember(config.bgImageUri) { loadBitmap(context, config.bgImageUri) }
        val overlayColor = if (config.dark) Color.Black else Color.White
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 透明蒙版：叠加一层半透明底色，保证前景文字可读
                if (config.overlayAlpha > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(overlayColor.copy(alpha = config.overlayAlpha.coerceIn(0f, 1f))),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().background(if (config.dark) DarkBg else LightBg))
            }
            content()
        }
    }
}

private fun loadBitmap(context: Context, uri: String?): android.graphics.Bitmap? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        val parsed = Uri.parse(uri)
        context.contentResolver.openInputStream(parsed)?.use { input ->
            // 先读尺寸，缩小采样避免大图 OOM
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            val sample = when {
                bounds.outWidth <= 0 && bounds.outHeight <= 0 -> 1
                else -> {
                    val maxSide = 1920
                    var s = 1
                    while (bounds.outWidth / (s * 2) >= maxSide || bounds.outHeight / (s * 2) >= maxSide) {
                        s *= 2
                    }
                    s
                }
            }
            context.contentResolver.openInputStream(parsed)?.use { input2 ->
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeStream(input2, null, opts)
            }
        }
    }.getOrNull()
}