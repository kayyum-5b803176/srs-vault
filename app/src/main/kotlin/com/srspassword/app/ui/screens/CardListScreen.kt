package com.srspassword.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun CardListScreen(
    onAddCard  : () -> Unit,
    onCardClick: (String) -> Unit,
    onBack     : () -> Unit,
    vm         : PasswordViewModel = hiltViewModel()
) {
    val cards by vm.allCards.collectAsState()
    val query by vm.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Passwords") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCard) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearchQuery,
                placeholder = { Text("Search by title, username, category…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { vm.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (query.isBlank()) "No passwords yet.\nTap + to add one!"
                            else "No results for \"$query\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        PasswordCardItem(card = card, onClick = { onCardClick(card.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordCardItem(card: PasswordCard, onClick: () -> Unit) {
    val sdf  = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val due  = Date(card.nextDueAt)
    val now  = System.currentTimeMillis()
    val isOverdue = card.nextDueAt < now

    Card(
        onClick  = onClick,
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // State indicator circle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (card.state) {
                        CardState.NEW       -> MaterialTheme.colorScheme.secondaryContainer
                        CardState.LEARNING  -> MaterialTheme.colorScheme.tertiaryContainer
                        CardState.REVIEW    -> MaterialTheme.colorScheme.primaryContainer
                        CardState.RELEARNING -> MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            when (card.state) {
                                CardState.NEW       -> "N"
                                CardState.LEARNING  -> "L"
                                CardState.REVIEW    -> "V"
                                CardState.RELEARNING -> "R"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (card.state) {
                                CardState.NEW       -> MaterialTheme.colorScheme.onSecondaryContainer
                                CardState.LEARNING  -> MaterialTheme.colorScheme.onTertiaryContainer
                                CardState.REVIEW    -> MaterialTheme.colorScheme.onPrimaryContainer
                                CardState.RELEARNING -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }

                Column {
                    Text(card.title, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(card.username, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(
                        if (isOverdue) "Overdue: ${sdf.format(due)}" else "Next: ${sdf.format(due)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
