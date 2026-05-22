package com.srspassword.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.srspassword.app.navigation.AppNavHost
import com.srspassword.app.navigation.Screen
import com.srspassword.app.ui.screens.BiometricLockScreen
import com.srspassword.app.ui.theme.SRSPasswordTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var isAuthenticated = mutableStateOf(false)
    private var authError = mutableStateOf<String?>(null)
    private var isBiometricAvailable = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkBiometricAvailability()

        setContent {
            SRSPasswordTheme {
                val authenticated by isAuthenticated
                val error by authError
                val biometricAvail by isBiometricAvailable

                if (authenticated) {
                    AppNavHost(startDestination = Screen.Dashboard.route)
                } else {
                    BiometricLockScreen(
                        isBiometricAvailable = biometricAvail,
                        errorMessage = error,
                        onAuthenticate = { showBiometricPrompt() },
                        onSkipBiometric = {
                            // Allow PIN fallback — navigate to PIN screen
                            isAuthenticated.value = true
                        }
                    )
                }
            }
        }

        // Auto-trigger biometric on launch
        if (savedInstanceState == null) {
            window.decorView.post { showBiometricPrompt() }
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
