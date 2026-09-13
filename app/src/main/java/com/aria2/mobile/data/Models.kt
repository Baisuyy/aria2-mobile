package com.aria2.mobile.data

/** aria2 服务器配置。 */
data class Aria2Server(
    val rpcUrl: String = "http://127.0.0.1:6800/jsonrpc",
    val secret: String = "",
) {
    val isConfigured: Boolean get() = rpcUrl.isNotBlank()
}

/** 下载偏好，最终映射为 aria2.addUri 的 options 参数。 */
data class DownloadPrefs(
    val dir: String = "",
    val extraOptions: String = "",
) {
    /** 把“每行 key=value”的原始文本解析成有序 options。 */
    fun optionsMap(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        if (dir.isNotBlank()) map["dir"] = dir
        extraOptions.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val idx = line.indexOf('=')
                if (idx > 0) map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
        return map
    }
}