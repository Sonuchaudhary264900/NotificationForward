package com.sonuchaudhary.notificationforward.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {
    @Upsert
    suspend fun upsert(device: PairedDeviceCache)

    @Query("SELECT * FROM paired_device WHERE familyId = :familyId")
    fun observeForFamily(familyId: String): Flow<List<PairedDeviceCache>>

    @Query("DELETE FROM paired_device WHERE familyId = :familyId")
    suspend fun clearFamily(familyId: String)
}
