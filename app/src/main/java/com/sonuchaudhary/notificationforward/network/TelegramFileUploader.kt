package com.sonuchaudhary.notificationforward.network

import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

data class RecordingUploadResult(
    val success: Boolean,
    val isPermanentFailure: Boolean,
    val message: String
)

class TelegramFileUploader {
    private val client = HttpClientProvider.client

    fun sendDocument(botToken: String, chatId: String, file: File): RecordingUploadResult {
        if (!file.exists()) {
            return RecordingUploadResult(success = false, isPermanentFailure = true, message = "File no longer exists")
        }
        if (file.length() > MAX_FILE_BYTES) {
            return RecordingUploadResult(
                success = false,
                isPermanentFailure = true,
                message = "File exceeds Telegram's 50MB bot upload limit"
            )
        }

        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("document", file.name, file.asRequestBody(guessMediaType(file.extension)))
                .build()

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendDocument")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val ok = response.isSuccessful && runCatching {
                    JsonParser.parseString(bodyString).asJsonObject.get("ok")?.asBoolean == true
                }.getOrDefault(false)

                if (ok) {
                    RecordingUploadResult(success = true, isPermanentFailure = false, message = "OK ${response.code}")
                } else {
                    val permanent = response.code in 400..499 && response.code != 429
                    RecordingUploadResult(
                        success = false,
                        isPermanentFailure = permanent,
                        message = "HTTP ${response.code}: ${bodyString.take(200)}"
                    )
                }
            }
        } catch (e: Exception) {
            RecordingUploadResult(success = false, isPermanentFailure = false, message = e.message ?: "network error")
        }
    }

    private fun guessMediaType(extension: String) = when (extension.lowercase()) {
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "amr" -> "audio/amr"
        "3gp" -> "audio/3gpp"
        "ogg" -> "audio/ogg"
        else -> "audio/aac"
    }.toMediaType()

    companion object {
        private const val MAX_FILE_BYTES = 50L * 1024 * 1024
    }
}
