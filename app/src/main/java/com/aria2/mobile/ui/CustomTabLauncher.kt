package com.aria2.mobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 用系统 Chrome Custom Tabs 打开网页：接管内核渲染，同时保留沉浸式”应用内“体验。
 * 设备没有支持 Custom Tabs 的浏览器时自动回退到普通系统打开。
 */
object CustomTabLauncher {

    fun launch(context: Context, rawUrl: String, toolbarColor: Int) {
        val uri = normalize(rawUrl) ?: return
        val packageName = CustomTabsClient.getPackageName(context, null)

        if (packageName == null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        val builder = CustomTabsIntent.Builder()
            .setToolbarColor(toolbarColor)
            .setShowTitle(true)
            .enableUrlBarHiding()

        val intent = builder.build()
        intent.intent.setPackage(packageName)
        try {
            intent.launchUrl(context, uri)
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun normalize(raw: String): Uri? {
        val t = raw.trim()
        if (t.isBlank()) return null
        val withScheme = if (t.contains("://")) t else "https://$t"
        return Uri.parse(withScheme)
    }
}