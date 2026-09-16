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

/**
 * 前台服务：保证内嵌 aria2 在后台持续提供服务，并关联常驻通知。
 * 生命周期：onStartCommand 里拉起 EmbeddedAria2，onDestroy 清理。
 */
class Aria2Service : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        EmbeddedAria2.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        EmbeddedAria2.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("aria2 本地服务")
            .setContentText("正在后台提供下载服务")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "aria2_service"
        private const val NOTIF_ID = 1

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "aria2 本地服务", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        fun start(context: Context) {
            ensureChannel(context)
            context.startForegroundService(Intent(context, Aria2Service::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, Aria2Service::class.java))
        }
    }
}