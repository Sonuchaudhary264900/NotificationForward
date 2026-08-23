package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonuchaudhary.notificationforward.data.RecordingRepository
import com.sonuchaudhary.notificationforward.network.TelegramFileUploader
import com.sonuchaudhary.notificationforward.settings.SettingsStore
import java.io.File

class RecordingScanWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val repository = RecordingRepository(appContext)
    private val settings = SettingsStore(appContext)
    private val uploader = TelegramFileUploader()

    override suspend fun doWork(): Result {
        val enabled = settings.recordingBackupEnabled
        val botToken = settings.recordingBotToken
        val chatId = settings.recordingChatId
        if (!enabled || botToken.isBlank() || chatId.isBlank()) {
            return Result.success()
        }

        repository.resetStaleSending()

        val lastScan = settings.lastRecordingScanAt
        if (lastScan == 0L) {
            // First run after enabling: establish a baseline so only recordings made
            // from this point forward are ever picked up, never the existing backlog.
            settings.lastRecordingScanAt = System.currentTimeMillis()
            return Result.success()
        }

        scanForNewRecordings(lastScan)

        val items = repository.getPending(settings.batchSize)
        if (items.isEmpty()) {
            return Result.success()
        }

        repository.markSending(items.map { it.id })

        var shouldRetry = false
        items.forEach { item ->
            val file = File(item.filePath)
            val result = uploader.sendDocument(botToken, chatId, file)
            if (result.success) {
                repository.markSent(item.id)
            } else {
                val attempt = item.attemptCount + 1
                repository.markFailure(
                    id = item.id,
                    attemptCount = attempt,
                    maxRetry = settings.maxRetries,
                    lastError = result.message
                )
                if (!result.isPermanentFailure) {
                    shouldRetry = true
                }
            }
        }

        return if (shouldRetry) Result.retry() else Result.success()
    }

    private suspend fun scanForNewRecordings(sinceMillis: Long) {
        val root = File(settings.recordingFolderPath)
        if (!root.isDirectory) {
            Log.w(TAG, "Recording folder not accessible: ${root.absolutePath}")
            return
        }

        var latestSeen = sinceMillis
        root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .forEach { file ->
                val modified = file.lastModified()
                if (modified > sinceMillis) {
                    repository.enqueueIfNew(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        sizeBytes = file.length(),
                        recordedAt = modified
                    )
                    if (modified > latestSeen) {
                        latestSeen = modified
                    }
                }
            }
        settings.lastRecordingScanAt = latestSeen
    }

    companion object {
        private const val TAG = "RecordingScanWorker"
        private val AUDIO_EXTENSIONS = setOf("aac", "wav", "mp3", "amr", "3gp", "m4a", "ogg")
    }
}
