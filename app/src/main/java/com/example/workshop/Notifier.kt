package com.example.workshop

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {

    // Away-from-workshop alerts.
    const val ALERT_CHANNEL = "garage_alerts"

    // The ongoing "tracking" notification of the background service.
    const val TRACKING_CHANNEL = "garage_tracking"

    fun createChannels(context: Context) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager =
            context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL,
                "Workshop alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when staff are away from the workshop"
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                TRACKING_CHANNEL,
                "Workshop tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while the app tracks workshop time in the background"
            }
        )

        // Channel from version 1 (test notification only).
        manager.deleteNotificationChannel("garage_notifications")
    }

    fun canNotify(context: Context): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        return true
    }

    fun openAppIntent(context: Context): PendingIntent {

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun showAlert(
        context: Context,
        id: Int,
        title: String,
        text: String
    ) {

        if (!canNotify(context)) {
            return
        }

        val notification =
            NotificationCompat.Builder(context, ALERT_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            android.util.Log.w("GARAGE_NOTIFICATION", "Notification blocked", e)
        }
    }
}
