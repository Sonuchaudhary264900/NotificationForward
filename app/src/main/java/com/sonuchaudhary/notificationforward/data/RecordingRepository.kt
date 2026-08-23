package com.sonuchaudhary.notificationforward.data

import android.content.Context

class RecordingRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).recordingDao()

    suspend fun enqueueIfNew(filePath: String, fileName: String, sizeBytes: Long, recordedAt: Long): Boolean {
        val now = System.currentTimeMillis()
        val item = RecordingItem(
            filePath = filePath,
            fileName = fileName,
            sizeBytes = sizeBytes,
            recordedAt = recordedAt,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now
        )
        return dao.insert(item) != -1L
    }

    suspend fun getPending(limit: Int): List<RecordingItem> = dao.getPending(System.currentTimeMillis(), limit)

    suspend fun markSending(ids: List<Long>) = dao.markSending(ids, System.currentTimeMillis())

    suspend fun markSent(id: Long) = dao.markSent(id, System.currentTimeMillis())

    suspend fun markFailure(id: Long, attemptCount: Int, maxRetry: Int, lastError: String) {
        val failed = attemptCount >= maxRetry
        val delayMillis = if (failed) 0L else calculateBackoff(attemptCount)
        dao.updateFailure(
            id = id,
            status = if (failed) QueueStatus.FAILED else QueueStatus.PENDING,
            attemptCount = attemptCount,
            nextRetryAt = System.currentTimeMillis() + delayMillis,
            lastError = lastError,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun resetStaleSending(staleAfterMillis: Long = 10 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        dao.resetStaleSending(now, now - staleAfterMillis)
    }

    suspend fun retryAllFailed() = dao.retryAllFailed(System.currentTimeMillis())

    fun observeStats() = dao.observeStats()

    fun observeRecent(limit: Int) = dao.observeRecent(limit)

    suspend fun deleteItem(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    private fun calculateBackoff(attemptCount: Int): Long {
        val base = 30_000L
        val exponential = base * (1L shl attemptCount.coerceAtMost(6))
        val jitter = (0..4_000).random().toLong()
        return exponential + jitter
    }
}
