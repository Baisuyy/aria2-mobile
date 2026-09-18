package com.aria2.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aria2.mobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务：仅负责"监听下载请求"并承载 [DownloadEngine]。
 *
 * 与旧版（嵌入式 aria2c + JSON-RPC）不同，这里不做任何 aria2 服务仿真：
 * - APP 收到系统链接/内置浏览器捕获/手动输入 → [DownloadEngine.add] 直接进入下载；
 * - 服务保持常驻，使下载在后台持续进行，并关联常驻通知。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        DownloadEngine.init(this)
        DownloadEngine.startListening()
        startForeground(NOTIF_ID, buildNotification())
        observeForNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DownloadEngine.init(this)
        DownloadEngine.startListening()
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        DownloadEngine.stopListening()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeForNotification() {
        scope.launch {
            DownloadEngine.items.collect { list ->
                val active = list.count { it.status == com.aria2.mobile.data.DownloadStatus.Active }
                val done = list.count { it.status == com.aria2.mobile.data.DownloadStatus.Complete }
                val manager = getSystemService(NotificationManager::class.java)
                val text = if (list.isEmpty()) "等待下载请求"
                    else "进行中 $active · 已完成 $done"
                val notif = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                    .setContentTitle("下载服务运行中")
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                runCatching { manager.notify(NOTIF_ID, notif) }
            }
        }
    }

    private fun buildNotification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("下载服务运行中")
            .setContentText("正在监听下载请求")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "download_service"
        private const val NOTIF_ID = 1

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                val ch = NotificationChannel(
                    CHANNEL_ID, "下载服务", NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(ch)
            }
        }

        fun start(context: Context) {
            ensureChannel(context)
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}