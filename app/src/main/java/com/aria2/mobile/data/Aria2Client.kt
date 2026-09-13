package com.aria2.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * aria2 JSON-RPC 客户端。所有方法均为挂起函数，网络操作在 IO 线程执行。
 */
class Aria2Client(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private var idCounter = 0

    /** 测试连通性并返回 aria2 版本。 */
    suspend fun getVersion(server: Aria2Server): String = withContext(Dispatchers.IO) {
        val res = callRaw(server, "aria2.getVersion")
        res.optJSONObject("result")?.optString("version") ?: ""
    }

    /** 新增下载，返回 gid。 */
    suspend fun addUri(
        server: Aria2Server,
        uri: String,
        options: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val uris = JSONArray().put(uri)
        val optionsJson = JSONObject(options)
        val params = buildParams(server, uris, optionsJson)
        callRaw(server, "aria2.addUri", params).optString("result")
    }

    /** 查询单个任务状态。 */
    suspend fun tellStatus(server: Aria2Server, gid: String): DownloadItem? =
        withContext(Dispatchers.IO) {
            val params = buildParams(server, gid)
            val res = callRaw(server, "aria2.tellStatus", params)
            val result = res.opt("result") as? JSONObject ?: return@withContext null
            parseTask(result)
        }

    suspend fun pause(server: Aria2Server, gid: String) =
        withContext(Dispatchers.IO) { callRaw(server, "aria2.pause", buildParams(server, gid)) }

    suspend fun unpause(server: Aria2Server, gid: String) =
        withContext(Dispatchers.IO) { callRaw(server, "aria2.unpause", buildParams(server, gid)) }

    suspend fun remove(server: Aria2Server, gid: String) =
        withContext(Dispatchers.IO) { callRaw(server, "aria2.remove", buildParams(server, gid)) }

    /** 查询统计信息，可用于判断服务器是否可达。 */
    suspend fun getGlobalStat(server: Aria2Server): JSONObject =
        withContext(Dispatchers.IO) {
            callRaw(server, "aria2.getGlobalStat").optJSONObject("result") ?: JSONObject()
        }

    // ---- 内部实现 ----

    private fun buildParams(server: Aria2Server, vararg params: Any): JSONArray {
        val array = JSONArray()
        if (server.secret.isNotBlank()) array.put("token:${server.secret}")
        params.forEach { array.put(it) }
        return array
    }

    private suspend fun callRaw(server: Aria2Server, method: String, params: Any = JSONArray()): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", (++idCounter).toString())
                .put("method", method)
                .put("params", params)
            val request = Request.Builder()
                .url(server.rpcUrl)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = if (body.isBlank()) JSONObject() else JSONObject(body)
                val error = json.optJSONObject("error")
                if (error != null) {
                    val msg = error.optString("message")
                    throw RuntimeException("aria2 RPC 错误：$msg")
                }
                json
            }
        }

    private fun parseTask(o: JSONObject): DownloadItem {
        val gid = o.optString("gid")
        val files = o.optJSONArray("files")
        val firstFile = files?.optJSONObject(0)
        val uris = firstFile?.optJSONArray("uris")
        val uri = uris?.optJSONObject(0)?.optString("uri") ?: ""
        val status = DownloadStatus.entries.firstOrNull {
            it.aria2State == o.optString("status")
        } ?: DownloadStatus.Error
        return DownloadItem(
            gid = gid,
            uri = uri,
            filename = firstFile?.optString("path")?.substringAfterLast('/') ?: "",
            directory = firstFile?.optString("path")?.substringBeforeLast('/') ?: "",
            status = status,
            totalLength = o.optLong("totalLength"),
            completedLength = o.optLong("completedLength"),
            downloadSpeed = o.optLong("downloadSpeed"),
            errorMessage = o.optString("errorMessage").ifBlank { null },
        )
    }
}