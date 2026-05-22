package com.srspassword.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srspassword.app.viewmodel.PasswordViewModel
import com.srspassword.app.viewmodel.UiEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    onBack: () -> Unit,
    vm    : PasswordViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var passphrase  by remember { mutableStateOf("") }
    var showPw      by remember { mutableStateOf(false) }
    var exportUri   by remember { mutableStateOf<Uri?>(null) }
    var importUri   by remember { mutableStateOf<Uri?>(null) }

    // Listen for UI events
    LaunchedEffect(Unit) {
        vm.uiEvent.collect { event ->
            if (event is UiEvent.ShowMessage) {
                snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // File pickers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            if (passphrase.length >= 8) {
                vm.exportVault(passphrase, context, it)
            } else {
                scope.launch { snackbarHostState.showSnackbar("Passphrase must be at least 8 characters") }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importUri = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import / Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Security notice
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer),
                shape  = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Security, null,
                        tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("End-to-End Encrypted", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Vault files use AES-256-GCM with PBKDF2 key derivation (310,000 iterations). " +
                            "Without the passphrase, data is unrecoverable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.8f)
                        )
                    }
                }
            }

            // Passphrase input
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Encryption Passphrase") },
                placeholder = { Text("Min. 8 characters (use a strong passphrase!)") },
                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine  = true,
                shape       = RoundedCornerShape(14.dp),
                supportingText = { Text("This passphrase encrypts/decrypts your export file") },
                modifier    = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // Export section
            Text("Export Vault", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(
                "Creates an encrypted .srsv file containing all your password cards and SRS progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    if (passphrase.length >= 8) {
                        exportLauncher.launch("srs_vault_backup.srsv")
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Enter a passphrase (min 8 chars) first") }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Export Encrypted Vault")
            }

            HorizontalDivider()

            // Import section
            Text("Import Vault", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(
                "Import a previously exported .srsv file. Cards will be merged with your current vault.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick  = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text(importUri?.lastPathSegment ?: "Select Vault File")
            }

            if (importUri != null) {
                Button(
                    onClick = {
                        if (passphrase.length >= 8) {
                            vm.importVault(passphrase, context, importUri!!)
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Enter the passphrase used during export") }
                        }
                    },
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
}
