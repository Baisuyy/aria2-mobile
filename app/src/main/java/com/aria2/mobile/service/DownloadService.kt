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
import com.aria2.mobile.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务：负责"监听下载请求"并承载 [DownloadEngine]。
 *
 * 除接收系统链接/内置浏览器捕获/手动输入外，还会在本地回环启动极简 aria2
 * JSON-RPC 监听（[Aria2RpcServer]，127.0.0.1:6800），接住网页/脚本 POST 过来的
 * 下载请求，取出直链交给 [DownloadEngine.add] 下载，不做完整 aria2 仿真。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i(TAG, "下载服务启动")
        DownloadEngine.init(this)
        DownloadEngine.startListening()
        Aria2RpcServer.start()
        startForeground(NOTIF_ID, buildNotification())
        observeForNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DownloadEngine.init(this)
        DownloadEngine.startListening()
        Aria2RpcServer.start()
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        AppLogger.i("DownloadService", "下载服务停止")
        Aria2RpcServer.stop()
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
        private const val TAG = "DownloadService"
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