package com.example.phuza.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.phuza.R

object NotificationUtils {

    const val CHANNEL_ID: String = "phuza-general"
    private const val TAG = "NotifUtils"

    /**
     * Ensure that the notification channel for general app notifications exists.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "General",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "App notifications"
            setShowBadge(true)
        }

        try {
            mgr.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "createNotificationChannel failed", e)
        }
    }

    /**
     * Check whether we can post notifications, accounting for runtime permission on Android 13+.
     */
    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun show(
        context: Context,
        title: String,
        text: String,
        id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
        @DrawableRes smallIconRes: Int = R.drawable.ic_bell,
        pendingIntent: PendingIntent? = null,
        isAutoCancel: Boolean = true
    ) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "Permission not granted; skipping notify")
            return
        }

        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(isAutoCancel)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (se: SecurityException) {
            Log.e(TAG, "notify() SecurityException", se)
        } catch (e: Exception) {
            Log.e(TAG, "notify() failed", e)
        }
    }
}

/*
 * REFERENCES
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 */
