package com.aria2.mobile.data

import org.json.JSONArray

/** 备用：兼容无 JSONObject 引入时的辅助工具。 */
object JsonUtil {
    fun parseStringArray(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}