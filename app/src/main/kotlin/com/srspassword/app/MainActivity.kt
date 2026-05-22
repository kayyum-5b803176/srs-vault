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
import com.srspassword.app.ui.screens.BiometricLockScreen
import com.srspassword.app.ui.theme.SRSPasswordTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    private var isAuthenticated     = mutableStateOf(false)
    private var authError           = mutableStateOf<String?>(null)
    private var isBiometricAvailable = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply stealth mode before any UI is drawn — must be set before setContent
        applyStealthMode()

        checkBiometricAvailability()

        setContent {
            SRSPasswordTheme {
                val authenticated  by isAuthenticated
                val error          by authError
                val biometricAvail by isBiometricAvailable

                if (authenticated) {
                    AppNavHost(startDestination = Screen.Dashboard.route)
                } else {
                    BiometricLockScreen(
                        isBiometricAvailable = biometricAvail,
                        errorMessage         = error,
                        onAuthenticate       = { showBiometricPrompt() },
                        onSkipBiometric      = { isAuthenticated.value = true }
                    )
                }
            }
        }

        if (savedInstanceState == null) {
            window.decorView.post { showBiometricPrompt() }
        }
    }

    /**
     * Reads the persisted stealth preference and applies or clears FLAG_SECURE.
     * Called once on create, and re-called whenever the user toggles the setting
     * via [refreshStealthMode].
     *
     * FLAG_SECURE effects:
     *   - Blocks screenshots (power + volume-down)
     *   - Blocks screen recorders and casting
     *   - Replaces the app thumbnail in the recent-apps switcher with a blank screen
     *   - Prevents other apps with MEDIA_PROJECTION permission from capturing this window
     */
    private fun applyStealthMode() {
        lifecycleScope.launch {
            appPreferences.stealthMode.collect { enabled ->
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(this)
        isBiometricAvailable.value = when (
            biometricManager.canAuthenticate(
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
                isAuthenticated.value = true
                authError.value = null
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                authError.value = "Authentication failed. Try again."
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                authError.value = errString.toString()
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
