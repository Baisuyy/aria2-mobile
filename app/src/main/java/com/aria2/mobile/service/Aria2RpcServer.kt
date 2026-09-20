package com.aria2.mobile.service

import android.util.Log
import com.aria2.mobile.data.DownloadStatus
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 极简 aria2 JSON-RPC 兼容监听器。
 *
 * 目的：站点/脚本会把下载请求 POST 到本机标准 aria2 地址
 * (`http://127.0.0.1:6800/jsonrpc`，见 [PORT]/[PATH])。本服务在本地回环上
 * 接收这些请求，把其中的真实直链/磁力链取出交给 [DownloadEngine.add]，从而
 * 复用现有 OkHttp 断点续传引擎下载。其余方法仅返回轻量兼容响应，让调用方认为
 * aria2 服务在线即可，不做完整仿真。
 *
 * 说明：
 * - 纯原生 [ServerSocket] + 固定线程池，零三方依赖（Android 不含 com.sun.net.httpserver）。
 * - 仅监听 loopback，不对外网开放。
 */
object Aria2RpcServer {

    private const val TAG = "Aria2RpcServer"
    private const val HOST = "127.0.0.1"
    const val PORT = 6800
    const val PATH = "/jsonrpc"
    private const val RPC_PATH = "/rpc"

    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null

    val isRunning: Boolean get() = running.get()

