package com.aria2.mobile.data

/** aria2 下载任务状态，映射自 aria2 的状态字符串。 */
enum class DownloadStatus(val aria2State: String) {
    Active("active"),
    Waiting("waiting"),
    Paused("paused"),
    Error("error"),
    Complete("complete"),
    Removed("removed");

    val isDone: Boolean
        get() = this == Complete || this == Removed
}

/**
 * 单个下载任务。字段与 aria2.tellStatus 的核心字段对应，
 * 后端轮询时用其补齐进度、速度等实时信息。
 */
data class DownloadItem(
    val gid: String,
    val uri: String,
    val filename: String,
    val directory: String,
    val status: DownloadStatus,
    val totalLength: Long = 0,
    val completedLength: Long = 0,
    val downloadSpeed: Long = 0,
    val errorMessage: String? = null,
) {
    val progress: Float
        get() = if (totalLength <= 0) 0f else (completedLength.toFloat() / totalLength).coerceIn(0f, 1f)

    /** 展示用名称：优先文件名，退化为链接尾部。 */
    fun displayName(): String =
        filename.takeIf { it.isNotBlank() } ?: uri.substringAfterLast("/").takeIf { it.isNotBlank() } ?: uri
}