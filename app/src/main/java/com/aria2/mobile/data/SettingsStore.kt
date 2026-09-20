package com.aria2.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

    // 外观
    private val keyThemeMode = stringPreferencesKey("theme_mode")          // system/light/dark
    private val keyPrimaryColor = stringPreferencesKey("theme_primary")    // hex, "" = 默认
    private val keyTextColor = stringPreferencesKey("theme_text")          // hex, "" = 默认
    private val keyBgImage = stringPreferencesKey("theme_bg_image")        // content uri, "" = 无
    private val keyOverlayAlpha = floatPreferencesKey("theme_overlay_alpha")

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

    // ---------- 外观 ----------
    val themeMode: Flow<String> = context.dataStore.data.map { p -> p[keyThemeMode] ?: "system" }
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { p -> p[keyThemeMode] = mode }
    }

    val primaryColor: Flow<String> = context.dataStore.data.map { p -> p[keyPrimaryColor] ?: "" }
    suspend fun setPrimaryColor(hex: String) {
        context.dataStore.edit { p -> p[keyPrimaryColor] = hex }
    }

    val textColor: Flow<String> = context.dataStore.data.map { p -> p[keyTextColor] ?: "" }
    suspend fun setTextColor(hex: String) {
        context.dataStore.edit { p -> p[keyTextColor] = hex }
    }

    val bgImage: Flow<String> = context.dataStore.data.map { p -> p[keyBgImage] ?: "" }
    suspend fun setBgImage(uri: String) {
        context.dataStore.edit { p -> p[keyBgImage] = uri }
    }

    val overlayAlpha: Flow<Float> = context.dataStore.data.map { p -> p[keyOverlayAlpha] ?: 0f }
    suspend fun setOverlayAlpha(value: Float) {
        context.dataStore.edit { p -> p[keyOverlayAlpha] = value }
    }
}

const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android) OkHttp/aria2-mobile"