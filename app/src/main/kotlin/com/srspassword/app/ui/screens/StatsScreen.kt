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
import com.srspassword.app.algorithm.CardState
import com.srspassword.app.viewmodel.PasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    vm    : PasswordViewModel = hiltViewModel()
) {
    val stats by vm.dashboardStats.collectAsState()
    val cards by vm.allCards.collectAsState()

    val stateBreakdown = remember(cards) {
        mapOf(
            "New"        to cards.count { it.state == CardState.NEW },
            "Learning"   to cards.count { it.state == CardState.LEARNING },
            "Review"     to cards.count { it.state == CardState.REVIEW },
            "Relearning" to cards.count { it.state == CardState.RELEARNING }
        )
    }

    val categoryBreakdown = remember(cards) {
        cards.groupBy { it.category }
            .mapValues { it.value.size }.entries
            .sortedByDescending { it.value }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(padding)
        ) {
            item {
                Text("Overview", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigStatCard("Total Cards", stats.total.toString(),   Icons.Default.Folder,      Modifier.weight(1f))
                    BigStatCard("Mastered",    stats.mastered.toString(),Icons.Default.EmojiEvents, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigStatCard("Due Now",  stats.dueNow.toString(),              Icons.Default.Schedule, Modifier.weight(1f))
                    BigStatCard("Avg Diff", String.format("%.1f", stats.avgDiff), Icons.Default.Analytics,Modifier.weight(1f))
                }
            }

            item {
                Text("Card State Breakdown", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        stateBreakdown.forEach { (state, count) ->
                            StateProgressRow(label = state, count = count, total = stats.total)
                        }
                    }
                }
            }

            if (categoryBreakdown.isNotEmpty()) {
                item {
                    Text("By Category", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier            = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            categoryBreakdown.forEach { (category, count) ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(category, style = MaterialTheme.typography.bodyMedium)
                                    Badge { Text(count.toString()) }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Algorithm Info", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
                    shape  = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("FSRS-5 Algorithm", fontWeight = FontWeight.Bold)
                        Text("Memory model: R = (1 + t/S)^(-0.5)",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Target retention: 90%",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Adapts difficulty per-card individually",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Trained on 400M+ real flashcard reviews",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Handles forgetting with optimized relearning",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StateProgressRow(label: String, count: Int, total: Int) {
    val progress = if (total > 0) count.toFloat() / total else 0f
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
