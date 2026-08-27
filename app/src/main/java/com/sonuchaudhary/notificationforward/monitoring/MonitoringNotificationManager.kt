package com.sonuchaudhary.notificationforward.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sonuchaudhary.notificationforward.R

enum class MonitoringFeature(val label: String) {
    LOCATION("location"),
    CAMERA("camera"),
    SCREEN("screen")
}

/**
 * The non-negotiable transparency notice: whenever a paired parent device is actively viewing
 * this device's location, camera, or screen, this notification is shown and cannot be swiped
 * away (it's tied to MonitoringForegroundService's lifecycle, not just setOngoing). Built now,
 * unused until Phase 2 (location) and Phase 5 (mirroring) start calling it.
 */
object MonitoringNotificationManager {
    private const val CHANNEL_ID = "monitoring_active"
    const val NOTIFICATION_ID = 5001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoring active",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shown whenever a paired parent device is actively viewing " +
                "this device's location, camera, or screen."
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context, features: Set<MonitoringFeature>): Notification {
        ensureChannel(context)
        val activeLabel = features.joinToString(", ") { it.label }.ifEmpty { "unknown" }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitoring)
            .setContentTitle("This device is being monitored")
            .setContentText("A paired parent device is viewing: $activeLabel")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
