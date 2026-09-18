package com.aria2.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aria2_prefs")

/** 持久化存储：新策略下只需保留少量界面偏好。下载任务由 DownloadEngine 自行持久化。 */
class SettingsStore(private val context: Context) {

    private val keyKeepOn = booleanPreferencesKey("keep_screen_on")

    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { p -> p[keyKeepOn] ?: false }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { p -> p[keyKeepOn] = value }
    }
}