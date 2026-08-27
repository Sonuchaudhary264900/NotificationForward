package com.sonuchaudhary.notificationforward.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sonuchaudhary.notificationforward.worker.WorkerScheduler

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                WorkerScheduler.ensurePeriodic(context)
                WorkerScheduler.enqueueImmediate(context)
                WorkerScheduler.ensureRecordingPeriodic(context)
                WorkerScheduler.enqueueRecordingScanNow(context)
                WorkerScheduler.ensureCommandPollPeriodic(context)
                WorkerScheduler.enqueueCommandPollNow(context)
                WorkerScheduler.enqueueFcmTokenSync(context)
            }
        }
    }
}
