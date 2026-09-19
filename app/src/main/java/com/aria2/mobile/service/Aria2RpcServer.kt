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
            client.getInputStream().buffered().reader(StandardCharsets.UTF_8).use { reader ->
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
                    val path = requestLine.split(" ").getOrNull(1) ?: "/"
                    if (requestLine.startsWith("POST") && path == PATH) {
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
                        writeJson(out, dispatch(body))
                    } else {
                        // GET 或其它路径：AriaNG 会 GET 探测，返回兼容空错误
                        val err = JSONObject()
                            .put("jsonrpc", "2.0")
                            .put("id", null)
                            .put("error", jsonError(1, "method not found"))
                        writeJson(out, err)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "request handle error: ${e.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun writeJson(out: java.io.OutputStream, obj: JSONObject) {
        val bytes = obj.toString().toByteArray(StandardCharsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        out.write(head)
        out.write(bytes)
        out.flush()
    }

    // ---- JSON-RPC 分发 ----

    private fun dispatch(body: String): JSONObject {
        return try {
            val req = JSONObject(body)
            val id = req.opt("id")
            val method = req.optString("method", "")
            val params = req.optJSONArray("params") ?: JSONArray()

            if (method == "system.multicall") {
                handleMulticall(id, params)
            } else {
                val result = handleMethod(method, params)
                // 正常返回
                JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
            }
        } catch (e: Exception) {
            JSONObject()
                .put("jsonrpc", "2.0").put("id", null)
                .put("error", jsonError(1, e.message ?: "internal error"))
        }
    }

    /** 处理 system.multicall：params = [{methodName, params}, ...]。 */
    private fun handleMulticall(id: Any?, params: JSONArray): JSONObject {
        val results = JSONArray()
        for (i in 0 until params.length()) {
            val call = params.optJSONObject(i)
            if (call == null) { results.put(null); continue }
            val m = call.optString("methodName")
            val p = call.optJSONArray("params") ?: JSONArray()
            try {
                results.put(handleMethod(m, p))
            } catch (e: Exception) {
                results.put(JSONObject().put("fault", jsonError(1, e.message ?: "error")))
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
            "aria2.addUri" -> addUri(params)
            "aria2.addTorrent" -> jsonError(1, "unsupported: use direct link") as Any
            "aria2.getGlobalStat" -> getGlobalStat()
            "aria2.tellActive" -> listToStatus()
            "aria2.tellWaiting" -> { params.optInt(1, 1000); listToStatus() }
            "aria2.tellStopped" -> listToStatus()
            "aria2.tellStatus" -> tellStatus(params)
            "aria2.pause" -> toggleStatus(params, DownloadStatus.Paused)
            "aria2.unpause" -> toggleStatus(params, DownloadStatus.Waiting)
            "aria2.remove" -> removeByGid(params)
            else -> jsonError(1, "method not found: $method") as Any
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

    /** aria2.addUri：params[0] = URI 数组。取首个有效链接加入下载。 */
    private fun addUri(params: JSONArray): String {
        val uris = params.optJSONArray(0) ?: JSONArray()
        for (i in 0 until uris.length()) {
            val u = uris.optString(i).trim()
            if (u.isBlank()) continue
            val gid = DownloadEngine.add(u)
            if (gid.isNotBlank()) return gid
        }
        throw IllegalArgumentException("no valid uri in addUri")
    }

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

    private fun toggleStatus(params: JSONArray, target: DownloadStatus): String {
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
}