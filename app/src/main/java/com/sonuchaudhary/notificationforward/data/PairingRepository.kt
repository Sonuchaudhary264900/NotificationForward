package com.sonuchaudhary.notificationforward.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sonuchaudhary.notificationforward.firebase.AnonymousAuthManager
import com.sonuchaudhary.notificationforward.firebase.FirebaseModule
import com.sonuchaudhary.notificationforward.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class PairingCodeResult(
    val code: String,
    val familyId: String,
    val expiresAt: Long,
    val alreadyPaired: Boolean = false
)

private const val FUNCTIONS_REGION = "us-central1"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Wraps the createPairingCode/consumePairingCode Cloud Functions (see firebase/functions/src/index.ts)
 * and the Firestore listener a child device uses to detect when its code has been consumed.
 *
 * Both operations run as an atomic server-side transaction rather than direct client Firestore
 * writes: at the moment a parent submits a code it is not yet a member of the family, so it can't
 * be granted rule-based write access to create its own device doc / join `members` without either
 * a race-prone multi-step write sequence or rules permissive enough to let any client join any
 * family by guessing a code before it's consumed.
 *
 * Calls them as plain HTTPS requests over the app's own OkHttp client (same pattern as
 * WebhookClient/TelegramFileUploader) with a manually-fetched Firebase Auth ID token, rather than
 * the Firebase Functions Android SDK's getHttpsCallable(). Verified on a real device: that SDK path
 * returned UNAUTHENTICATED even right after a confirmed-successful anonymous sign-in, while the
 * exact same ID token worked fine calling the function directly over REST — the Functions SDK's own
 * token-attachment relies on a Google Play Services channel that's broken/non-certified on some
 * budget devices. Fetching the token via FirebaseAuth directly and attaching it as a plain
 * Authorization header sidesteps that dependency.
 */
class PairingRepository {
    private val firestore = FirebaseModule.firestore
    private val gson = Gson()

    private suspend fun callFunction(name: String, payload: Map<String, Any?>): JsonObject {
        AnonymousAuthManager.ensureSignedIn()
        val idToken = FirebaseModule.auth.currentUser?.getIdToken(false)?.await()?.token
            ?: error("Signed in, but no auth token was returned")
        val projectId = FirebaseModule.auth.app.options.projectId
            ?: error("Firebase project ID unavailable")

        val request = Request.Builder()
            .url("https://$FUNCTIONS_REGION-$projectId.cloudfunctions.net/$name")
            .addHeader("Authorization", "Bearer $idToken")
            .post(gson.toJson(mapOf("data" to payload)).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val responseBody = withContext(Dispatchers.IO) {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMessage = runCatching {
                        gson.fromJson(text, JsonObject::class.java)
                            .getAsJsonObject("error")?.get("message")?.asString
                    }.getOrNull()
                    error(serverMessage ?: "$name failed (HTTP ${response.code})")
                }
                text
            }
        }
        return runCatching { gson.fromJson(responseBody, JsonObject::class.java).getAsJsonObject("result") }
            .getOrNull() ?: error("Unexpected response from $name")
    }

    suspend fun createPairingCode(childDeviceId: String, displayName: String): PairingCodeResult {
        val data = callFunction(
            "createPairingCode",
            mapOf("childDeviceId" to childDeviceId, "displayName" to displayName)
        )
        val alreadyPaired = data.get("alreadyPaired")?.asBoolean ?: false
        return PairingCodeResult(
            code = data.get("code")?.asString
                ?: if (alreadyPaired) "" else error("Missing code in createPairingCode response"),
            familyId = data.get("familyId")?.asString ?: error("Missing familyId in createPairingCode response"),
            expiresAt = data.get("expiresAt")?.asLong ?: 0L,
            alreadyPaired = alreadyPaired
        )
    }

    suspend fun consumePairingCode(code: String, parentDeviceId: String, displayName: String): String {
        val data = callFunction(
            "consumePairingCode",
            mapOf("code" to code.trim(), "parentDeviceId" to parentDeviceId, "displayName" to displayName)
        )
        return data.get("familyId")?.asString ?: error("Missing familyId in consumePairingCode response")
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
