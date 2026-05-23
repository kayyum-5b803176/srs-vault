package com.srspassword.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srspassword.app.ui.components.PinDots
import com.srspassword.app.ui.components.ScrambledNumPad

/** Why the PIN screen was triggered — drives the header copy. */
enum class PinUnlockReason {
    BIOMETRIC_CHANGED,   // biometric enrollment changed
    AUTO_LOCK_TIMEOUT    // app was idle longer than the configured interval
}

/**
 * PIN unlock screen shown when:
 *  - Biometric enrollment changed (a new finger was added or an old one removed).
 *  - The app was not opened within the auto-lock interval.
 *
 * Digits re-scramble after every wrong attempt.
 *
 * @param reason              Why the screen is showing (drives copy/icon).
 * @param pinLength           Exact length of the stored PIN (controls dots).
 * @param onPinEntered        Called with the raw PIN string; caller validates it.
 * @param onFallbackBiometric Optional: called when "Use biometrics instead" is tapped.
 *                            Pass null to hide that button (e.g. when biometrics changed).
 */
@Composable
fun PinUnlockScreen(
    reason              : PinUnlockReason,
    pinLength           : Int,
    onPinEntered        : (String) -> Boolean,   // returns true = correct, false = wrong
    onFallbackBiometric : (() -> Unit)? = null
) {
    var entered    by remember { mutableStateOf("") }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    var shuffleKey by remember { mutableIntStateOf(0) }

    // Shake animation on wrong PIN
    val shakeOffset = remember { Animatable(0f) }

    suspend fun triggerShake() {
        shakeOffset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 400
                10f  at 50
                -10f at 100
                10f  at 150
                -10f at 200
                6f   at 250
                -6f  at 300
                0f   at 400
            }
        )
    }

    val reasonConfig = when (reason) {
        PinUnlockReason.BIOMETRIC_CHANGED -> ReasonConfig(
            icon     = Icons.Default.Fingerprint,
            headline = "Biometric Change Detected",
            body     = "A fingerprint or face was added or removed.\nEnter your master PIN to verify it's you."
        )
        PinUnlockReason.AUTO_LOCK_TIMEOUT -> ReasonConfig(
            icon     = Icons.Default.Timer,
            headline = "App Auto-Locked",
            body     = "The app locked after being idle.\nEnter your master PIN to continue."
        )
    }

    fun onDigit(d: Char) {
        errorMsg = null
        if (entered.length < pinLength) entered += d
    }

    fun onBackspace() {
        errorMsg = null
        if (entered.isNotEmpty()) entered = entered.dropLast(1)
    }

    fun onConfirm() {
        val pin = entered
        if (pin.length < 4) return
        val correct = onPinEntered(pin)
        if (!correct) {
            errorMsg = "Incorrect PIN — try again"
            entered = ""
            shuffleKey++   // re-scramble
        }
        // If correct, the caller updates auth state and removes this screen.
    }

    // Auto-submit when PIN reaches expected length
    LaunchedEffect(entered) {
        if (entered.length == pinLength) {
            val correct = onPinEntered(entered)
            if (!correct) {
                triggerShake()
                errorMsg = "Incorrect PIN — try again"
                entered = ""
                shuffleKey++
            }
        }
    }

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Reason icon
            Surface(
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = reasonConfig.icon,
                        contentDescription = null,
                        modifier           = Modifier.size(36.dp),
                        tint               = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                "SRS Password Vault",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )

            // Reason banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        reasonConfig.headline,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign  = TextAlign.Center
                    )
                    Text(
                        reasonConfig.body,
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // PIN dots — offset by shake animation
            Row(modifier = Modifier.offset(x = shakeOffset.value.dp)) {
                PinDots(totalDots = pinLength, filledCount = entered.length)
            }

            // Error message
            errorMsg?.let { err ->
                Text(
                    err,
                    color    = MaterialTheme.colorScheme.error,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(1f)
                )
            }

            // Scrambled numpad
            ScrambledNumPad(
                shuffleKey     = shuffleKey,
                onDigit        = ::onDigit,
                onBackspace    = ::onBackspace,
                onConfirm      = ::onConfirm,
                confirmEnabled = entered.length >= 4
            )

            // Optional biometric fallback button
            if (onFallbackBiometric != null) {
                TextButton(onClick = onFallbackBiometric) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Use biometrics instead")
                }
            }
        }
    }
}

// ── Private helper ────────────────────────────────────────────────────────────

private data class ReasonConfig(
    val icon    : ImageVector,
    val headline: String,
    val body    : String
)
