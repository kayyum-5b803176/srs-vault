package com.srspassword.app.ui.screens

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srspassword.app.algorithm.CardState
import com.srspassword.app.data.PasswordCard
import com.srspassword.app.viewmodel.PasswordViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId : String,
    onEdit : (String) -> Unit,
    onBack : () -> Unit,
    vm     : PasswordViewModel = hiltViewModel()
) {
    val card by vm.selectedCard.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    var revealedPw  by remember { mutableStateOf<String?>(null) }
    var showDelete  by remember { mutableStateOf(false) }
    val clipboard   = LocalClipboardManager.current

    LaunchedEffect(cardId) { vm.loadCard(cardId) }

    if (card == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val c = card!!
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { onEdit(c.id) }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Card(
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(c.title, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        StateChip(c.state)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(c.username, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f))
                    if (c.category.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        AssistChip(onClick = {}, label = { Text(c.category) })
                    }
                }
            }

            // Password section
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Password", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Row {
                            IconButton(onClick = {
                                showPassword = !showPassword
                                if (showPassword && revealedPw == null) {
                                    revealedPw = vm.revealPassword(c)
                                }
                            }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null
                                )
                            }
                            if (showPassword && revealedPw != null) {
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(revealedPw!!))
                                }) {
                                    Icon(Icons.Default.ContentCopy, "Copy")
                                }
                            }
                        }
                    }
                    Text(
                        if (showPassword) (revealedPw ?: "Loading…") else "••••••••••••",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Hint
            if (c.hint.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(c.hint, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // FSRS Stats
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Memory Stats (FSRS-5)", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    DetailRow("Stability",  "${String.format("%.2f", c.stability)} days")
                    DetailRow("Difficulty", "${String.format("%.1f", c.difficulty)} / 10")
                    DetailRow("Repetitions", c.repetitions.toString())
                    DetailRow("Lapses",    c.lapses.toString())
                    DetailRow("Total Reviews", c.totalReviews.toString())
                    DetailRow("Streak",    "${c.correctStreak} correct in a row")
                    DetailRow("Next Due",  sdf.format(Date(c.nextDueAt)))
                    c.lastReviewedAt?.let {
                        DetailRow("Last Reviewed", sdf.format(Date(it)))
                    }
                    DetailRow("Created",   sdf.format(Date(c.createdAt)))
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete Password?") },
            text  = { Text("This will permanently delete \"${c.title}\" and all its review history.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteCard(c); showDelete = false; onBack() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StateChip(state: CardState) {
    val (label, color) = when (state) {
        CardState.NEW        -> "New"        to MaterialTheme.colorScheme.secondary
        CardState.LEARNING   -> "Learning"   to MaterialTheme.colorScheme.tertiary
        CardState.REVIEW     -> "Review"     to MaterialTheme.colorScheme.primary
        CardState.RELEARNING -> "Relearning" to MaterialTheme.colorScheme.error
    }
    SuggestionChip(onClick = {}, label = { Text(label) },
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = color.copy(alpha = 0.15f)))
}
