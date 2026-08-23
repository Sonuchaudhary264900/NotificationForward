package com.sonuchaudhary.notificationforward

import android.app.Application
import com.sonuchaudhary.notificationforward.worker.WorkerScheduler

class NotificationForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkerScheduler.ensurePeriodic(this)
        WorkerScheduler.ensureRecordingPeriodic(this)
    }
}
