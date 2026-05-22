package com.srspassword.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        private val KEY_STEALTH_MODE = booleanPreferencesKey("stealth_mode")
    }

    /** Emits true when FLAG_SECURE / stealth mode is enabled. Defaults to true (secure by default). */
    val stealthMode: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_STEALTH_MODE] ?: true }

    suspend fun setStealthMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_STEALTH_MODE] = enabled }
    }
}
