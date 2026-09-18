package com.aria2.mobile.service

import android.content.Context
import android.util.Log
import com.aria2.mobile.data.DownloadItem
import com.aria2.mobile.data.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 原生下载引擎。按照"只开一个服务监听下载请求"的策略实现：
 *
 * - 不再嵌入式 aria2c 二进制、不再走 aria2 JSON-RPC；
 * - 前台服务持有本引擎，监听系统链接/内置浏览器/手动输入发来的下载请求；
 * - 引擎直接用 OkHttp 分块下载，支持 Range 断点续传（.part 临时文件）；
 * - 任务元数据与实时进度以 StateFlow 暴露给 UI，全程单 APK、无 ABI 拆包。
 */
object DownloadEngine {

    private const val TAG = "DownloadEngine"
    private const val CHUNK = 64 * 1024

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private class Task(
        var item: DownloadItem,
        @Volatile var job: Job? = null,
        @Volatile var cancelRequested: Boolean = false,
        @Volatile var paused: Boolean = false,
    )

    private val tasks = ConcurrentHashMap<String, Task>()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val persistMutex = Mutex()

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        restore()
    }

    private fun downloadsDir(): File =
        File(appContext.getExternalFilesDir(null), "Download").apply { mkdirs() }

    private fun metaFile() = File(appContext.filesDir, "downloads.json")

    // ---- 对外 API：下载请求入口 ----

    /** 新增下载任务，返回 gid；立即启动。 */
    fun add(url: String, filename: String = ""): String {
        val gid = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val item = DownloadItem(
            gid = gid,
            uri = url,
            filename = filename,
            directory = downloadsDir().absolutePath,
            status = DownloadStatus.Waiting,
        )
        tasks[gid] = Task(item = item)
        publish()
        persist()
        if (_running.value) startTask(gid)
        return gid
    }

    fun pause(gid: String) {
        val task = tasks[gid] ?: return
        task.paused = true
        task.cancelRequested = true
        task.job?.cancel()
        setStatus(gid, DownloadStatus.Paused)
    }

    fun resume(gid: String) {
        val task = tasks[gid] ?: return
        if (task.item.status == DownloadStatus.Complete) return
        task.paused = false
        task.cancelRequested = false
        setStatus(gid, DownloadStatus.Waiting)
        if (_running.value) startTask(gid)
    }

    fun remove(gid: String) {
        val task = tasks.remove(gid) ?: return
        task.job?.cancel()
        partialFileOf(task.item).delete()
        publish()
        persist()
    }

    fun clearCompleted() {
        tasks.entries.removeIf { (_, t) -> t.item.status == DownloadStatus.Complete }
        publish()
        persist()
    }

    /** 服务生命周期：开始听下载请求（含恢复未完成任务）。 */
    fun startListening() {
        if (_running.value) return
        _running.value = true
        // 恢复之前未完成的任务
        tasks.values.filter { t ->
            t.item.status == DownloadStatus.Waiting ||
                t.item.status == DownloadStatus.Active ||
                t.item.status == DownloadStatus.Paused
        }.forEach { startTask(it.item.gid) }
    }

    /** 服务停止：停掉所有进行中的下载（保留 .part，下次续传）。 */
    fun stopListening() {
        _running.value = false
        tasks.values.forEach { t ->
            t.job?.cancel()
            t.job = null
        }
    }

    fun shutdown() {
        stopListening()
        scope.cancel()
    }

    // ---- 任务调度 ----

    private fun startTask(gid: String) {
        val task = tasks[gid] ?: return
        if (task.job?.isActive == true) return
        if (task.item.status == DownloadStatus.Complete) return
        setStatus(gid, DownloadStatus.Active)
        task.cancelRequested = false
        task.job = scope.launch(Dispatchers.IO) { performDownload(task) }
    }

    private suspend fun performDownload(task: Task) {
        val gid = task.item.gid
        val part = partialFileOf(task.item)
        try {
            val startByte = part.length()
            val request = Request.Builder().url(task.item.uri)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) OkHttp/aria2-mobile")
                .apply { if (startByte > 0) header("Range", "bytes=$startByte-") }
                .build()

            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    // 服务端不支持 Range 时（200），从头开始
                    if (resp.code == 200) { /* 继续用 200 逻辑 */ }
                    else throw IOException("HTTP ${resp.code} ${resp.message}")
                }

                val body = resp.body ?: throw IOException("空响应")
                val totalLength = when {
                    resp.code == 206 -> resp.header("Content-Range")
                        ?.substringAfter("/")
                        ?.toLongOrNull() ?: (startByte + body.contentLength())
                    else -> body.contentLength()
                }.takeIf { it > 0 } ?: 0L

                // 200 全量返回时，若已有 .part 需废弃
                if (resp.code == 200 && startByte > 0) {
                    part.delete()
                }
                val output = FileOutputStream(part, true)

                var completed = if (resp.code == 206) startByte else 0L
                updateTask(gid) { it.copy(
                    totalLength = totalLength,
                    completedLength = completed,
                    downloadSpeed = 0,
                    status = DownloadStatus.Active,
                ) }

                val buffer = ByteArray(CHUNK)
                var lastSpeedAt = System.currentTimeMillis()
                var lastBytes = completed
                try {
                    output.use { out ->
                        body.byteStream().use { input ->
                            while (scope.isActive && !task.paused) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                completed += read

                                val now = System.currentTimeMillis()
                                if (now - lastSpeedAt >= 800) {
                                    val dt = ((now - lastSpeedAt) / 1000).coerceAtLeast(1)
                                    val speed = (completed - lastBytes) / dt
                                    lastSpeedAt = now
                                    lastBytes = completed
                                    updateTask(gid) {
                                        it.copy(
                                            completedLength = completed,
                                            downloadSpeed = speed,
                                            totalLength = totalLength,
                                            status = DownloadStatus.Active,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    try { body.close() } catch (_: Exception) {}
                }

                if (task.paused) {
                    setStatus(gid, DownloadStatus.Paused)
                    return@use
                }
                if (!scope.isActive) return@use

                // 校验完成度
                val finalCompleted = part.length()
                val done = totalLength <= 0 || finalCompleted >= totalLength
                if (done) {
                    val finalFile = finalFileOf(task.item)
                    part.renameTo(finalFile)
                    updateTask(gid) {
                        it.copy(
                            completedLength = if (totalLength > 0) totalLength else finalCompleted,
                            totalLength = if (totalLength > 0) totalLength else finalCompleted,
                            status = DownloadStatus.Complete,
                            downloadSpeed = 0,
                            filename = finalFile.name,
                        )
                    }
                    persist()
                } else {
                    setStatus(gid, DownloadStatus.Waiting)
                }
            }
        } catch (e: Exception) {
            if (task.paused || !scope.isActive) {
                if (task.paused) setStatus(gid, DownloadStatus.Paused)
            } else {
                Log.w(TAG, "download failed: ${task.item.uri}", e)
                setStatus(gid, DownloadStatus.Error, e.message ?: "下载失败")
                persist()
            }
        } finally {
            task.job = null
        }
    }

    // ---- 状态更新 ----

    private fun setStatus(gid: String, status: DownloadStatus, error: String? = null) {
        updateTask(gid) {
            it.copy(
                status = status,
                errorMessage = error,
                downloadSpeed = if (status == DownloadStatus.Active) it.downloadSpeed else 0,
            )
        }
    }

    private fun updateTask(gid: String, transform: (DownloadItem) -> DownloadItem) {
        val task = tasks[gid] ?: return
        task.item = transform(task.item)
        publish()
    }

    private fun publish() {
        _items.value = tasks.values.map { it.item }
    }

    // ---- 持久化 ----

    private fun persist() {
        val scope = scope
        scope.launch { persistMutex.withLock { writeMetaLocked() } }
    }

    private fun writeMetaLocked() {
        try {
            val arr = JSONArray()
            tasks.values.forEach { t ->
                val i = t.item
                arr.put(JSONObject().apply {
                    put("gid", i.gid)
                    put("uri", i.uri)
                    put("filename", i.filename)
                    put("directory", i.directory)
                    put("status", i.status.name)
                    put("total", i.totalLength)
                    put("completed", i.completedLength)
                })
            }
            metaFile().writeText(JSONObject().put("tasks", arr).toString())
        } catch (e: Exception) {
            Log.w(TAG, "persist failed", e)
        }
    }

    private fun restore() {
        try {
            if (!metaFile().exists()) return
            val root = JSONObject(metaFile().readText())
            val arr = root.optJSONArray("tasks") ?: return
            for (j in 0 until arr.length()) {
                val o = arr.getJSONObject(j)
                var status = runCatching { DownloadStatus.valueOf(o.optString("status")) }
                    .getOrDefault(DownloadStatus.Waiting)
                val item = DownloadItem(
                    gid = o.optString("gid"),
                    uri = o.optString("uri"),
                    filename = o.optString("filename"),
                    directory = o.optString("directory").ifBlank { downloadsDir().absolutePath },
                    status = status,
                    totalLength = o.optLong("total"),
                    completedLength = o.optLong("completed"),
                )
                // 完成的任务：若最终文件仍存在则保持 Complete
                if (status == DownloadStatus.Complete && !finalFileOf(item).exists()) {
                    status = DownloadStatus.Waiting
                }
                tasks[item.gid] = Task(item = item)
            }
            publish()
        } catch (e: Exception) {
            Log.w(TAG, "restore failed", e)
        }
    }

    private fun partialFileOf(item: DownloadItem): File {
        val name = item.filename.ifBlank { deriveName(item.uri) }
        return File(item.directory, "$name.part")
    }

    private fun finalFileOf(item: DownloadItem): File {
        val name = item.filename.ifBlank { deriveName(item.uri) }
        return File(item.directory, name)
    }

    private fun deriveName(uri: String): String {
        val clean = uri.substringBefore("?").substringBefore("#")
        val seg = clean.substringAfterLast('/')
        return seg.takeIf { it.isNotBlank() && it.contains('.') } ?: "download"
    }
}