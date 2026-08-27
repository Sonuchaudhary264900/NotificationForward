package com.sonuchaudhary.notificationforward.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sonuchaudhary.notificationforward.security.SecurePrefsMigrator
import java.util.UUID

enum class DeviceRole { NONE, PARENT, CHILD }

/**
 * Local device identity + pairing state. Written only to the encrypted prefs file
 * (see SecurePrefsMigrator) — pairing/family identifiers never touch plaintext storage.
 */
class RoleStore(context: Context) {
    private val prefs: SharedPreferences = SecurePrefsMigrator.securePrefs(context)

    /** Stable per-install identifier, generated once on first access. */
    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            val generated = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_DEVICE_ID, generated) }
            return generated
        }

    var role: DeviceRole
        get() = DeviceRole.valueOf(prefs.getString(KEY_ROLE, DeviceRole.NONE.name)!!)
        set(value) = prefs.edit { putString(KEY_ROLE, value.name) }

    var familyId: String?
        get() = prefs.getString(KEY_FAMILY_ID, null)
        set(value) = prefs.edit { putString(KEY_FAMILY_ID, value) }

    var pairingStatus: String
        get() = prefs.getString(KEY_PAIRING_STATUS, "PENDING") ?: "PENDING"
        set(value) = prefs.edit { putString(KEY_PAIRING_STATUS, value) }

    /** Set only after the child device's consent screen has been explicitly accepted. */
    var consentAcceptedAt: Long
        get() = prefs.getLong(KEY_CONSENT_ACCEPTED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_CONSENT_ACCEPTED_AT, value) }

    var lastFcmToken: String?
        get() = prefs.getString(KEY_LAST_FCM_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_LAST_FCM_TOKEN, value) }

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, android.os.Build.MODEL ?: "Device") ?: "Device"
        set(value) = prefs.edit { putString(KEY_DISPLAY_NAME, value.trim()) }

    val isPaired: Boolean
        get() = role != DeviceRole.NONE && familyId != null && pairingStatus == "ACTIVE"

    val hasConsented: Boolean
        get() = consentAcceptedAt > 0L

    fun reset() {
        prefs.edit {
            remove(KEY_ROLE)
            remove(KEY_FAMILY_ID)
            remove(KEY_PAIRING_STATUS)
            remove(KEY_CONSENT_ACCEPTED_AT)
            // deviceId and lastFcmToken intentionally preserved across a re-pair.
        }
    }

    companion object {
        private const val KEY_DEVICE_ID = "role_device_id"
        private const val KEY_ROLE = "role_role"
        private const val KEY_FAMILY_ID = "role_family_id"
        private const val KEY_PAIRING_STATUS = "role_pairing_status"
        private const val KEY_CONSENT_ACCEPTED_AT = "role_consent_accepted_at"
        private const val KEY_LAST_FCM_TOKEN = "role_last_fcm_token"
        private const val KEY_DISPLAY_NAME = "role_display_name"
    }
}
