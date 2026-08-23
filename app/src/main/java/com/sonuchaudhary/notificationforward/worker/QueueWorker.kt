package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonuchaudhary.notificationforward.data.NotificationRepository
import com.sonuchaudhary.notificationforward.network.WebhookClient
import com.sonuchaudhary.notificationforward.settings.AuthMode
import com.sonuchaudhary.notificationforward.settings.SettingsStore

class QueueWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val repository = NotificationRepository(appContext)
    private val settings = SettingsStore(appContext)
    private val webhookClient = WebhookClient()

    override suspend fun doWork(): Result {
        val config = settings.readAll()
        if (!config.forwardingEnabled || config.webhookUrl.isBlank()) {
            return Result.success()
        }

        // Auto-purge sent items older than 7 days
        try {
            repository.purgeOldSent(7)
        } catch (e: Exception) {
            Log.w(TAG, "Purge failed", e)
        }

        val items = repository.getPending(config.batchSize)
        if (items.isEmpty()) {
            return Result.success()
        }

        repository.markSending(items.map { it.id })
        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"

        val headers = buildHeaders(config.authMode, config.bearerToken, settings.parseHeaders())
        val queryParams = settings.parseQueryParams()

        var shouldRetry = false
        items.forEach { item ->
            val result = webhookClient.send(
                url = config.webhookUrl,
                method = config.webhookMethod,
                headers = headers,
                queryParams = queryParams,
                payloadTemplate = config.payloadTemplateRaw,
                item = item,
                deviceId = deviceId
            )
            if (result.success) {
                repository.markSent(item.id)
            } else {
                val attempt = item.attemptCount + 1
                repository.markFailure(
                    id = item.id,
                    attemptCount = if (result.isPermanentFailure) config.maxRetries else attempt,
                    maxRetry = config.maxRetries,
                    lastError = result.message
                )
                if (!result.isPermanentFailure) {
                    shouldRetry = true
                }
            }
        }

        return if (shouldRetry) Result.retry() else Result.success()
    }

    private fun buildHeaders(
        authMode: AuthMode,
        bearerToken: String,
        customHeaders: Map<String, String>
    ): Map<String, String> {
        val finalHeaders = linkedMapOf("Content-Type" to "application/json")
        if (authMode == AuthMode.BEARER && bearerToken.isNotBlank()) {
            finalHeaders["Authorization"] = "Bearer $bearerToken"
        }
        finalHeaders.putAll(customHeaders)
        return finalHeaders
    }

    companion object {
        private const val TAG = "QueueWorker"
    }
}
