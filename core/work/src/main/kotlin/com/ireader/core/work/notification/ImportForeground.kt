package com.ireader.core.work.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object ImportForeground {
    const val CHANNEL_ID = "import"
    private const val CHANNEL_NAME = "Book Import"
    private const val NOTIFICATION_ID = 4242

    fun info(context: Context, done: Int, total: Int, currentTitle: String?): ForegroundInfo {
        ensureChannel(context)

        val contentTitle = currentTitle?.takeIf { it.isNotBlank() } ?: "Importing books"
        val contentText = if (total > 0) "$done / $total" else done.toString()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), total <= 0)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while importing books"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
