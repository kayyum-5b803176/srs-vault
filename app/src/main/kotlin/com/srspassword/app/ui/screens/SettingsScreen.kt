package com.srspassword.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

// ── Auto-lock options ─────────────────────────────────────────────────────────

private val AUTO_LOCK_OPTIONS = listOf(
    0     to "Never",
    1     to "1 minute",
    5     to "5 minutes",
    15    to "15 minutes",
    30    to "30 minutes",
    60    to "1 hour",
    240   to "4 hours",
    1440  to "1 day"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onImportExport: () -> Unit,
    onSetupPin    : () -> Unit,   // navigate to PinSetupScreen
    onBack        : () -> Unit,
    vm            : SettingsViewModel = hiltViewModel()
) {
    val stealthMode     by vm.stealthMode.collectAsState()
    val isPinSet        by vm.isPinSet.collectAsState()
    val autoLockMinutes by vm.autoLockMinutes.collectAsState()

    // Dialog states
    var showAlgorithmDialog  by remember { mutableStateOf(false) }
    var showRetentionDialog  by remember { mutableStateOf(false) }
    var showAutoLockDialog   by remember { mutableStateOf(false) }
    var showRemovePinDialog  by remember { mutableStateOf(false) }

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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
                SettingsToggleItem(
                    icon            = Icons.Default.VisibilityOff,
                    title           = "Stealth Mode",
                    subtitle        = if (stealthMode)
                        "Screen capture blocked — other apps cannot record this window"
                    else
                        "Screen capture allowed — disable only for debugging",
                    checked         = stealthMode,
                    onCheckedChange = vm::setStealthMode
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                // Biometric lock info
                SettingsItem(
                    icon     = Icons.Default.Fingerprint,
                    title    = "Biometric Lock",
                    subtitle = "Always enabled — required to open app"
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                // Master PIN
                SettingsItem(
                    icon     = Icons.Default.Pin,
                    title    = "Master PIN",
                    subtitle = if (isPinSet)
                        "PIN set — tap to change"
                    else
                        "Not set — tap to create a fallback PIN",
                    onClick  = onSetupPin
                )

                // Auto-lock (only visible when PIN is set)
                if (isPinSet) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    val lockLabel = AUTO_LOCK_OPTIONS
                        .firstOrNull { it.first == autoLockMinutes }?.second ?: "Custom"

                    SettingsItem(
                        icon     = Icons.Default.Timer,
                        title    = "Auto-Lock",
                        subtitle = "Lock after: $lockLabel",
                        onClick  = { showAutoLockDialog = true }
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Remove PIN
                    SettingsItem(
                        icon     = Icons.Default.LockOpen,
                        title    = "Remove Master PIN",
                        subtitle = "Disables auto-lock and PIN fallback",
                        onClick  = { showRemovePinDialog = true }
                    )
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

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
                    subtitle = "FSRS-5 — tap to learn more",
                    onClick  = { showAlgorithmDialog = true }
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon     = Icons.Default.TrackChanges,
                    title    = "Target Retention",
                    subtitle = "90% recall probability — tap to learn more",
                    onClick  = { showRetentionDialog = true }
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

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Auto-lock picker dialog ───────────────────────────────────────────────
    if (showAutoLockDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            icon  = { Icon(Icons.Default.Timer, null) },
            title = { Text("Auto-Lock Interval") },
            text  = {
                Column {
                    Text(
                        "Lock the app with your master PIN after this period of inactivity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    AUTO_LOCK_OPTIONS.forEach { (minutes, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setAutoLockMinutes(minutes)
                                    showAutoLockDialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = autoLockMinutes == minutes,
                                onClick  = {
                                    vm.setAutoLockMinutes(minutes)
                                    showAutoLockDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Remove PIN confirmation dialog ────────────────────────────────────────
    if (showRemovePinDialog) {
        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            icon  = { Icon(Icons.Default.LockOpen, null) },
            title = { Text("Remove Master PIN?") },
            text  = {
                Text(
                    "This will remove your fallback PIN and disable auto-lock. " +
                    "If biometrics change, you will need to set up a new PIN to regain access.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearPin()
                        showRemovePinDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Remove PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── FSRS-5 Algorithm info dialog ──────────────────────────────────────────
    if (showAlgorithmDialog) {
        AlertDialog(
            onDismissRequest = { showAlgorithmDialog = false },
            icon  = { Icon(Icons.Default.Psychology, null) },
            title = { Text("FSRS-5 Algorithm") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("What is it?",
                        "Free Spaced Repetition Scheduler v5 — the most accurate open-source " +
                        "memory scheduling algorithm, trained on 400M+ real review events.")
                    InfoRow("Memory model",
                        "R = (1 + t/S)^\u22120.5  where R = retrievability, t = days since " +
                        "last review, S = stability (how long memory lasts).")
                    InfoRow("Difficulty",
                        "Each card tracks its own difficulty D (1\u201310). Hard cards get " +
                        "shorter intervals; easy cards grow faster.")
                    InfoRow("vs SM-2",
                        "SM-2 (used by old Anki) uses a fixed ease factor. FSRS-5 models " +
                        "the true forgetting curve and adapts per card — typically 20\u201340% " +
                        "fewer reviews for the same retention.")
                    InfoRow("Ratings",
                        "Again / Hard / Good / Easy — each adjusts both stability and " +
                        "difficulty for that card individually.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlgorithmDialog = false }) { Text("Got it") }
            }
        )
    }

    // ── Target Retention info dialog ──────────────────────────────────────────
    if (showRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            icon  = { Icon(Icons.Default.TrackChanges, null) },
            title = { Text("Target Retention: 90%") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("What it means",
                        "When a card is shown for review, you have a 90% chance of " +
                        "remembering it correctly. FSRS-5 calculates intervals specifically " +
                        "to maintain this probability.")
                    InfoRow("Why 90%?",
                        "Research shows 90% is the sweet spot: high enough to feel fluent, " +
                        "low enough that intervals stay practical (not too short, not too long).")
                    InfoRow("Trade-off",
                        "Higher retention (e.g. 95%) means more frequent reviews. " +
                        "Lower retention (e.g. 80%) means fewer reviews but more forgetting. " +
                        "90% balances effort and memory robustness.")
                    InfoRow("For passwords",
                        "90% is ideal — you will occasionally need a hint for harder " +
                        "passwords, which is better than over-reviewing simple ones.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetentionDialog = false }) { Text("Got it") }
            }
        )
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun InfoRow(label: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.primary
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
