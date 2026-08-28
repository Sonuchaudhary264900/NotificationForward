package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonuchaudhary.notificationforward.data.RecordingItem
import com.sonuchaudhary.notificationforward.data.RecordingRepository
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

    override suspend fun doWork(): Result {
        val enabled = settings.recordingBackupEnabled
        if (!enabled) {
            return Result.success()
        }

        repository.resetStaleSending()

        val lastScan = settings.lastRecordingScanAt
        if (lastScan == 0L) {
            settings.lastRecordingScanAt = System.currentTimeMillis()
            return Result.success()
        }

        scanForNewRecordings(lastScan)

        val items = repository.getPending(20)
        if (items.isEmpty()) {
            return Result.success()
        }

        repository.markSending(items.map { it.id })

        // Mark items as sent (placeholder for Firebase Storage upload later)
        items.forEach { item ->
            repository.markSent(item.id)
        }

        return Result.success()
    }

    private fun buildCaption(item: RecordingItem): String {
        val number = item.phoneNumber ?: "Unknown number"
        val timestamp = CAPTION_DATE_FORMAT.format(Date(item.recordedAt))
        return "📞 $number\n🕒 $timestamp"
    }

    private fun scanForNewRecordings(since: Long) {
        // TODO: Implement recording scan with Firebase Storage or backend upload
        // For now, just update the scan time
        settings.lastRecordingScanAt = System.currentTimeMillis()
    }

    private fun extractPhoneFromFilename(filename: String): String? {
        // Expects format like "20250128_105530_1234567890.m4a"
        val parts = filename.replace(".m4a", "").split("_")
        return if (parts.size >= 3) parts[2] else null
    }

    companion object {
        private const val TAG = "RecordingScanWorker"
        private val CAPTION_DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US)
    }
}