    /** 启动监听。幂等：已在运行则直接返回。 */
    fun start() {
        if (running.getAndSet(true)) return
        pool = Executors.newFixedThreadPool(4)
        Thread({
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(HOST, PORT))
                serverSocket = ss
                Log.i(TAG, "aria2 RPC 监听已启动: http://$HOST:$PORT$PATH")
                while (running.get()) {
                    val client = try { ss.accept() } catch (e: Exception) { break }
                    pool?.execute { handle(client) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "aria2 RPC 监听启动失败: ${e.message}")
            } finally {
                running.set(false)
            }
        }, "aria2-rpc-listener").apply { isDaemon = true }.start()
    }

    /** 停止监听并关闭线程池。 */
    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        runCatching { pool?.shutdownNow() }
        serverSocket = null
        pool = null
        Log.i(TAG, "aria2 RPC 监听已停止")
    }

    // ---- HTTP 处理 ----

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 10_000
            client.getInputStream().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                client.getOutputStream().buffered(8192).use { out ->
                    val requestLine = reader.readLine() ?: return
                    // 请求头：读取到空行（记录 Content-Length）
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) {
                            line.substringAfter(':').trim().toIntOrNull()?.let { contentLength = it }
                        }
                    }
                    val parts = requestLine.split(" ")
                    val method = parts.getOrNull(0) ?: ""
                    val rawPath = parts.getOrNull(1) ?: "/"
                    val pathname = rawPath.substringBefore('?')
                    val query = rawPath.substringAfter('?', "")

                    // CORS 预检：跨源页面 fetch 到 127.0.0.1 前必须先通过这里
                    if (method.equals("OPTIONS", ignoreCase = true)) {
                        writeEmpty(out, 204)
                        return
                    }

                    val isRpcPath = pathname == PATH || pathname == RPC_PATH

                    if (method.equals("POST", ignoreCase = true) && isRpcPath) {
                        val body = if (contentLength > 0) {
                            val buf = CharArray(contentLength)
                            var off = 0
                            while (off < contentLength) {
                                val n = reader.read(buf, off, contentLength - off)
                                if (n < 0) break
                                off += n
                            }
                            String(buf, 0, off)
                        } else ""
                        writeJson(out, dispatchText(body))
                    } else if (method.equals("GET", ignoreCase = true) && isRpcPath
                        && query.toQueryParam("method").isNotEmpty()
                    ) {
                        // GET 形式的 JSON-RPC：/?method=aria2.getVersion&id=1&params=[]
                        val paramsRaw = query.toQueryParam("params")
                        val payload = JSONObject()
                            .put("jsonrpc", "2.0")
                            .put("id", query.toQueryParam("id").ifEmpty { "1" })
                            .put("method", query.toQueryParam("method"))
                            .put("params",
                                if (paramsRaw.isBlank()) JSONArray()
                                else runCatching { JSONArray(paramsRaw) }.getOrDefault(JSONArray()))
                        writeJson(out, dispatchPayload(payload))
                    } else if (method.equals("GET", ignoreCase = true)
                        && (pathname == "/" || pathname == "/index.html" || pathname == "/status")
                    ) {
                        // 状态探测页
                        val stat = JSONObject()
                        writeJson(out, JSONObject().apply {
                            put("service", "aria2-mobile")
                            put("version", "1.36.0")
                            put("endpoint", "http://$HOST:$PORT$PATH")
                            put("globalStat", stat)
                        })
                    } else {
                        writeJson(out, JSONObject().apply {
                            put("jsonrpc", "2.0")
                            put("id", null)
                            put("error", jsonError(-32601, "Method not found"))
                        })
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "request handle error: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    /** CORS 响应头：放行任意源（本站为 loopback 抓包工具，无需凭据）。 */
    private fun corsHeaders(): String =
        "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type, Accept, X-Aria2-Token, X-Aria2-Secret, Authorization, Range\r\n" +
            "Access-Control-Max-Age: 86400\r\n"

    private fun writeEmpty(out: java.io.OutputStream, statusCode: Int) {
        val status = if (statusCode == 204) "204 No Content" else "200 OK"
        val bytes = ByteArray(0)
        val head = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: keep-alive\r\n")
            append(corsHeaders())
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        out.write(head)
        out.write(bytes)
        out.flush()
    }

    private fun writeJson(out: java.io.OutputStream, obj: JSONObject) {
        val bytes = obj.toString().toByteArray(StandardCharsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n")
            append(corsHeaders())
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        out.write(head)
        out.write(bytes)
        out.flush()
    }

    // ---- JSON-RPC 分发 ----

    private class RpcError(val code: Int, msg: String) : Exception(msg)
    private fun fail(code: Int, msg: String): Nothing = throw RpcError(code, msg)

    private fun dispatchText(body: String): JSONObject = try {
        dispatchPayload(JSONObject(body))
    } catch (e: Exception) {
        JSONObject().put("jsonrpc", "2.0").put("id", null)
            .put("error", jsonError(-32700, "Parse error: ${e.message}"))
    }

    private fun dispatchPayload(req: JSONObject): JSONObject {
        val id = req.opt("id")
        return try {
            val method = req.optString("method", "")
            if (method == "system.multicall") {
                handleMulticall(id, req.optJSONArray("params") ?: JSONArray())
            } else {
                JSONObject().put("jsonrpc", "2.0").put("id", id)
                    .put("result", handleMethod(method, req.optJSONArray("params") ?: JSONArray()))
            }
        } catch (e: RpcError) {
            JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("error", jsonError(e.code, e.message ?: "error"))
        } catch (e: Exception) {
            JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("error", jsonError(-32603, e.message ?: "Internal error"))
        }
    }

    /** 处理 system.multicall：结果每项包成 `[res]`，失败返回 faultCode/faultString（AriaNG 协议）。 */
    private fun handleMulticall(id: Any?, params: JSONArray): JSONObject {
        val results = JSONArray()
        for (i in 0 until params.length()) {
            val call = params.optJSONObject(i)
            if (call == null) { results.put(null); continue }
            try {
                results.put(JSONArray().put(
                    handleMethod(call.optString("methodName"),
                        call.optJSONArray("params") ?: JSONArray())
                ))
            } catch (e: RpcError) {
                results.put(JSONObject()
                    .put("faultCode", e.code.toString())
                    .put("faultString", e.message ?: "error"))
            } catch (e: Exception) {
                results.put(JSONObject()
                    .put("faultCode", "-32603")
                    .put("faultString", e.message ?: "Internal error"))
            }
        }
        return JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", results)
    }

    /**
     * 执行单个方法，返回结果值（可为 String/JSONObject/JSONArray/null）。
     * params 结构兼容 aria2 常见两种格式：
     *   - `[ "token", uriArray, options ]`（旧式带 token）
     *   - `[ uriArray, options ]`（新版 token 在 header）
     */
    private fun handleMethod(method: String, rawParams: JSONArray): Any {
        val params = stripToken(rawParams)
        return when (method) {
            "aria2.getVersion" -> getVersion()
            "aria2.getSessionInfo" ->
                JSONObject().put("sessionId",
                    java.util.UUID.randomUUID().toString().replace("-", ""))
            "aria2.addUri" -> addUri(params)
            "aria2.addTorrent" -> fail(1, "unsupported: use direct link")
            "aria2.getGlobalStat" -> getGlobalStat()
            "aria2.getGlobalOption" -> globalOption()
            "aria2.changeGlobalOption" -> "OK"
            "aria2.getOption" -> JSONObject()
            "aria2.changeOption" -> "OK"
            "aria2.tellActive" -> listToStatus()
            "aria2.tellWaiting" -> listToStatus()
            "aria2.tellStopped" -> listToStatus()
            "aria2.tellStatus" -> tellStatus(params)
            "aria2.getUris" -> JSONArray()
            "aria2.getFiles" -> JSONArray()
            "aria2.getPeers" -> JSONArray()
            "aria2.getServers" -> JSONArray()
            "aria2.pause" -> toggle(params, DownloadStatus.Paused)
            "aria2.forcePause" -> toggle(params, DownloadStatus.Paused)
            "aria2.unpause" -> toggle(params, DownloadStatus.Waiting)
            "aria2.pauseAll" -> {
                DownloadEngine.items.value
                    .filter { it.status == DownloadStatus.Active }
                    .forEach { DownloadEngine.pause(it.gid) }
                "OK"
            }
            "aria2.forcePauseAll" -> "OK"
            "aria2.unpauseAll" -> "OK"
            "aria2.remove" -> removeByGid(params)
            "aria2.forceRemove" -> removeByGid(params)
            "aria2.removeDownloadResult" -> "OK"
            "aria2.purgeDownloadResult" -> "OK"
            "aria2.changePosition" -> "OK"
            "aria2.changeUri" -> "OK"
            "aria2.shutdown" -> "OK"
            "aria2.forceShutdown" -> "OK"
            "aria2.saveSession" -> "OK"
            "system.listMethods" -> JSONArray().apply {
                put("aria2.addUri"); put("aria2.getVersion")
                put("aria2.getGlobalStat"); put("aria2.tellStatus")
                put("system.multicall"); put("aria2.pause"); put("aria2.unpause")
            }
            else -> fail(-32601, "Method not found: $method")
        }
    }

    /** 若 params[0] 是字符串（旧式 token/secret），剥掉它。 */
    private fun stripToken(params: JSONArray): JSONArray {
        if (params.length() > 0 && params.opt(0) is String && !looksLikeUriArray(params)) {
            val out = JSONArray()
            for (i in 1 until params.length()) out.put(params[i])
            return out
        }
        return params
    }

    /** 判断某数组是否为"URI 字符串数组"（用于区分 token 是字符串还是 uris 数组）。 */
    private fun looksLikeUriArray(params: JSONArray): Boolean {
        if (params.length() == 0) return false
        val list = params.optJSONArray(0) ?: return false
        if (list.length() == 0) return false
        val first = list.opt(0)
        if (first !is String) return false
        return first.startsWith("http") || first.startsWith("magnet") || first.startsWith("ftp")
    }

    private fun getVersion(): JSONObject = JSONObject().put(
        "version",
        JSONObject()
            .put("version", "1.36.0")
            .put("enabledFeatures", JSONArray().apply {
                put("Async DNS"); put("BitTorrent"); put("Firefox3 Cookie")
                put("GZip"); put("HTTPS"); put("Message Digest"); put("Metalink")
                put("XML-RPC"); put("SFTP")
            })
    )

    /** aria2.addUri：params[0] = URI 数组（或单个字符串）。取首个有效链接加入下载。 */
    private fun addUri(params: JSONArray): String {
        if (params.length() == 0) fail(-32602, "Invalid params: no uri")
        val first = params.opt(0)
        val uris = when (first) {
            is String -> listOf(first)
            is JSONArray -> (0 until first.length()).map { first.optString(it) }
            else -> emptyList()
        }
        for (u in uris) {
            val v = u.trim()
            if (v.isBlank()) continue
            com.aria2.mobile.util.AppLogger.i(TAG, "RPC 捕获下载: $v")
            val gid = DownloadEngine.add(v)
            if (gid.isNotBlank()) return gid
        }
        fail(-32602, "Invalid params: no valid uri")
    }

    private fun globalOption(): JSONObject = JSONObject()
        .put("dir", DownloadEngine.downloadDir())
        .put("max-concurrent-downloads", "4")
        .put("split", "4")
        .put("continue", "true")

    private fun getGlobalStat(): JSONObject {
        val list = DownloadEngine.items.value
        val active = list.count { it.status == DownloadStatus.Active }
        val waiting = list.count { it.status == DownloadStatus.Waiting || it.status == DownloadStatus.Paused }
        val stopped = list.count { it.status == DownloadStatus.Complete || it.status == DownloadStatus.Error }
        return JSONObject()
            .put("downloadSpeed", list.sumOf { if (it.status == DownloadStatus.Active) it.downloadSpeed else 0L }.toString())
            .put("uploadSpeed", "0")
            .put("numActive", active.toString())
            .put("numWaiting", waiting.toString())
            .put("numStopped", stopped.toString())
            .put("numStoppedTotal", stopped.toString())
    }

    private fun listToStatus(): JSONArray {
        val arr = JSONArray()
        DownloadEngine.items.value.forEach { arr.put(itemToJson(it)) }
        return arr
    }

    private fun tellStatus(params: JSONArray): JSONObject {
        val gid = params.optString(0)
        val item = DownloadEngine.items.value.firstOrNull { it.gid == gid }
        return if (item != null) itemToJson(item) else jsonError(1, "gid not found: $gid") as JSONObject
    }

    private fun toggle(params: JSONArray, target: DownloadStatus): String {
        if (params.length() == 0) fail(1, "missing gid")
        val gid = params.optString(0)
        when (target) {
            DownloadStatus.Paused -> DownloadEngine.pause(gid)
            else -> DownloadEngine.resume(gid)
        }
        return gid
    }

    private fun removeByGid(params: JSONArray): String {
        val gid = params.optString(0)
        DownloadEngine.remove(gid)
        return gid
    }

    /** 把引擎任务映射为 aria2 风格状态对象，便于前端展示进度。 */
    private fun itemToJson(item: com.aria2.mobile.data.DownloadItem): JSONObject = JSONObject()
        .put("gid", item.gid)
        .put("status", aria2Status(item.status))
        .put("totalLength", item.totalLength.toString())
        .put("completedLength", item.completedLength.toString())
        .put("downloadSpeed", item.downloadSpeed.toString())
        .put("uploadSpeed", "0")
        .put("files", JSONArray().put(
            JSONObject()
                .put("index", "1")
                .put("path", item.uri)
                .put("completedLength", item.completedLength.toString())
                .put("length", item.totalLength.toString())
        ))

    private fun aria2Status(status: DownloadStatus): String = when (status) {
        DownloadStatus.Active -> "active"
        DownloadStatus.Waiting -> "waiting"
        DownloadStatus.Paused -> "paused"
        DownloadStatus.Complete -> "complete"
        DownloadStatus.Error -> "error"
        DownloadStatus.Removed -> "removed"
    }

    private fun jsonError(code: Int, message: String): JSONObject =
        JSONObject().put("code", code).put("message", message)

    /** 从查询串 `a=1&b=2` 取某个参数的 URL 解码值，无则返回空串。 */
    private fun String.toQueryParam(name: String): String {
        for (pair in split('&')) {
            val idx = pair.indexOf('=')
            if (idx > 0 && pair.substring(0, idx) == name) {
                return runCatching {
                    java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                }.getOrDefault(pair.substring(idx + 1))
            }
        }
        return ""
    }
}