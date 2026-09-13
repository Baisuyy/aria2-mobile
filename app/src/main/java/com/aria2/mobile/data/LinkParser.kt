package com.aria2.mobile.data

import android.content.Intent
import android.net.Uri
import java.net.URI

/**
 * 链接监听解析器：把“打开链接 / 分享文字 / 自定义 scheme”的 Intent 统一解析成一个可下载链接。
 *
 * 支持的来源：
 *  - ACTION_VIEW + http/https 数据
 *  - ACTION_VIEW + 字符串数据（纯文本链接）
 *  - ACTION_SEND + EXTRA_TEXT（分享）
 *  - aria2://<urlencoded 链接>
 */
object LinkParser {

    /** 从 Intent 中提取链接；无有效链接时返回 null。 */
    fun parse(intent: Intent): String? {
        val action = intent.action ?: return null
        return when (action) {
            Intent.ACTION_SEND -> fromShareText(intent)
            Intent.ACTION_VIEW -> fromView(intent)
            else -> null
        }
    }

    private fun fromShareText(intent: Intent): String? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return extractUrl(text) ?: (text.trim().takeIf { it.isNotEmpty() })
    }

    private fun fromView(intent: Intent): String? {
        val scheme = intent.scheme ?: return null
        when (scheme) {
            "aria2" -> return intent.dataString?.substringAfter("aria2://", "")?.let { uriDecode(it) }
            "http", "https" -> return intent.dataString
            else -> {
                // 字符串形式的数据（text/plain 过滤）
                val str = intent.dataString ?: return null
                return str.takeIf { it.startsWith("http", ignoreCase = true) || it.startsWith("magnet:") }
            }
        }
    }

    /** 从一段文本（可能是分享文案）里提取第一个形如链接的子串。 */
    private fun extractUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("magnet:", ignoreCase = true)) {
            val firstSpace = trimmed.indexOfAny(charArrayOf(' ', '\n', '\r', '\t'))
            return if (firstSpace > 0) trimmed.substring(0, firstSpace) else trimmed
        }
        return null
    }

    private fun uriDecode(value: String): String = try {
        Uri.decode(value)
    } catch (_: Exception) {
        value
    }
}