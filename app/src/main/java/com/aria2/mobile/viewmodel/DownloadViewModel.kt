package com.aria2.mobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aria2.mobile.data.DownloadItem
import com.aria2.mobile.data.DownloadStatus
import com.aria2.mobile.data.SettingsStore
import com.aria2.mobile.service.DownloadEngine
import com.aria2.mobile.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val serviceOn: Boolean = false,
    val keepScreenOn: Boolean = false,
    val downloads: List<DownloadItem> = emptyList(),
    val lastError: String? = null,
    val userAgent: String = "",
    val logs: List<String> = emptyList(),
    // 聚合统计
    val totalSpeed: Long = 0,
    val activeCount: Int = 0,
    val waitingCount: Int = 0,
    val completeCount: Int = 0,
    val errorCount: Int = 0,
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // 服务开关：前台服务已启动则置为 on
        viewModelScope.launch {
            combine(DownloadEngine.running, settings.keepScreenOn) { on, keep ->
                on to keep
            }.collect { (on, keep) ->
                _state.update {
                    it.copy(serviceOn = on, keepScreenOn = keep)
                }
            }
        }
        // 任务列表：引擎推送实时进度
        viewModelScope.launch {
            DownloadEngine.items.collect { list ->
                _state.update {
                    it.copy(
                        downloads = list,
                        totalSpeed = list.sumOf { d -> if (d.status == DownloadStatus.Active) d.downloadSpeed else 0L },
                        activeCount = list.count { d -> d.status == DownloadStatus.Active },
                        waitingCount = list.count { d -> d.status == DownloadStatus.Waiting || d.status == DownloadStatus.Paused },
                        completeCount = list.count { d -> d.status == DownloadStatus.Complete },
                        errorCount = list.count { d -> d.status == DownloadStatus.Error },
                    )
                }
            }
        }
        // UA 设置
        viewModelScope.launch {
            settings.userAgent.collect { ua ->
                _state.update { it.copy(userAgent = ua) }
            }
        }
        // 日志缓冲
        viewModelScope.launch {
            AppLogger.buffer.collect { logs ->
                _state.update { it.copy(logs = logs) }
            }
        }
    }

    // ---- 下载入口 ----

    /** 手动输入/链接监听唤入：把链接交给引擎下载。 */
    fun addDownload(url: String, filename: String = "") {
        if (url.isBlank()) return
        val gid = DownloadEngine.add(url.trim(), filename.trim())
        if (gid.isBlank()) {
            _state.update { it.copy(lastError = "添加下载失败") }
        }
    }

    // ---- 任务控制 ----

    fun toggle(item: DownloadItem) {
        if (item.status == DownloadStatus.Active) DownloadEngine.pause(item.gid)
        else DownloadEngine.resume(item.gid)
    }

    fun pause(gid: String) = DownloadEngine.pause(gid)
    fun resume(gid: String) = DownloadEngine.resume(gid)
    fun remove(gid: String) = DownloadEngine.remove(gid)
    fun clearCompleted() = DownloadEngine.clearCompleted()

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { settings.setKeepScreenOn(value) }
    }

    fun setUserAgent(value: String) {
        viewModelScope.launch { settings.setUserAgent(value) }
    }

    /** 将 UA 恢复为默认值。 */
    fun resetUserAgent() {
        viewModelScope.launch { settings.setUserAgent("") }
    }

    fun clearLogs() {
        AppLogger.clear()
    }

    fun shareLogs() {
        val ctx = getApplication<Application>()
        AppLogger.share(ctx)
    }

    fun clearError() = _state.update { it.copy(lastError = null) }
}