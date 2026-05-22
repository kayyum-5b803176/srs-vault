package com.srspassword.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onImportExport: () -> Unit,
    onBack: () -> Unit
) {
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
            SettingsGroup(title = "Data") {
                SettingsItem(
                    icon  = Icons.Default.ImportExport,
                    title = "Import / Export",
                    subtitle = "Backup or restore your encrypted vault",
                    onClick = onImportExport
                )
            }

            SettingsGroup(title = "Security") {
                SettingsItem(
                    icon     = Icons.Default.Fingerprint,
                    title    = "Biometric Lock",
                    subtitle = "Always enabled — required to open app"
                )
                SettingsItem(
                    icon     = Icons.Default.Lock,
                    title    = "Encryption",
                    subtitle = "AES-256-GCM via Android Keystore (hardware-backed)"
                )
            }

            SettingsGroup(title = "Algorithm") {
                SettingsItem(
                    icon     = Icons.Default.Psychology,
                    title    = "SRS Algorithm",
                    subtitle = "FSRS-5 (state-of-the-art, trained on 400M+ reviews)"
                )
                SettingsItem(
                    icon     = Icons.Default.TrackChanges,
                    title    = "Target Retention",
                    subtitle = "90% recall probability (FSRS-5 default)"
                )
            }

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

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null   // null = info-only row, non-null = tappable
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
