package com.srspassword.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BiometricLockScreen(
    isBiometricAvailable: Boolean,
    errorMessage: String?,
    onAuthenticate: () -> Unit,
    onSkipBiometric: () -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier           = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment   = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // App icon — Lock icon instead of emoji
            Surface(
                shape  = CircleShape,
                color  = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                "SRS Password Vault",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )

            Text(
                "Your passwords, memorized through science.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            if (isBiometricAvailable) {
                Surface(
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(100.dp).scale(scale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = "Biometric",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Button(
                    onClick  = onAuthenticate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Authenticate with Biometrics")
                }
            } else {
                Text(
                    "Biometric authentication not available.\nUsing device PIN/pattern.",
                    color     = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onSkipBiometric, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }

            errorMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        style    = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
