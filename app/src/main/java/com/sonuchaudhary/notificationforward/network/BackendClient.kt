package com.sonuchaudhary.notificationforward.network

import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class NotificationLog(
    val deviceId: String,
    val familyId: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

data class RecordingLog(
    val deviceId: String,
    val familyId: String,
    val recordingName: String,
    val duration: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    val storagePath: String,
    val uploadedAt: Long
)

data class DeviceHeartbeat(
    val deviceId: String,
    val familyId: String,
    val role: String,
    val status: String,
    val fcmToken: String?,
    val lastSeen: Long
)

class BackendClient(private val backendUrl: String = DEFAULT_BACKEND_URL) {
    private val client = HttpClientProvider.client
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun logNotification(notification: NotificationLog): Result<Unit> {
        return callBackend("notifications", notification)
    }

    suspend fun logRecording(recording: RecordingLog): Result<Unit> {
        return callBackend("recordings", recording)
    }

    suspend fun sendHeartbeat(heartbeat: DeviceHeartbeat): Result<Unit> {
        return callBackend("heartbeat", heartbeat)
    }

    suspend fun deleteData(familyId: String, deviceId: String, dataType: String, itemId: String): Result<Unit> {
        return try {
            val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: return Result.failure(IllegalStateException("Not authenticated"))

            val url = "$backendUrl/api/data/delete"
            val body = mapOf(
                "familyId" to familyId,
                "deviceId" to deviceId,
                "dataType" to dataType,
                "itemId" to itemId
            )

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $idToken")
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Delete failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun callBackend(endpoint: String, payload: Any): Result<Unit> {
        return try {
            val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: return Result.failure(IllegalStateException("Not authenticated"))

            val url = "$backendUrl/api/$endpoint"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $idToken")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("$endpoint failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val DEFAULT_BACKEND_URL = "https://your-backend.example.com"
    }
}