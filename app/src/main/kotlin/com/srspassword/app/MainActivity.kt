package com.srspassword.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.srspassword.app.data.AppPreferences
import com.srspassword.app.navigation.AppNavHost
import com.srspassword.app.navigation.Screen
import com.srspassword.app.security.PinManager
import com.srspassword.app.ui.screens.BiometricLockScreen
import com.srspassword.app.ui.screens.PinSetupScreen
import com.srspassword.app.ui.screens.PinUnlockReason
import com.srspassword.app.ui.screens.PinUnlockScreen
import com.srspassword.app.ui.theme.SRSPasswordTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Authentication state machine:
 *
 *  ┌──────────────────┐
 *  │   App launches   │
 *  └────────┬─────────┘
 *           │
 *     PIN set?  ─── No ──► [BiometricLockScreen]
 *           │
 *          Yes
 *           │
 *     Biometric key
 *     invalidated?  ─── Yes ──► [PinUnlockScreen(BIOMETRIC_CHANGED)]
 *           │                         │ ✓ PIN correct → rebind key → [App]
 *          No                        │ ✗ retry
 *           │
 *     Auto-lock
 *     timeout?  ─── Yes ──► [PinUnlockScreen(AUTO_LOCK_TIMEOUT)]
 *           │                         │ ✓ PIN correct → [App]
 *          No                        │ ✗ retry
 *           │
 *     [BiometricLockScreen]
 *           │ ✓ → [App]
 *
 * "PIN not set + biometric changed" → [PinSetupScreen] forced (must create PIN first).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var pinManager    : PinManager

    // ── Auth state ────────────────────────────────────────────────────────────

    private sealed interface AuthScreen {
        object Biometric              : AuthScreen
        data class PinUnlock(val reason: PinUnlockReason) : AuthScreen
        object ForcedPinSetup         : AuthScreen   // biometric changed but no PIN exists yet
        object App                    : AuthScreen
    }

    private var authScreen          = mutableStateOf<AuthScreen>(AuthScreen.Biometric)
    private var biometricAvailable  = mutableStateOf(false)
    private var biometricError      = mutableStateOf<String?>(null)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyStealthMode()
        checkBiometricAvailability()
        determineInitialAuthScreen()

        setContent {
            SRSPasswordTheme {
                val screen by authScreen
                val bioAvail by biometricAvailable
                val bioError by biometricError

                when (val s = screen) {

                    // ── Forced PIN setup (biometric changed, no PIN set) ──────
                    AuthScreen.ForcedPinSetup -> PinSetupScreen(
                        isChangingPin  = false,
                        onPinConfirmed = { pin ->
                            savePin(pin)
                            // After setup, proceed to normal biometric prompt
                            authScreen.value = AuthScreen.Biometric
                            window.decorView.post { showBiometricPrompt() }
                        },
                        onBack = {
                            // Can't truly go back; show biometric screen without PIN
                            // (user must set PIN to satisfy biometric-changed requirement)
                            // Just re-show the forced setup note.
                        }
                    )

                    // ── PIN unlock ────────────────────────────────────────────
                    is AuthScreen.PinUnlock -> {
                        val pinLen = runBlocking { appPreferences.pinLength.first() }
                        val pinHash = runBlocking { appPreferences.pinHash.first() } ?: ""

                        PinUnlockScreen(
                            reason    = s.reason,
                            pinLength = pinLen,
                            onPinEntered = { enteredPin ->
                                if (pinManager.verifyPin(enteredPin, pinHash)) {
                                    onAuthSuccess(rebindBiometricKey = true)
                                    true
                                } else false
                            },
                            // Only offer biometric fallback for timeout, not for biometric-changed
                            onFallbackBiometric = if (s.reason == PinUnlockReason.AUTO_LOCK_TIMEOUT && bioAvail)
                                { { showBiometricPrompt() } } else null
                        )
                    }

                    // ── Biometric lock ────────────────────────────────────────
                    AuthScreen.Biometric -> BiometricLockScreen(
                        isBiometricAvailable = bioAvail,
                        errorMessage         = bioError,
                        onAuthenticate       = { showBiometricPrompt() },
                        onSkipBiometric      = { onAuthSuccess(rebindBiometricKey = false) }
                    )

                    // ── Main app ──────────────────────────────────────────────
                    AuthScreen.App -> AppNavHost(
                        startDestination = Screen.Dashboard.route
                    )
                }
            }
        }

        if (savedInstanceState == null) {
            window.decorView.post {
                if (authScreen.value == AuthScreen.Biometric) showBiometricPrompt()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check auto-lock when the app returns to foreground
        if (authScreen.value == AuthScreen.App) {
            checkAutoLockOnResume()
        }
    }

    override fun onPause() {
        super.onPause()
        // Record the time the user left the app (only while authenticated)
        if (authScreen.value == AuthScreen.App) {
            lifecycleScope.launch {
                appPreferences.setLastAccessTime(System.currentTimeMillis())
            }
        }
    }

    // ── Auth logic ────────────────────────────────────────────────────────────

    /**
     * Reads persisted preferences *synchronously* (one-shot, tiny DataStore read)
     * to decide which screen to show before any UI is drawn.
     */
    private fun determineInitialAuthScreen() {
        val pinHash        = runBlocking { appPreferences.pinHash.first() }
        val hasPinSet      = !pinHash.isNullOrEmpty()

        if (!hasPinSet) {
            // No PIN configured: go straight to biometric
            authScreen.value = AuthScreen.Biometric
            return
        }

        // Check biometric-enrollment change
        if (pinManager.isBiometricKeyInvalidated()) {
            authScreen.value = AuthScreen.PinUnlock(PinUnlockReason.BIOMETRIC_CHANGED)
            return
        }

        // Check auto-lock timeout
        val autoLockMinutes = runBlocking { appPreferences.autoLockMinutes.first() }
        val lastAccess      = runBlocking { appPreferences.lastAccessTime.first() }

        if (autoLockMinutes > 0) {
            val idleMillis  = System.currentTimeMillis() - lastAccess
            val limitMillis = autoLockMinutes * 60_000L
            if (idleMillis > limitMillis) {
                authScreen.value = AuthScreen.PinUnlock(PinUnlockReason.AUTO_LOCK_TIMEOUT)
                return
            }
        }

        // All clear — show biometric
        authScreen.value = AuthScreen.Biometric
    }

    /**
     * Re-evaluates the auto-lock condition when the app comes back to foreground.
     * Only runs when the user is already inside the app (authenticated).
     */
    private fun checkAutoLockOnResume() {
        lifecycleScope.launch {
            val pinHash         = appPreferences.pinHash.first()
            val hasPinSet       = !pinHash.isNullOrEmpty()
            if (!hasPinSet) return@launch

            val autoLockMinutes = appPreferences.autoLockMinutes.first()
            val lastAccess      = appPreferences.lastAccessTime.first()

            if (autoLockMinutes > 0) {
                val idleMillis  = System.currentTimeMillis() - lastAccess
                val limitMillis = autoLockMinutes * 60_000L
                if (idleMillis > limitMillis) {
                    authScreen.value = AuthScreen.PinUnlock(PinUnlockReason.AUTO_LOCK_TIMEOUT)
                }
            }
        }
    }

    /**
     * Called on every successful authentication (biometric OR PIN).
     * Updates last-access timestamp and, optionally, re-creates the biometric key
     * (so future biometric-change detection has the current enrollment as baseline).
     */
    private fun onAuthSuccess(rebindBiometricKey: Boolean) {
        lifecycleScope.launch {
            appPreferences.setLastAccessTime(System.currentTimeMillis())
        }
        if (rebindBiometricKey) {
            pinManager.createBiometricBoundKey()
        }
        biometricError.value = null
        authScreen.value     = AuthScreen.App
    }

    /** Stores [pin] hashed and re-creates biometric key. */
    private fun savePin(pin: String) {
        lifecycleScope.launch {
            appPreferences.setPinHash(pinManager.hashPin(pin))
            appPreferences.setPinLength(pin.length)
            appPreferences.setLastAccessTime(System.currentTimeMillis())
            pinManager.createBiometricBoundKey()
        }
    }

    // ── Biometric prompt ──────────────────────────────────────────────────────

    private fun checkBiometricAvailability() {
        biometricAvailable.value = when (
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthSuccess(rebindBiometricKey = false)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                biometricError.value = "Authentication failed. Try again."
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                biometricError.value = errString.toString()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        BiometricPrompt(this, executor, callback).authenticate(promptInfo)
    }

    // ── Stealth mode ──────────────────────────────────────────────────────────

    private fun applyStealthMode() {
        lifecycleScope.launch {
            appPreferences.stealthMode.collect { enabled ->
                if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else         window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
