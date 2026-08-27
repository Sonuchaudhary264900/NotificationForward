package com.sonuchaudhary.notificationforward.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local read cache of the linked family device, refreshed from Firestore. Not the source of truth. */
@Entity(tableName = "paired_device")
data class PairedDeviceCache(
    @PrimaryKey val deviceId: String,
    val familyId: String,
    val role: String,
    val displayName: String,
    val status: String,
    val lastSeenAt: Long,
    val updatedAt: Long
)
