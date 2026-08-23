package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonuchaudhary.notificationforward.data.RecordingItem
import com.sonuchaudhary.notificationforward.data.RecordingRepository
import com.sonuchaudhary.notificationforward.network.TelegramFileUploader
import com.sonuchaudhary.notificationforward.settings.SettingsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val result = uploader.sendDocument(botToken, chatId, file, caption = buildCaption(item))
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

    private fun buildCaption(item: RecordingItem): String {
        val number = item.phoneNumber ?: "Unknown number"
        val timestamp = CAPTION_DATE_FORMAT.format(Date(item.recordedAt))
        return "📞 $number\n🕒 $timestamp"
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
                        phoneNumber = extractPhoneNumber(file, root.name),
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

    /**
     * Most OEM call recorders store recordings under a per-contact folder named after the
     * phone number (e.g. PhoneRecord/8960867408/...); a few embed the number in the file
     * name instead (e.g. 1763307063863_+918960867408.wav). Try the folder first, then fall
     * back to scanning the file name for a number-looking run of digits.
     */
    private fun extractPhoneNumber(file: File, rootFolderName: String): String? {
        val parentName = file.parentFile?.name
        if (parentName != null && parentName != rootFolderName && looksLikePhoneNumber(parentName)) {
            return parentName
        }
        return extractNumberFromName(file.nameWithoutExtension)
    }

    private fun looksLikePhoneNumber(value: String): Boolean {
        val digitCount = value.count { it.isDigit() }
        return digitCount >= 3 && value.all { it.isDigit() || it == '+' }
    }

    private fun extractNumberFromName(name: String): String? {
        val matches = PHONE_NUMBER_PATTERN.findAll(name).map { it.value }.toList()
        if (matches.isEmpty()) return null
        // Prefer a run with an explicit "+" (international format) over a bare digit run,
        // since bare digit runs are often just the recording's timestamp prefix.
        return matches.firstOrNull { it.startsWith("+") } ?: matches.last()
    }

    companion object {
        private const val TAG = "RecordingScanWorker"
        private val AUDIO_EXTENSIONS = setOf("aac", "wav", "mp3", "amr", "3gp", "m4a", "ogg")
        private val PHONE_NUMBER_PATTERN = Regex("""\+?\d{5,15}""")
        private val CAPTION_DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }
}
