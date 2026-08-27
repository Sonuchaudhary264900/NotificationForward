package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    private const val QUEUE_SYNC_WORK = "queue_sync_work"
    private const val QUEUE_PERIODIC_WORK = "queue_periodic_work"
    private const val RECORDING_SYNC_WORK = "recording_sync_work"
    private const val RECORDING_PERIODIC_WORK = "recording_periodic_work"
    private const val FCM_TOKEN_SYNC_WORK = "fcm_token_sync_work"
    private const val COMMAND_POLL_WORK = "command_poll_work"
    private const val COMMAND_POLL_PERIODIC_WORK = "command_poll_periodic_work"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            QUEUE_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun ensurePeriodic(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<QueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            QUEUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    fun enqueueRecordingScanNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RecordingScanWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RECORDING_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun ensureRecordingPeriodic(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<RecordingScanWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECORDING_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    fun enqueueFcmTokenSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<FcmTokenSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            FCM_TOKEN_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueCommandPollNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CommandPollWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            COMMAND_POLL_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun ensureCommandPollPeriodic(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<CommandPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            COMMAND_POLL_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }
}
