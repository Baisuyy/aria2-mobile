package com.aria2.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// 语义色：下载主题，以“绿 = 完成/在线、琥珀 = 下载中、红 = 错误”为语言
val Accent = Color(0xFF10B981)
val AccentDark = Color(0xFF0F7B5B)
val OnAccent = Color(0xFF04120C)

val Amber = Color(0xFFF5A623)
val Danger = Color(0xFFEF4444)

val DarkBg = Color(0xFF0E1116)
val DarkSurface = Color(0xFF161B22)
val DarkSurfaceHi = Color(0xFF1D242E)
val DarkOutline = Color(0xFF2A333F)
val DarkOnBg = Color(0xFFEDEFF2)
val DarkOnSurface = Color(0xFFC7CDD6)
val DarkMuted = Color(0xFF7A8494)

val LightBg = Color(0xFFF6F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHi = Color(0xFFEFF1F4)
val LightOutline = Color(0xFFE2E6EC)
val LightOnBg = Color(0xFF13161C)
val LightOnSurface = Color(0xFF3A404C)
val LightMuted = Color(0xFF8A94A3)

/** 解析 "#RRGGBB" 或 "#AARRGGBB" 颜色；非法返回 null。 */
fun parseHexColor(hex: String): Color? {
    val h = hex.removePrefix("#").trim()
    if (h.length != 6 && h.length != 8) return null
    val rgb = h.take(6).toLongOrNull(16) ?: return null
    val alpha = if (h.length == 8) (h.takeLast(2).toIntOrNull(16) ?: 255) else 255
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = alpha / 255f,
    )
}

/** 把 Color 转成 "#RRGGBB" 字符串。 */
fun Color.toHex(): String =
    "#%02X%02X%02X".format(
        ((red * 255).toInt().coerceIn(0, 255)),
        ((green * 255).toInt().coerceIn(0, 255)),
        ((blue * 255).toInt().coerceIn(0, 255)),
    )