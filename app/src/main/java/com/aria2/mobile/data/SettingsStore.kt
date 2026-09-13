package com.aria2.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "aria2_prefs")

/** 持久化存储：aria2 服务器配置、下载偏好与界面设置。 */
class SettingsStore(private val context: Context) {

    private val keyRpc = stringPreferencesKey("rpc_url")
    private val keySecret = stringPreferencesKey("rpc_secret")
    private val keyDir = stringPreferencesKey("default_dir")
    private val keyOptions = stringPreferencesKey("default_options")
    private val keyPoll = intPreferencesKey("poll_interval")
    private val keyKeepOn = booleanPreferencesKey("keep_screen_on")
    private val keyKnownGids = stringPreferencesKey("known_gids")

    /** 已跟踪的 gid 列表（JSON 数组字符串），便于跨重启恢复。 */
    val knownGids: Flow<List<String>> = context.dataStore.data.map { p ->
        val raw = p[keyKnownGids].orEmpty()
        if (raw.isBlank()) emptyList()
        else try {
            (JSONArray(raw)).let { a -> (0 until a.length()).map { a.getString(it) } }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun setKnownGids(gids: List<String>) {
        context.dataStore.edit { p ->
            val arr = JSONArray(gids)
            p[keyKnownGids] = arr.toString()
        }
    }

    val server: Flow<Aria2Server> = context.dataStore.data.map { p ->
        Aria2Server(
            rpcUrl = p[keyRpc] ?: "http://127.0.0.1:6800/jsonrpc",
            secret = p[keySecret].orEmpty(),
        )
    }

    val prefs: Flow<DownloadPrefs> = context.dataStore.data.map { p ->
        DownloadPrefs(
            dir = p[keyDir].orEmpty(),
            extraOptions = p[keyOptions].orEmpty(),
        )
    }

    val pollInterval: Flow<Int> = context.dataStore.data.map { p -> p[keyPoll] ?: 2 }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { p -> p[keyKeepOn] ?: false }

    suspend fun setServer(rpcUrl: String, secret: String) {
        context.dataStore.edit { p ->
            p[keyRpc] = rpcUrl.trim()
            p[keySecret] = secret.trim()
        }
    }

    suspend fun setPrefs(dir: String, extraOptions: String) {
        context.dataStore.edit { p ->
            p[keyDir] = dir.trim()
            p[keyOptions] = extraOptions.trim()
        }
    }

    suspend fun setPollInterval(seconds: Int) {
        context.dataStore.edit { p -> p[keyPoll] = seconds.coerceIn(1, 60) }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { p -> p[keyKeepOn] = value }
    }
}