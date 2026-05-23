package com.srspassword.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "srs_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_STEALTH_MODE      = booleanPreferencesKey("stealth_mode")

        // ── Master PIN ────────────────────────────────────────────────────────
        /** "saltHex:sha256Hex" produced by PinManager.hashPin(), or null if not set. */
        private val KEY_PIN_HASH          = stringPreferencesKey("master_pin_hash")
        /** Length chosen during PIN setup (4–12). Used to render the dot row. */
        private val KEY_PIN_LENGTH        = intPreferencesKey("master_pin_length")

        // ── Auto-lock ─────────────────────────────────────────────────────────
        /** Minutes of inactivity before the app locks.  0 = disabled (never). */
        private val KEY_AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        /** Epoch-millis of the last successful authentication / foreground entry. */
        private val KEY_LAST_ACCESS_TIME  = longPreferencesKey("last_access_time")
    }

    // ── Stealth mode ──────────────────────────────────────────────────────────

    /** Emits true when FLAG_SECURE / stealth mode is enabled. Defaults to true. */
    val stealthMode: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_STEALTH_MODE] ?: true }

    suspend fun setStealthMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STEALTH_MODE] = enabled }
    }

    // ── Master PIN ────────────────────────────────────────────────────────────

    /** Emits the stored pin hash, or null when no PIN has been set yet. */
    val pinHash: Flow<String?> = context.dataStore.data
        .map { it[KEY_PIN_HASH] }

    suspend fun setPinHash(hash: String?) {
        context.dataStore.edit { prefs ->
            if (hash != null) prefs[KEY_PIN_HASH] = hash
            else prefs.remove(KEY_PIN_HASH)
        }
    }

    /** Emits the number of digits the user chose (4–12). Defaults to 6. */
    val pinLength: Flow<Int> = context.dataStore.data
        .map { it[KEY_PIN_LENGTH] ?: 6 }

    suspend fun setPinLength(length: Int) {
        context.dataStore.edit { it[KEY_PIN_LENGTH] = length.coerceIn(4, 12) }
    }

    // ── Auto-lock ─────────────────────────────────────────────────────────────

    /** Emits the auto-lock interval in minutes. 0 means "never lock". */
    val autoLockMinutes: Flow<Int> = context.dataStore.data
        .map { it[KEY_AUTO_LOCK_MINUTES] ?: 0 }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_AUTO_LOCK_MINUTES] = minutes }
    }

    /** Emits the epoch-millis timestamp of the last successful unlock. */
    val lastAccessTime: Flow<Long> = context.dataStore.data
        .map { it[KEY_LAST_ACCESS_TIME] ?: 0L }

    suspend fun setLastAccessTime(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_ACCESS_TIME] = epochMillis }
    }
}
