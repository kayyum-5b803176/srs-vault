package com.srspassword.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Scrambled numeric key-pad — identical to how banking apps work.
 *
 * Digits 0-9 are shuffled each time [shuffleKey] changes (use an incrementing
 * integer so callers can re-shuffle after a wrong attempt).
 *
 * Layout (same aspect-ratio as a phone dial-pad but scrambled):
 *
 *   [ d0 ]  [ d1 ]  [ d2 ]
 *   [ d3 ]  [ d4 ]  [ d5 ]
 *   [ d6 ]  [ d7 ]  [ d8 ]
 *   [ ⌫  ]  [ d9 ]  [ ✓  ]
 */
@Composable
fun ScrambledNumPad(
    shuffleKey    : Int     = 0,
    onDigit       : (Char) -> Unit,
    onBackspace   : () -> Unit,
    onConfirm     : () -> Unit,
    confirmEnabled: Boolean = true,
    modifier      : Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Shuffle once per unique shuffleKey value.
    val keys = remember(shuffleKey) { ('0'..'9').toList().shuffled() }

    val grid = listOf(
        keys.subList(0, 3),
        keys.subList(3, 6),
        keys.subList(6, 9)
    )

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        grid.forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { digit ->
                    PinKey(
                        label    = digit.toString(),
                        modifier = Modifier.weight(1f),
                        onClick  = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigit(digit)
                        }
                    )
                }
            }
        }

        // Bottom row: backspace | d9 | confirm
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PinKey(
                icon     = Icons.AutoMirrored.Filled.Backspace,
                modifier = Modifier.weight(1f),
                onClick  = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBackspace()
                }
            )
            PinKey(
                label    = keys[9].toString(),
                modifier = Modifier.weight(1f),
                onClick  = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigit(keys[9])
                }
            )
            PinKey(
                icon        = Icons.Default.Check,
                modifier    = Modifier.weight(1f),
                enabled     = confirmEnabled,
                highlighted = true,
                onClick     = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
                }
            )
        }
    }
}

// ── Dot indicator row ─────────────────────────────────────────────────────────

/**
 * Shows [totalDots] circles; the first [filledCount] are filled (primary color),
 * the rest are outlined (surface-variant).
 */
@Composable
fun PinDots(
    totalDots  : Int,
    filledCount: Int,
    modifier   : Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(totalDots) { i ->
            val filled = i < filledCount
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .then(
                        if (filled) Modifier.background(
                            MaterialTheme.colorScheme.primary, CircleShape
                        )
                        else Modifier.border(
                            1.5.dp, MaterialTheme.colorScheme.outline, CircleShape
                        )
                    )
            )
        }
    }
}

// ── Private: single key button ────────────────────────────────────────────────

@Composable
private fun PinKey(
    modifier   : Modifier = Modifier,
    label      : String? = null,
    icon       : ImageVector? = null,
    enabled    : Boolean = true,
    highlighted: Boolean = false,
    onClick    : () -> Unit
) {
    val containerColor = if (highlighted)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (highlighted)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick  = onClick,
        enabled  = enabled,
        shape    = RoundedCornerShape(14.dp),
        color    = containerColor,
        modifier = modifier
            .height(60.dp)
            .alpha(if (enabled) 1f else 0.35f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                label != null -> Text(
                    text  = label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    color = contentColor
                )
                icon != null  -> Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = contentColor,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
    }
}
