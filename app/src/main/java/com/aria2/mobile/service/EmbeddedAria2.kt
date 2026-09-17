package com.aria2.mobile.service

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 内嵌 aria2 服务管理器。
 *
 * 借鉴 aria-ng-gui-android 的自托管做法：
 * - 打包时把预编译的 aria2c 放进 jniLibs/{abi}，随 ABI 拆包分发（armeabi-v7a 32 位
 *   / arm64-v8a 64 位），安装后即被解压到 applicationInfo.nativeLibraryDir；
 * - 运行时按当前设备的 ABI 从 nativeLibraryDir 把对应该架构的二进制复制到应用私有
 *   目录并设可执行位，然后由 ProcessBuilder 拉成本地子进程；
 * - RPC 端口自动探测空闲端口（默认 6800 起），配置写盘后交给 aria2c；
 * - 下载目录用应用专属外部目录，规避 Android 10+ 分区存储权限限制。
 */
object EmbeddedAria2 {

    private const val TAG = "EmbeddedAria2"
    private const val DEFAULT_PORT = 6800
    private const val MAX_PORT_SCAN = 20
    private const val RPC_PATH = "/jsonrpc"

    enum class Status { Idle, Starting, Running, Failed, Stopped }

    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _port = MutableStateFlow(DEFAULT_PORT)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _log = MutableStateFlow<String?>(null)
    val log: StateFlow<String?> = _log.asStateFlow()

    @Volatile private var process: Process? = null

    private fun binaryFile(ctx: Context) = File(ctx.filesDir, "aria2/aria2c")
    private fun confFile(ctx: Context) = File(ctx.filesDir, "aria2/aria2.conf")

    fun rpcUrl(): String = "http://127.0.0.1:${_port.value}$RPC_PATH"

    /** 从 nativeLibraryDir 取出与当前设备 ABI 匹配的二进制并设置可执行位。 */
    private fun provision(ctx: Context): File {
        val bin = binaryFile(ctx)
        if (!bin.exists() || bin.length() == 0L) {
            bin.parentFile?.mkdirs()
            val abi = Build.SUPPORTED_ABIS.firstOrNull()
            val native = File(ctx.applicationInfo.nativeLibraryDir, "aria2c")
            if (!native.exists() || native.length() == 0L) {
                throw IllegalStateException(
                    if (abi != null) "没有匹配 ${abi} 架构的内嵌 aria2c，请改连远程服务器"
                    else "未找到可用 ABI 的内嵌 aria2c"
                )
            }
            native.inputStream().use { input ->
                bin.outputStream().use { output -> input.copyTo(output) }
            }
        }
        bin.setExecutable(true, true)
        bin.setReadable(true, true)
        return bin
    }

    /** 探测空闲端口（默认 6800 起，最多扫描若干位）。 */
    private fun findFreePort(from: Int): Int {
        for (p in from until from + MAX_PORT_SCAN) {
            try {
                ServerSocket().use { ss -> ss.bind(InetSocketAddress("127.0.0.1", p)) }
                return p
            } catch (_: IOException) { /* 端口被占用，尝试下一个 */ }
        }
        return from
    }

    /** 生成 aria2 配置。dir 用应用专属外部目录，避免分区存储读写权限问题。 */
    private fun writeConf(ctx: Context, port: Int): File {
        val downloads = File(ctx.getExternalFilesDir(null), "Download").apply { mkdirs() }
        val conf = """
            enable-rpc=true
            rpc-listen-all=false
            rpc-listen-port=$port
            rpc-allow-origin-all=true
            rpc-secure=false
            dir=${downloads.absolutePath}
            continue=true
            max-connection-per-server=16
            split=16
            min-split-size=1M
            file-allocation=none
            allow-overwrite=false
            auto-file-renaming=true
            event-poll=10
        """.trimIndent()
        val f = confFile(ctx)
        f.parentFile?.mkdirs()
        f.writeText(conf)
        return f
    }

    /** 启动内嵌 aria2。幂等：已在运行则直接返回。 */
    fun start(ctx: Context) {
        if (process?.isAlive == true) return
        if (_status.value == Status.Starting) return
        _status.value = Status.Starting
        _log.value = null
        val abis = Build.SUPPORTED_ABIS.joinToString()
        Thread {
            try {
                val bin = provision(ctx)
                val port = findFreePort(DEFAULT_PORT)
                _port.value = port
                val conf = writeConf(ctx, port)

                val pb = ProcessBuilder(bin.absolutePath, "--conf-path=${conf.absolutePath}")
                pb.redirectErrorStream(true)
                val proc = pb.start()
                process = proc

                // 消费输出，避免管道阻塞；同时保留最近几行，方便失败时定位原因
                val tail = ArrayDeque<String>()
                Thread {
                    try {
                        proc.inputStream.bufferedReader().forEachLine { line ->
                            Log.i(TAG, line)
                            tail.addLast(line)
                            while (tail.size > 8) tail.removeFirst()
                        }
                    } catch (_: Exception) {
                    }
                }.apply { isDaemon = true }.start()

                // 短暂等待：进程立刻退出说明二进制/环境不兼容
                Thread.sleep(1400)
                if (proc.isAlive) {
                    _status.value = Status.Running
                    _log.value = "设备架构 $abis · 端口 $port"
                } else {
                    val code = runCatching { proc.exitValue() }.getOrNull()
                    proc.destroy()
                    process = null
                    _status.value = Status.Failed
                    val detail = tail.joinToString(" ").take(300)
                    _log.value = "进程退出码 $code（设备架构 $abis）。" +
                        if (detail.isNotBlank()) " 输出：$detail"
                        else " 可能当前设备不支持该二进制（如 x86/x86_64 模拟器），可改连远程服务器"
                }
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
                process = null
                _status.value = Status.Failed
                _log.value = (e.message ?: "启动失败") + "（设备架构 ${abis}）"
            }
        }.apply { isDaemon = true }.start()
    }

    /** 停止内嵌 aria2。 */
    fun stop() {
        val p = process
        process = null
        if (p != null) {
            runCatching { p.destroy() }
            try { p.waitFor(1500, TimeUnit.MILLISECONDS) } catch (_: Exception) { }
            runCatching { p.destroyForcibly() }
        }
        _status.value = Status.Stopped
    }
}