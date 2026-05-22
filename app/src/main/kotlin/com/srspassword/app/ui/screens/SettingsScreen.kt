package com.srspassword.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srspassword.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onImportExport: () -> Unit,
    onBack        : () -> Unit,
    vm            : SettingsViewModel = hiltViewModel()
) {
    val stealthMode by vm.stealthMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Data ──────────────────────────────────────────────────────────
            SettingsGroup(title = "Data") {
                SettingsItem(
                    icon     = Icons.Default.ImportExport,
                    title    = "Import / Export",
                    subtitle = "Backup or restore your encrypted vault",
                    onClick  = onImportExport
                )
            }

            // ── Security ──────────────────────────────────────────────────────
            SettingsGroup(title = "Security") {
                // Stealth mode toggle
                SettingsToggleItem(
                    icon     = Icons.Default.VisibilityOff,
                    title    = "Stealth Mode",
                    subtitle = if (stealthMode)
                        "Screen capture blocked — other apps cannot record this window"
                    else
                        "Screen capture allowed — disable only for debugging",
                    checked  = stealthMode,
                    onCheckedChange = vm::setStealthMode
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon     = Icons.Default.Fingerprint,
                    title    = "Biometric Lock",
                    subtitle = "Always enabled — required to open app"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon     = Icons.Default.Lock,
                    title    = "Encryption",
                    subtitle = "AES-256-GCM via Android Keystore (hardware-backed)"
                )
            }

            // ── Algorithm ─────────────────────────────────────────────────────
            SettingsGroup(title = "Algorithm") {
                SettingsItem(
                    icon     = Icons.Default.Psychology,
                    title    = "SRS Algorithm",
                    subtitle = "FSRS-5 (state-of-the-art, trained on 400M+ reviews)"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon     = Icons.Default.TrackChanges,
                    title    = "Target Retention",
                    subtitle = "90% recall probability (FSRS-5 default)"
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsGroup(title = "About") {
                SettingsItem(
                    icon     = Icons.Default.Info,
                    title    = "Version",
                    subtitle = "SRS Password Vault v1.0.0"
                )
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Tappable or info-only row. Pass onClick = null for info-only. */
@Composable
private fun SettingsItem(
    icon    : ImageVector,
    title   : String,
    subtitle: String,
    onClick : (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Row with a Switch on the right side. */
@Composable
private fun SettingsToggleItem(
    icon           : ImageVector,
    title          : String,
    subtitle       : String,
    checked        : Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
