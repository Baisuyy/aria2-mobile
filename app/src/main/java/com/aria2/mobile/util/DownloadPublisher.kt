package com.aria2.mobile.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把下载完成后的文件发布到系统公共「下载」目录。
 *
 * 由于 targetSdk 34 启用作用域存储，无法直接以 File 写入公共目录，
 * 这里通过 MediaStore.Downloads（API 29+）插入媒体项并写入字节，
 * 完成后自动出现在系统「下载」App 与相册扫描结果里。
 */
object DownloadPublisher {

    data class Published(val name: String, val path: String)

    /**
     * 发布 source 文件到公共下载目录。
     * @return 成功返回 [Published]；失败返回 null（调用方回退到私有目录）。
     */
    fun publish(context: Context, source: File, name: String): Published? {
        val safeName = sanitize(name)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(safeName))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: run { resolver.delete(uri, null, null); return null }

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            val path = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), safeName).absolutePath
            return Published(safeName, path)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace("/", "_").replace("\\", "_").replace(":", "_")
            .replace("?", "_").replace("*", "_").replace("\"", "_").trim()
        return cleaned.ifBlank { "download" }
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "flv", "webm", "wmv", "mpg", "mpeg", "3gp" ->
                "video/*"
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus" -> "audio/*"
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "image/*"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "pdf" -> "application/pdf"
            "torrent" -> "application/x-bittorrent"
            else -> "application/octet-stream"
        }
    }
}