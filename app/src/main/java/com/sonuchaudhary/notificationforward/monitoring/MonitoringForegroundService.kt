package com.sonuchaudhary.notificationforward.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Skeleton owner of the "being monitored" foreground notification's lifecycle. Non-dismissability
 * comes from the notification being tied to a live foreground service rather than relying on
 * setOngoing() alone. Phase 2 (location) and Phase 5 (mirroring) call start()/stop() with the
 * relevant MonitoringFeature set instead of managing their own foreground service each.
 */
class MonitoringForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val features = intent?.getStringArrayExtra(EXTRA_FEATURES)
            ?.mapNotNull { runCatching { MonitoringFeature.valueOf(it) }.getOrNull() }
            ?.toSet()
            .orEmpty()

        if (features.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            MonitoringNotificationManager.NOTIFICATION_ID,
            MonitoringNotificationManager.buildNotification(this, features)
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        MonitoringNotificationManager.clear(this)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_FEATURES = "extra_features"

        fun start(context: Context, features: Set<MonitoringFeature>) {
            val intent = Intent(context, MonitoringForegroundService::class.java)
                .putExtra(EXTRA_FEATURES, features.map { it.name }.toTypedArray())
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringForegroundService::class.java))
        }
    }
}
