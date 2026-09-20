package com.aria2.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aria2_prefs")

/** 持久化界面/下载偏好。下载任务元数据由 [com.aria2.mobile.service.DownloadEngine] 另行持久化。 */
class SettingsStore(private val context: Context) {

    private val keyKeepOn = booleanPreferencesKey("keep_screen_on")
    private val keyUserAgent = stringPreferencesKey("download_user_agent")

    val keepScreenOn: Flow<Boolean> =
        context.dataStore.data.map { p -> p[keyKeepOn] ?: false }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { p -> p[keyKeepOn] = value }
    }

    /** 下载请求使用的 User-Agent；为空用默认。 */
    val userAgent: Flow<String> =
        context.dataStore.data.map { p -> p[keyUserAgent] ?: "" }

    suspend fun setUserAgent(value: String) {
        context.dataStore.edit { p -> p[keyUserAgent] = value.trim() }
    }

    /** 当前生效的 UA：用户未设置时返回默认值。 */
    suspend fun currentUserAgent(default: String = DEFAULT_UA): String =
        userAgent.first().trim().ifBlank { default }
}

const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android) OkHttp/aria2-mobile"