package com.sonuchaudhary.notificationforward

import android.app.Application
import com.sonuchaudhary.notificationforward.security.SecurePrefsMigrator
import com.sonuchaudhary.notificationforward.worker.WorkerScheduler

class NotificationForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Runs before any SettingsStore/RoleStore is constructed elsewhere in the app.
        SecurePrefsMigrator.migrateIfNeeded(this)
        WorkerScheduler.ensurePeriodic(this)
        WorkerScheduler.ensureRecordingPeriodic(this)
        WorkerScheduler.ensureCommandPollPeriodic(this)
    }
}
