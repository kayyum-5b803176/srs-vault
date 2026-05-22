package com.srspassword.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srspassword.app.data.ConflictStrategy
import com.srspassword.app.data.ImportResult
import com.srspassword.app.viewmodel.ImportExportState
import com.srspassword.app.viewmodel.PasswordViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

// ── Passphrase strength ───────────────────────────────────────────────────────

private enum class PassphraseStrength(val label: String, val color: @Composable () -> Color) {
    WEAK(   "Weak",      { MaterialTheme.colorScheme.error }),
    FAIR(   "Fair",      { Color(0xFFE65100) }),
    GOOD(   "Good",      { Color(0xFFF9A825) }),
    STRONG( "Strong",    { Color(0xFF2E7D32) }),
    VERY_STRONG("Very Strong", { Color(0xFF1B5E20) })
}

private fun assessStrength(pw: String): PassphraseStrength {
    if (pw.length < 8)  return PassphraseStrength.WEAK
    var score = 0
    if (pw.length >= 12) score++
    if (pw.length >= 16) score++
    if (pw.any { it.isUpperCase() }) score++
    if (pw.any { it.isLowerCase() }) score++
    if (pw.any { it.isDigit() })     score++
    if (pw.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 2 -> PassphraseStrength.WEAK
        score == 3 -> PassphraseStrength.FAIR
        score == 4 -> PassphraseStrength.GOOD
        score == 5 -> PassphraseStrength.STRONG
        else       -> PassphraseStrength.VERY_STRONG
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    onBack: () -> Unit,
    vm    : PasswordViewModel = hiltViewModel()
) {
    val context  = LocalContext.current
    val state    by vm.importExportState.collectAsState()
    val preview  by vm.vaultPreview.collectAsState()

    var exportPassphrase  by remember { mutableStateOf("") }
    var exportConfirm     by remember { mutableStateOf("") }
    var showExportPw      by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }

    var importPassphrase  by remember { mutableStateOf("") }
    var showImportPw      by remember { mutableStateOf(false) }
    var importUri         by remember { mutableStateOf<Uri?>(null) }
    var importRawData     by remember { mutableStateOf<String?>(null) }
    var conflictStrategy  by remember { mutableStateOf(ConflictStrategy.SKIP_DUPLICATES) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val exportStrength  = assessStrength(exportPassphrase)
    val exportMismatch  = exportConfirm.isNotEmpty() && exportPassphrase != exportConfirm

    // File pickers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { vm.exportVault(exportPassphrase, context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importUri = uri
        // Read the file and load preview
        val data = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        importRawData = data
        if (data != null) vm.loadVaultPreview(context, uri)
    }

    // Reset state when leaving
    DisposableEffect(Unit) { onDispose { vm.resetImportExportState() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import / Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        // ── Global working overlay ────────────────────────────────────────────
        if (state is ImportExportState.Working) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text((state as ImportExportState.Working).message,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            return@Scaffold
        }

        // ── Result screens ────────────────────────────────────────────────────
        if (state is ImportExportState.ExportSuccess) {
            ResultScreen(
                icon    = Icons.Default.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                title   = "Export Complete",
                body    = "Your vault has been saved as an encrypted .srsv file.\nKeep the passphrase safe — without it the file is unrecoverable.",
                onDone  = { vm.resetImportExportState(); onBack() }
            )
            return@Scaffold
        }

        if (state is ImportExportState.ImportSuccess) {
            val r = (state as ImportExportState.ImportSuccess).result
            ResultScreen(
                icon    = Icons.Default.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                title   = "Import Complete",
                body    = buildString {
                    appendLine("${r.total} cards processed")
                    appendLine("  Added:    ${r.inserted}")
                    appendLine("  Replaced: ${r.replaced}")
                    append("  Skipped:  ${r.skipped}")
                },
                onDone  = { vm.resetImportExportState(); onBack() }
            )
            return@Scaffold
        }

        if (state is ImportExportState.Failure) {
            ResultScreen(
                icon    = Icons.Default.ErrorOutline,
                iconTint = MaterialTheme.colorScheme.error,
                title   = "Operation Failed",
                body    = (state as ImportExportState.Failure).message,
                onDone  = { vm.resetImportExportState() }
            )
            return@Scaffold
        }

        // ── Main content ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SecurityNotice()

            // ════════════════════════ EXPORT ═════════════════════════════════
            SectionHeader(icon = Icons.Default.FileUpload, title = "Export Vault")

            Text(
                "Creates an encrypted .srsv file. Anyone without the passphrase cannot read it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Passphrase
            PassphraseField(
                value           = exportPassphrase,
                onValueChange   = { exportPassphrase = it },
                label           = "Export Passphrase",
                show            = showExportPw,
                onToggleShow    = { showExportPw = !showExportPw }
            )

            if (exportPassphrase.isNotEmpty()) {
                StrengthBar(strength = exportStrength)
            }

            // Confirm
            PassphraseField(
                value           = exportConfirm,
                onValueChange   = { exportConfirm = it },
                label           = "Confirm Passphrase",
                show            = showExportConfirm,
                onToggleShow    = { showExportConfirm = !showExportConfirm },
                isError         = exportMismatch,
                errorText       = if (exportMismatch) "Passphrases do not match" else null
            )

            val exportReady = exportPassphrase.length >= 8 &&
                              exportPassphrase == exportConfirm &&
                              exportStrength != PassphraseStrength.WEAK

            Button(
                onClick  = { exportLauncher.launch("srs_vault_backup.srsv") },
                enabled  = exportReady,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Export Encrypted Vault")
            }

            if (!exportReady && exportPassphrase.isNotEmpty()) {
                Text(
                    when {
                        exportPassphrase.length < 8 -> "Passphrase must be at least 8 characters"
                        exportMismatch              -> "Passphrases must match"
                        else                        -> "Use a stronger passphrase"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // ════════════════════════ IMPORT ═════════════════════════════════
            SectionHeader(icon = Icons.Default.FileDownload, title = "Import Vault")

            Text(
                "Merges an exported .srsv file into your current vault.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // File picker
            OutlinedButton(
                onClick  = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AttachFile, null)
                Spacer(Modifier.width(8.dp))
                Text(importUri?.lastPathSegment?.takeLast(40) ?: "Select .srsv file")
            }

            // Vault preview card
            if (importUri != null) {
                VaultPreviewCard(preview = preview)

                PassphraseField(
                    value         = importPassphrase,
                    onValueChange = { importPassphrase = it },
                    label         = "Vault Passphrase",
                    show          = showImportPw,
                    onToggleShow  = { showImportPw = !showImportPw }
                )

                // Conflict strategy
                Text("If a card already exists in your vault:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                ConflictStrategyPicker(
                    selected  = conflictStrategy,
                    onSelect  = { conflictStrategy = it }
                )

                Button(
                    onClick  = { showImportConfirm = true },
                    enabled  = importPassphrase.length >= 8,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Decrypt & Import")
                }
            }
        }
    }

    // ── Import confirmation dialog ────────────────────────────────────────────
    if (showImportConfirm && importUri != null && importRawData != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            icon  = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirm Import") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will merge the vault file into your current data.")
                    Text(
                        "Strategy: ${conflictStrategy.displayName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportConfirm = false
                        vm.importVault(importPassphrase, context, importUri!!, conflictStrategy)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Import Now") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SecurityNotice() {
    Card(
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer),
        shape  = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("End-to-End Encrypted", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "AES-256-GCM + PBKDF2-HMAC-SHA256 (310,000 iterations).\n" +
                    "HMAC integrity check on every import.\n" +
                    "Wrong passphrase = unrecoverable data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.85f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    show: Boolean,
    onToggleShow: () -> Unit,
    isError: Boolean = false,
    errorText: String? = null
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label) },
        leadingIcon     = { Icon(Icons.Default.VpnKey, null) },
        trailingIcon    = {
            IconButton(onClick = onToggleShow) {
                Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError         = isError,
        supportingText  = errorText?.let { { Text(it) } },
        singleLine      = true,
        shape           = RoundedCornerShape(14.dp),
        modifier        = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StrengthBar(strength: PassphraseStrength) {
    val levels   = PassphraseStrength.values()
    val idx      = levels.indexOf(strength)
    val fraction = (idx + 1).toFloat() / levels.size
    val color    = strength.color()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress   = { fraction },
            modifier   = Modifier.fillMaxWidth().height(6.dp),
            color      = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text("Passphrase strength: ${strength.label}",
            style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun VaultPreviewCard(preview: com.srspassword.app.encryption.VaultExporter.VaultPreview?) {
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    Card(
        colors   = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
            if (preview != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Vault file detected", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("${preview.cardCount} cards  •  v${preview.version}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Exported: ${sdf.format(Date(preview.exportedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("File selected — enter passphrase to import",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConflictStrategyPicker(
    selected: ConflictStrategy,
    onSelect: (ConflictStrategy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ConflictStrategy.values().forEach { strategy ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = selected == strategy,
                    onClick  = { onSelect(strategy) }
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(strategy.displayName(), style = MaterialTheme.typography.bodyMedium)
                    Text(strategy.description(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ResultScreen(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title   : String,
    body    : String,
    onDone  : () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Card(
                colors   = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(body, modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun ConflictStrategy.displayName() = when (this) {
    ConflictStrategy.SKIP_DUPLICATES -> "Skip duplicates"
    ConflictStrategy.REPLACE_ALL     -> "Replace all"
    ConflictStrategy.KEEP_NEWER      -> "Keep newer"
}

private fun ConflictStrategy.description() = when (this) {
    ConflictStrategy.SKIP_DUPLICATES -> "Existing cards are never overwritten"
    ConflictStrategy.REPLACE_ALL     -> "Imported card always replaces local card"
    ConflictStrategy.KEEP_NEWER      -> "Keep whichever card was reviewed more recently"
}
