package com.aria2.mobile.ui

import java.util.Locale

/** 把字节数格式化为人类可读的大小。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var u = 0
    while (v >= 1024 && u < units.lastIndex) {
        v /= 1024
        u++
    }
    return String.format(Locale.ROOT, "%.1f %s", v, units[u])
}

/** 把速度字节每秒格式化为可读速率。 */
fun formatSpeed(bytesPerSec: Long): String =
    if (bytesPerSec <= 0) "0 B/s" else "${formatBytes(bytesPerSec)}/s"

/** 百分比。 */
fun formatPercent(progress: Float): String =
    if (progress <= 0f) "0%" else String.format(Locale.ROOT, "%.0f%%", progress * 100f)