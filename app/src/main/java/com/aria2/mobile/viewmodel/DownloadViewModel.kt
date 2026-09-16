package com.aria2.mobile.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aria2.mobile.data.Aria2Client
import com.aria2.mobile.data.Aria2Server
import com.aria2.mobile.data.DownloadItem
import com.aria2.mobile.data.DownloadPrefs
import com.aria2.mobile.data.DownloadStatus
import com.aria2.mobile.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class UiState(
    val server: Aria2Server = Aria2Server(),
    val prefs: DownloadPrefs = DownloadPrefs(),
    val pollInterval: Int = 2,
    val keepScreenOn: Boolean = false,
    val embeddedEnabled: Boolean = true,
    val connection: ConnectionState = ConnectionState.Idle,
    val downloads: List<DownloadItem> = emptyList(),
    val lastError: String? = null,
    // 聚合统计
    val totalSpeed: Long = 0,
    val activeCount: Int = 0,
    val waitingCount: Int = 0,
    val completeCount: Int = 0,
    val errorCount: Int = 0,
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val client = Aria2Client()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val knownGids = CopyOnWriteArrayList<String>()
    private var pollJob: Job? = null
    private val pollMutex = kotlinx.coroutines.sync.Mutex()
    private var hasConnectedOnce = false
    private var consecutiveFailures = 0

    init {
        viewModelScope.launch {
            settings.server.collect { s ->
                _state.update { it.copy(server = s) }
                bumpPolling()
            }
        }
        viewModelScope.launch {
            settings.prefs.collect { p -> _state.update { it.copy(prefs = p) } }
        }
        viewModelScope.launch {
            settings.pollInterval.collect { i ->
                _state.update { it.copy(pollInterval = i) }
                bumpPolling()
            }
        }
        viewModelScope.launch {
            settings.keepScreenOn.collect { k -> _state.update { it.copy(keepScreenOn = k) } }
        }
        viewModelScope.launch {
            settings.embeddedEnabled.collect { e -> _state.update { it.copy(embeddedEnabled = e) } }
        }
        viewModelScope.launch {
            settings.knownGids.collect { gids ->
                knownGids.clear()
                knownGids.addAll(gids)
                bumpPolling()
            }
        }
    }

    // ---- 配置写入 ----
    fun saveServer(rpcUrl: String, secret: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            settings.setServer(rpcUrl, secret)
            onDone()
        }
    }

    fun savePrefs(dir: String, extraOptions: String) {
        viewModelScope.launch { settings.setPrefs(dir, extraOptions) }
    }

    fun setPollInterval(seconds: Int) {
        viewModelScope.launch { settings.setPollInterval(seconds) }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { settings.setKeepScreenOn(value) }
    }

    fun setEmbeddedEnabled(value: Boolean) {
        viewModelScope.launch { settings.setEmbeddedEnabled(value) }
    }

    // ---- 新增下载 ----
    fun addDownload(url: String, filename: String = "", optionsRaw: String = "") {
        if (url.isBlank()) return
        viewModelScope.launch {
            val server = _state.value.server
            if (!server.isConfigured) {
                fail("尚未配置 aria2 服务器，请先在设置中填写 RPC 地址")
                return@launch
            }
            updateConnecting()
            val base = _state.value.prefs.optionsMap()
            val opt = buildMap {
                putAll(base)
                if (filename.isNotBlank()) put("out", filename)
                optionsRaw.lineSequence().map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { line ->
                        val idx = line.indexOf('=')
                        if (idx > 0) put(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
                    }
            }
            try {
                val gid = client.addUri(server, url, opt)
                track(gid)
                _state.update { it.copy(connection = ConnectionState.Connected, lastError = null) }
            } catch (e: Exception) {
                fail(e.message ?: "添加下载失败")
            }
        }
    }

    /** 由链接监听唤入：直接把解析出的链接加入下载。 */
    fun recordLink(url: String, optionsRaw: String = "") {
        addDownload(url, optionsRaw = optionsRaw)
    }

    // ---- 任务控制 ----
    fun pause(gid: String) = control { client.pause(_state.value.server, gid) }
    fun unpause(gid: String) = control { client.unpause(_state.value.server, gid) }
    fun remove(gid: String) {
        viewModelScope.launch {
            try {
                client.remove(_state.value.server, gid)
            } catch (_: Exception) { /* 服务端已不存在 */ }
            untrack(gid)
        }
    }

    private fun control(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                fail(e.message ?: "操作失败")
            }
        }
    }

    // ---- 状态管理 ----
    fun clearError() = _state.update { it.copy(lastError = null) }

    private fun track(gid: String) {
        if (gid.isBlank()) return
        if (!knownGids.contains(gid)) {
            knownGids.add(gid)
            persistGids()
        }
    }

    private fun untrack(gid: String) {
        knownGids.remove(gid)
        persistGids()
    }

    private fun persistGids() {
        viewModelScope.launch { settings.setKnownGids(knownGids.toList()) }
    }

    private fun bumpPolling() {
        pollJob?.cancel()
        startPolling()
    }

    private fun startPolling() {
        pollJob = viewModelScope.launch {
            val intervalMs = (_state.value.pollInterval.coerceAtLeast(1) * 1000L).coerceAtLeast(1000L)
            // 保证至少一次立即刷新
            refreshOnce()
            while (isActive) {
                delay(intervalMs)
                refreshOnce()
            }
        }
    }

    private suspend fun refreshOnce() {
        val s = _state.value
        val server = s.server
        if (!server.isConfigured) {
            _state.update { it.copy(connection = ConnectionState.Idle) }
            return
        }
        // 用互斥锁避免轮询与手动刷新重入
        if (!pollMutex.tryLock()) return
        try {
            val items = LinkedHashMap<String, DownloadItem>()
            for (gid in knownGids) {
                val item = client.tellStatus(server, gid)
                if (item != null) items[gid] = item
            }
            hasConnectedOnce = true
            consecutiveFailures = 0
            val list = items.values.toList()
            _state.update {
                it.copy(
                    connection = ConnectionState.Connected,
                    downloads = list,
                    lastError = null,
                    totalSpeed = list.sumOf { d -> if (d.status == DownloadStatus.Active) d.downloadSpeed else 0L },
                    activeCount = list.count { d -> d.status == DownloadStatus.Active },
                    waitingCount = list.count { d -> d.status == DownloadStatus.Waiting || d.status == DownloadStatus.Paused },
                    completeCount = list.count { d -> d.status == DownloadStatus.Complete },
                    errorCount = list.count { d -> d.status == DownloadStatus.Error },
                )
            }
        } catch (e: Exception) {
            // 偶发网络抖动不打断已建立的连接；连续失败或从未成功过才进入 Error
            consecutiveFailures++
            val transient = hasConnectedOnce && consecutiveFailures < 3
            val conn = if (transient) ConnectionState.Connected
            else ConnectionState.Error(e.message ?: "无法连接服务器")
            Log.w("Aria2", "refresh failed (${consecutiveFailures})", e)
            _state.update { it.copy(connection = conn) }
        } finally {
            pollMutex.unlock()
        }
    }

    private fun updateConnecting() {
        _state.update { it.copy(connection = ConnectionState.Connecting) }
    }

    private fun fail(msg: String) {
        _state.update { it.copy(connection = ConnectionState.Error(msg), lastError = msg) }
    }
}