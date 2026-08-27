package com.sonuchaudhary.notificationforward.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Provides the app's single encrypted SharedPreferences file and migrates any values written to
 * the old plaintext "notif_settings" file (Telegram bot token, webhook bearer token) into it,
 * once. Pairing/role secrets (RoleStore) are written to the encrypted file only and never touch
 * the legacy plaintext path.
 */
object SecurePrefsMigrator {
    private const val LEGACY_PREFS_NAME = "notif_settings"
    private const val SECURE_PREFS_NAME = "notif_settings_secure"
    private const val KEY_MIGRATED = "migrated_v1"
    private val SECRET_KEYS = setOf("bearer_token", "recording_bot_token")

    @Volatile
    private var instance: SharedPreferences? = null

    fun securePrefs(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    /** Idempotent; safe to call multiple times (e.g. once eagerly from Application.onCreate). */
    fun migrateIfNeeded(context: Context) {
        securePrefs(context)
    }

    private fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val secure = EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        migrateLegacyValues(context, secure)
        return secure
    }

    private fun migrateLegacyValues(context: Context, secure: SharedPreferences) {
        if (secure.getBoolean(KEY_MIGRATED, false)) return

        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        secure.edit {
            legacy.all.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(key, value as Set<String>)
                    }
                }
            }
            putBoolean(KEY_MIGRATED, true)
        }

        // Secrets are now only in the encrypted file — scrub them from the legacy plaintext file.
        legacy.edit {
            SECRET_KEYS.forEach { remove(it) }
        }
    }
}
