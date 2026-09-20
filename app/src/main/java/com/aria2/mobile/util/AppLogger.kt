package com.aria2.mobile.util

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志系统。
 *
 * - 双写入：同时写 logcat（辅助抓取）与内部文件 logs/aria2.log（持久）；
 * - 文件达到上限后保留最近一半，避免无限增长；
 * - 内存里维护一个环形缓冲区，供设置页实时查看/复制/清空。
 */
object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_BUFFER = 400            // 内存保留的最大行数
    private const val MAX_FILE_BYTES = 512 * 1024 // 日志文件上限 512KB

    private lateinit var appContext: Context
    private val _buffer = MutableStateFlow<List<String>>(emptyList())
    val buffer: StateFlow<List<String>> = _buffer.asStateFlow()

    private val lock = Any()
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        logFile().let { it.parentFile?.mkdirs() }
    }

    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String) = log("E", tag, msg)

    fun size(): Int = synchronized(lock) { _buffer.value.size }

    /** 清空内存缓冲区与日志文件。 */
    fun clear() {
        synchronized(lock) {
            _buffer.value = emptyList()
            runCatching { logFile().writeText("") }
        }
    }

    /** 取当前日志全文（用于复制/分享）。 */
    fun text(): String = synchronized(lock) {
        if (_buffer.value.isEmpty()) "（暂无日志）"
        else _buffer.value.joinToString("\n")
    }

    fun share(context: Context) {
        val text = "【aria2 Mobile 日志】\n${text()}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "分享日志")) }
    }

    private fun log(level: String, tag: String, msg: String) {
        val line = "${timeFmt.format(Date())} $level $tag: $msg"
        // logcat 同步一份，便于不再打开设置页也能抓
        if (level == "E") Log.e(tag, msg)
        else if (level == "W") Log.w(tag, msg)
        else Log.d(tag, msg)
        synchronized(lock) {
            _buffer.update { list ->
                (list + line).takeLast(MAX_BUFFER)
            }
            runCatching { appendLine(line) }
        }
    }

    private fun appendLine(line: String) {
        val f = logFile()
        if (f.length() > MAX_FILE_BYTES) {
            // 截断：保留后一半字节
            RandomAccessFile(f, "rw").use { raf ->
                val keep = (f.length() / 2).toLong()
                raf.seek(keep)
                val rest = ByteArray((f.length() - keep).toInt())
                raf.readFully(rest)
                raf.seek(0)
                raf.setLength(0)
                raf.write(rest)
            }
        }
        val raf = RandomAccessFile(f, "rw")
        raf.seek(f.length())
        raf.write((line + "\n").toByteArray(Charsets.UTF_8))
        raf.close()
    }

    private fun logFile(): File = File(appContext.filesDir, "logs/aria2.log")
}