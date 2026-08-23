package com.sonuchaudhary.notificationforward.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recording_queue",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class RecordingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val phoneNumber: String?,
    val sizeBytes: Long,
    val recordedAt: Long,
    val status: QueueStatus = QueueStatus.PENDING,
    val attemptCount: Int = 0,
    val nextRetryAt: Long = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RecordingStats(
    val pendingCount: Int,
    val sendingCount: Int,
    val sentCount: Int,
    val failedCount: Int
)
