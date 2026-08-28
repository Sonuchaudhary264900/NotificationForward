package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonuchaudhary.notificationforward.data.NotificationRepository
import com.sonuchaudhary.notificationforward.network.BackendClient
import com.sonuchaudhary.notificationforward.network.NotificationLog
import com.sonuchaudhary.notificationforward.settings.RoleStore
import com.sonuchaudhary.notificationforward.settings.SettingsStore

class QueueWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val repository = NotificationRepository(appContext)
    private val settings = SettingsStore(appContext)
    private val roleStore = RoleStore(appContext)
    private val backendClient = BackendClient()

    override suspend fun doWork(): Result {
        if (!settings.forwardingEnabled) {
            return Result.success()
        }

        try {
            repository.purgeOldSent(settings.dataRetentionDays)
        } catch (e: Exception) {
            Log.w(TAG, "Purge failed", e)
        }

        val items = repository.getPending(20)
        if (items.isEmpty()) {
            return Result.success()
        }

        if (!roleStore.isPaired) {
            repository.markSent(items.first().id)
            return Result.success()
        }

        repository.markSending(items.map { it.id })
        var shouldRetry = false

        items.forEach { item ->
            val log = NotificationLog(
                deviceId = roleStore.deviceId,
                familyId = roleStore.familyId ?: return@forEach,
                packageName = item.packageName,
                appName = item.appName,
                title = item.title,
                text = item.text,
                postedAt = item.postedAt
            )

            val result = backendClient.logNotification(log)
            if (result.isSuccess) {
                repository.markSent(item.id)
            } else {
                val attempt = item.attemptCount + 1
                val maxRetries = 10
                repository.markFailure(
                    id = item.id,
                    attemptCount = if (attempt >= maxRetries) maxRetries else attempt,
                    maxRetry = maxRetries,
                    lastError = result.exceptionOrNull()?.message ?: "Backend error"
                )
                if (attempt < maxRetries) {
                    shouldRetry = true
                }
            }
        }

        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "QueueWorker"
    }
}
