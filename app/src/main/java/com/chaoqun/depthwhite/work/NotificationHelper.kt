package com.chaoqun.depthwhite.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.chaoqun.depthwhite.MainActivity
import com.chaoqun.depthwhite.R

object NotificationHelper {
    const val CHANNEL_ID = "depth_white_convert"
    const val CONVERT_ID = 41
    const val CLIP_ID = 42

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun notification(context: Context, title: String, text: String, progress: Int, max: Int): Notification {
        ensureChannel(context)
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (max > 0) {
            builder.setProgress(max, progress.coerceIn(0, max), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun foregroundInfo(
        context: Context,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        id: Int = CONVERT_ID,
    ): ForegroundInfo {
        val n = notification(context, title, text, progress, max)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, n)
        }
    }
}
