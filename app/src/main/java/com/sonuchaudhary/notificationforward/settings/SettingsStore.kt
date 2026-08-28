package com.sonuchaudhary.notificationforward.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sonuchaudhary.notificationforward.security.SecurePrefsMigrator

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = SecurePrefsMigrator.securePrefs(context)

    var forwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_FORWARDING_ENABLED, value) }

    var recordingBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECORDING_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_RECORDING_ENABLED, value) }

    var recordingFolderPath: String
        get() = prefs.getString(KEY_RECORDING_FOLDER_PATH, DEFAULT_RECORDING_FOLDER) ?: DEFAULT_RECORDING_FOLDER
        set(value) = prefs.edit { putString(KEY_RECORDING_FOLDER_PATH, value.trim()) }

    var lastRecordingScanAt: Long
        get() = prefs.getLong(KEY_LAST_RECORDING_SCAN, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_RECORDING_SCAN, value) }

    var dataRetentionDays: Int
        get() = prefs.getInt(KEY_DATA_RETENTION_DAYS, 90)
        set(value) = prefs.edit { putInt(KEY_DATA_RETENTION_DAYS, value.coerceIn(1, 365)) }

    companion object {
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_RECORDING_ENABLED = "recording_backup_enabled"
        private const val KEY_RECORDING_FOLDER_PATH = "recording_folder_path"
        private const val KEY_LAST_RECORDING_SCAN = "last_recording_scan_at"
        private const val KEY_DATA_RETENTION_DAYS = "data_retention_days"
        private const val DEFAULT_RECORDING_FOLDER = "/storage/emulated/0/Music/PhoneRecord"
    }
}
