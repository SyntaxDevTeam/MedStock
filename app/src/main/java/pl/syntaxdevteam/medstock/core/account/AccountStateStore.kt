package pl.syntaxdevteam.medstock.core.account

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AccountStateStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(): AccountState {
        val email = preferences.getString(KEY_EMAIL, null).orEmpty()
        val isConnected = email.isNotBlank()
        val driveBackupEnabled = preferences.getBoolean(KEY_DRIVE_BACKUP_ENABLED, false) && isConnected
        val lastBackupEpochMillis = preferences.getLong(KEY_LAST_BACKUP_EPOCH_MILLIS, 0L).takeIf { it > 0L }
        return AccountState(
            isConnected = isConnected,
            email = email,
            avatarLabel = AccountAvatarFormatter.avatarLabel(email),
            avatarUrl = preferences.getString(KEY_AVATAR_URL, null)?.takeIf { isConnected && it.isNotBlank() },
            driveBackupEnabled = driveBackupEnabled,
            lastBackupEpochMillis = lastBackupEpochMillis,
        )
    }

    fun connect(email: String, avatarUrl: String? = null) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) return
        preferences.edit()
            .putString(KEY_EMAIL, normalizedEmail)
            .putBoolean(KEY_DRIVE_BACKUP_ENABLED, preferences.getBoolean(KEY_DRIVE_BACKUP_ENABLED, false))
            .applyAvatarUrl(avatarUrl)
            .apply()
    }

    fun setAvatarUrl(email: String, avatarUrl: String?) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || !getState().email.equals(normalizedEmail, ignoreCase = true)) return
        preferences.edit()
            .applyAvatarUrl(avatarUrl)
            .apply()
    }

    fun disconnect() {
        preferences.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_AVATAR_URL)
            .putBoolean(KEY_DRIVE_BACKUP_ENABLED, false)
            .remove(KEY_LAST_BACKUP_EPOCH_MILLIS)
            .apply()
    }

    fun setDriveBackupEnabled(enabled: Boolean) {
        val state = getState()
        preferences.edit()
            .putBoolean(KEY_DRIVE_BACKUP_ENABLED, enabled && state.isConnected)
            .apply()
    }

    fun markBackupCreated(epochMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_LAST_BACKUP_EPOCH_MILLIS, epochMillis)
            .apply()
    }

    private fun SharedPreferences.Editor.applyAvatarUrl(avatarUrl: String?): SharedPreferences.Editor {
        val normalizedAvatarUrl = avatarUrl?.trim().orEmpty()
        return if (normalizedAvatarUrl.isBlank()) {
            remove(KEY_AVATAR_URL)
        } else {
            putString(KEY_AVATAR_URL, normalizedAvatarUrl)
        }
    }

    companion object {
        private const val PREFS_NAME = "account_state"
        private const val KEY_EMAIL = "email"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_DRIVE_BACKUP_ENABLED = "drive_backup_enabled"
        private const val KEY_LAST_BACKUP_EPOCH_MILLIS = "last_backup_epoch_millis"
    }
}

data class AccountState(
    val isConnected: Boolean,
    val email: String,
    val avatarLabel: String,
    val avatarUrl: String?,
    val driveBackupEnabled: Boolean,
    val lastBackupEpochMillis: Long?,
) {
    fun formattedLastBackup(zoneId: ZoneId = ZoneId.systemDefault()): String? {
        val epochMillis = lastBackupEpochMillis ?: return null
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))
    }
}

object AccountAvatarFormatter {
    fun avatarLabel(email: String): String {
        val localPart = email.substringBefore('@').trim()
        val source = localPart.ifBlank { email.trim() }
        return source.firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            .orEmpty()
            .ifBlank { "?" }
    }
}
