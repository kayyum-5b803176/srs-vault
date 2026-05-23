package com.srspassword.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srspassword.app.data.AppPreferences
import com.srspassword.app.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs     : AppPreferences,
    private val pinManager: PinManager
) : ViewModel() {

    // ── Stealth mode ──────────────────────────────────────────────────────────

    val stealthMode: StateFlow<Boolean> = prefs.stealthMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setStealthMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setStealthMode(enabled) }
    }

    // ── Master PIN ────────────────────────────────────────────────────────────

    private val pinHash: StateFlow<String?> = prefs.pinHash
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True once a master PIN has been stored. */
    val isPinSet: StateFlow<Boolean> = pinHash
        .map { !it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pinLength: StateFlow<Int> = prefs.pinLength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 6)

    /**
     * Hashes [pin] with a fresh random salt and stores the result.
     * Also (re-)creates the biometric-bound KeyStore key so change-detection
     * starts fresh from this moment.
     * [length] is stored separately so PinUnlockScreen can render the correct
     * number of dot indicators without knowing the plain-text PIN.
     */
    fun savePin(pin: String, length: Int) {
        viewModelScope.launch {
            prefs.setPinHash(pinManager.hashPin(pin))
            prefs.setPinLength(length)
            pinManager.createBiometricBoundKey()
        }
    }

    /** Removes the stored PIN hash and disables auto-lock (requires a PIN). */
    fun clearPin() {
        viewModelScope.launch {
            prefs.setPinHash(null)
            prefs.setAutoLockMinutes(0)
        }
    }

    // ── Auto-lock ─────────────────────────────────────────────────────────────

    /** Auto-lock interval in minutes; 0 = disabled. */
    val autoLockMinutes: StateFlow<Int> = prefs.autoLockMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setAutoLockMinutes(minutes) }
    }
}
