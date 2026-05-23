package com.srspassword.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srspassword.app.ui.components.PinDots
import com.srspassword.app.ui.components.ScrambledNumPad

private enum class SetupStep { CHOOSE_LENGTH, ENTER_PIN, CONFIRM_PIN }

/**
 * Three-step Master PIN setup screen:
 *
 *  1. Choose PIN length (4 – 12 digits).
 *  2. Enter the PIN on a scrambled numpad.
 *  3. Re-enter (confirm) the PIN; digits re-shuffle if they don't match.
 *
 * @param onPinConfirmed Called with the confirmed PIN string when setup is complete.
 * @param onBack         Called when the user presses the back / cancel button.
 * @param isChangingPin  True when replacing an existing PIN (changes UI copy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onPinConfirmed: (String) -> Unit,
    onBack        : () -> Unit,
    isChangingPin : Boolean = false
) {
    var step        by remember { mutableStateOf(SetupStep.CHOOSE_LENGTH) }
    var pinLength   by remember { mutableIntStateOf(6) }
    var firstPin    by remember { mutableStateOf("") }
    var secondPin   by remember { mutableStateOf("") }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var shuffleKey  by remember { mutableIntStateOf(0) }

    // Current PIN buffer based on step
    val currentPin  = if (step == SetupStep.ENTER_PIN) firstPin else secondPin
    val maxLen      = pinLength

    fun onDigit(d: Char) {
        errorMsg = null
        if (step == SetupStep.ENTER_PIN && firstPin.length < maxLen)
            firstPin += d
        else if (step == SetupStep.CONFIRM_PIN && secondPin.length < maxLen)
            secondPin += d
    }

    fun onBackspace() {
        errorMsg = null
        if (step == SetupStep.ENTER_PIN && firstPin.isNotEmpty())
            firstPin = firstPin.dropLast(1)
        else if (step == SetupStep.CONFIRM_PIN && secondPin.isNotEmpty())
            secondPin = secondPin.dropLast(1)
    }

    fun onConfirm() {
        when (step) {
            SetupStep.CHOOSE_LENGTH -> step = SetupStep.ENTER_PIN

            SetupStep.ENTER_PIN -> {
                if (firstPin.length < 4) {
                    errorMsg = "PIN must be at least 4 digits"
                    return
                }
                step = SetupStep.CONFIRM_PIN
                shuffleKey++          // re-scramble for confirmation entry
            }

            SetupStep.CONFIRM_PIN -> {
                if (secondPin == firstPin) {
                    onPinConfirmed(secondPin)
                } else {
                    errorMsg = "PINs don't match — try again"
                    secondPin = ""
                    shuffleKey++      // re-scramble after mismatch
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isChangingPin) "Change Master PIN" else "Set Master PIN")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App icon
            Surface(
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── Step-specific content ─────────────────────────────────────────
            AnimatedContent(
                targetState      = step,
                transitionSpec   = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label            = "setup_step"
            ) { currentStep ->
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    when (currentStep) {

                        // ── Step 1: choose length ─────────────────────────────
                        SetupStep.CHOOSE_LENGTH -> {
                            Text(
                                "Choose PIN length",
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Longer PINs are more secure.\nYou'll enter this every time biometrics change or the app auto-locks.",
                                style     = MaterialTheme.typography.bodyMedium,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            // Length chips: 4, 6, 8, 10, 12
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.fillMaxWidth(),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                listOf(4, 6, 8, 10, 12).forEach { len ->
                                    FilterChip(
                                        selected = pinLength == len,
                                        onClick  = { pinLength = len },
                                        label    = { Text("$len") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Text(
                                "Selected: $pinLength digits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Button(
                                onClick  = { step = SetupStep.ENTER_PIN },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continue")
                            }
                        }

                        // ── Step 2: enter PIN ─────────────────────────────────
                        SetupStep.ENTER_PIN -> {
                            Text(
                                "Enter your $pinLength-digit PIN",
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Use the scrambled keypad below.\nDigits are randomised for security.",
                                style     = MaterialTheme.typography.bodyMedium,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            PinDots(
                                totalDots   = pinLength,
                                filledCount = firstPin.length
                            )

                            errorMsg?.let { err ->
                                Text(
                                    err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            ScrambledNumPad(
                                shuffleKey     = shuffleKey,
                                onDigit        = ::onDigit,
                                onBackspace    = ::onBackspace,
                                onConfirm      = ::onConfirm,
                                confirmEnabled = firstPin.length >= 4
                            )
                        }

                        // ── Step 3: confirm PIN ───────────────────────────────
                        SetupStep.CONFIRM_PIN -> {
                            Text(
                                "Confirm your PIN",
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Re-enter your $pinLength-digit PIN to confirm.",
                                style     = MaterialTheme.typography.bodyMedium,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            PinDots(
                                totalDots   = pinLength,
                                filledCount = secondPin.length
                            )

                            errorMsg?.let { err ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        err,
                                        modifier = Modifier.padding(12.dp),
                                        color    = MaterialTheme.colorScheme.onErrorContainer,
                                        style    = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            ScrambledNumPad(
                                shuffleKey     = shuffleKey,
                                onDigit        = ::onDigit,
                                onBackspace    = ::onBackspace,
                                onConfirm      = ::onConfirm,
                                confirmEnabled = secondPin.length == pinLength
                            )
                        }
                    }
                }
            }
        }
    }
}
