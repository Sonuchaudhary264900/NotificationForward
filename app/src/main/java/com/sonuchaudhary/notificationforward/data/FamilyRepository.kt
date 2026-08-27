package com.sonuchaudhary.notificationforward.data

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.sonuchaudhary.notificationforward.firebase.FirebaseModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Ongoing family/device state once pairing has completed: presence heartbeat, consent
 * acknowledgement, and a local Room cache of the family's device roster (kept in sync from
 * Firestore, following the same "Room is the UI's source of truth, network syncs into it"
 * pattern QueueDao/RecordingDao already use).
 */
class FamilyRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).pairedDeviceDao()
    private val firestore = FirebaseModule.firestore
    private var devicesListener: ListenerRegistration? = null

    fun observeDevices(familyId: String): Flow<List<PairedDeviceCache>> = dao.observeForFamily(familyId)

    fun startSyncingDevices(familyId: String, scope: CoroutineScope) {
        devicesListener?.remove()
        devicesListener = firestore.collection("families").document(familyId)
            .collection("devices")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    snapshot.documents.forEach { doc ->
                        dao.upsert(
                            PairedDeviceCache(
                                deviceId = doc.id,
                                familyId = familyId,
                                role = doc.getString("role") ?: "",
                                displayName = doc.getString("displayName") ?: "",
                                status = doc.getString("status") ?: "PENDING",
                                lastSeenAt = doc.getTimestamp("lastSeenAt")?.toDate()?.time ?: 0L,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
    }

    fun stopSyncingDevices() {
        devicesListener?.remove()
        devicesListener = null
    }

    suspend fun heartbeat(familyId: String, deviceId: String, fcmToken: String?) {
        val data = mutableMapOf<String, Any>("lastSeenAt" to FieldValue.serverTimestamp())
        if (fcmToken != null) data["fcmToken"] = fcmToken
        firestore.collection("families").document(familyId)
            .collection("devices").document(deviceId)
            .set(data, SetOptions.merge())
            .await()
    }

    /** Hard requirement: a child device must record explicit consent before pairing is usable. */
    suspend fun recordConsent(familyId: String, deviceId: String) {
        firestore.collection("families").document(familyId)
            .collection("devices").document(deviceId)
            .set(mapOf("consentAcknowledgedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }
}
