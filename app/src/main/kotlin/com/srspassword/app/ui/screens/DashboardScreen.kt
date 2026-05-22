package com.srspassword.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.srspassword.app.viewmodel.PasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartReview  : () -> Unit,
    onViewAllCards : () -> Unit,
    onAddCard      : () -> Unit,
    onOpenSettings : () -> Unit,
    onOpenStats    : () -> Unit,
    vm             : PasswordViewModel = hiltViewModel()
) {
    val stats by vm.dashboardStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SRS Vault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Default.BarChart, "Stats")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCard,
                icon    = { Icon(Icons.Default.Add, "Add") },
                text    = { Text("Add Password") }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {

            // Review Banner
            item {
                if (stats.dueNow > 0) {
                    Card(
                        onClick  = onStartReview,
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape    = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Column {
                                Text(
                                    "${stats.dueNow} cards due",
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Tap to start your review session",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                                )
                            }
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Card(
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape    = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text("All caught up!", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "No reviews due right now. Great work!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Stats Grid row 1
            item {
                Text(
                    "Overview",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard("Total",   stats.total.toString(),    Icons.Default.Folder,      Modifier.weight(1f))
                    StatCard("New",     stats.newCards.toString(), Icons.Default.FiberNew,    Modifier.weight(1f))
                    StatCard("Mastered",stats.mastered.toString(), Icons.Default.EmojiEvents, Modifier.weight(1f))
                }
            }

            // Stats Grid row 2
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard("Due Now",  stats.dueNow.toString(),                  Icons.Default.Schedule,    Modifier.weight(1f))
                    StatCard("Streak",   stats.streakCards.toString(),             Icons.Default.Whatshot,    Modifier.weight(1f))
                    StatCard("Avg Diff", String.format("%.1f", stats.avgDiff),     Icons.Default.Analytics,   Modifier.weight(1f))
                }
            }

            // Quick Actions
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quick Actions",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionRow(
                        icon    = Icons.Default.List,
                        label   = "Browse All Passwords",
                        onClick = onViewAllCards
                    )
                    QuickActionRow(
                        icon    = Icons.Default.School,
                        label   = "Start Review Session",
                        onClick = onStartReview
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape    = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick  = onClick,
        shape    = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
