package com.sonuchaudhary.notificationforward.data

import com.sonuchaudhary.notificationforward.firebase.AnonymousAuthManager
import com.sonuchaudhary.notificationforward.firebase.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class PairingCodeResult(
    val code: String,
    val familyId: String,
    val expiresAt: Long,
    val alreadyPaired: Boolean = false
)

/**
 * Wraps the createPairingCode/consumePairingCode Cloud Functions (see firebase/functions/src/index.ts)
 * and the Firestore listener a child device uses to detect when its code has been consumed.
 *
 * Both operations run as an atomic server-side transaction rather than direct client Firestore
 * writes: at the moment a parent submits a code it is not yet a member of the family, so it can't
 * be granted rule-based write access to create its own device doc / join `members` without either
 * a race-prone multi-step write sequence or rules permissive enough to let any client join any
 * family by guessing a code before it's consumed.
 */
class PairingRepository {
    private val functions = FirebaseModule.functions
    private val firestore = FirebaseModule.firestore

    suspend fun createPairingCode(childDeviceId: String, displayName: String): PairingCodeResult {
        AnonymousAuthManager.ensureSignedIn()
        val payload = hashMapOf(
            "childDeviceId" to childDeviceId,
            "displayName" to displayName
        )
        val result = functions.getHttpsCallable("createPairingCode").call(payload).await()
        val data = result.data as? Map<*, *> ?: error("Unexpected response from createPairingCode")
        val alreadyPaired = data["alreadyPaired"] as? Boolean ?: false
        return PairingCodeResult(
            code = data["code"] as? String ?: if (alreadyPaired) "" else error("Missing code in createPairingCode response"),
            familyId = data["familyId"] as? String ?: error("Missing familyId in createPairingCode response"),
            expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L,
            alreadyPaired = alreadyPaired
        )
    }

    suspend fun consumePairingCode(code: String, parentDeviceId: String, displayName: String): String {
        AnonymousAuthManager.ensureSignedIn()
        val payload = hashMapOf(
            "code" to code.trim(),
            "parentDeviceId" to parentDeviceId,
            "displayName" to displayName
        )
        val result = functions.getHttpsCallable("consumePairingCode").call(payload).await()
        val data = result.data as? Map<*, *> ?: error("Unexpected response from consumePairingCode")
        return data["familyId"] as? String ?: error("Missing familyId in consumePairingCode response")
    }

    /** Emits the child device's `status` field so the code screen can detect ACTIVE and navigate on. */
    fun observeDeviceStatus(familyId: String, deviceId: String): Flow<String> = callbackFlow {
        val registration = firestore.collection("families").document(familyId)
            .collection("devices").document(deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.getString("status") ?: "PENDING")
            }
        awaitClose { registration.remove() }
    }
}
