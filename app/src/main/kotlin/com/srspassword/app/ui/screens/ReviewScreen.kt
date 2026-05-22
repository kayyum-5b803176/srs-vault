package com.srspassword.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srspassword.app.algorithm.FSRS5Algorithm
import com.srspassword.app.data.PasswordCard
import com.srspassword.app.data.ReviewType
import com.srspassword.app.viewmodel.PasswordViewModel
import com.srspassword.app.viewmodel.ReviewSessionStats
import com.srspassword.app.viewmodel.UiEvent

// ── Diff result ───────────────────────────────────────────────────────────────

enum class CharMatch { CORRECT, WRONG, MISSING, EXTRA }

data class DiffChar(val char: Char, val match: CharMatch)

/**
 * Character-level diff between [typed] and [actual].
 * Returns a list of DiffChar covering the longer of the two strings,
 * so every character — correct, wrong, missing, or extra — is shown.
 */
fun diffPasswords(typed: String, actual: String): List<DiffChar> {
    val result = mutableListOf<DiffChar>()
    val maxLen = maxOf(typed.length, actual.length)
    for (i in 0 until maxLen) {
        when {
            i >= typed.length  -> result.add(DiffChar(actual[i],  CharMatch.MISSING))
            i >= actual.length -> result.add(DiffChar(typed[i],   CharMatch.EXTRA))
            typed[i] == actual[i] -> result.add(DiffChar(typed[i], CharMatch.CORRECT))
            else               -> result.add(DiffChar(actual[i],  CharMatch.WRONG))
        }
    }
    return result
}

fun List<DiffChar>.toAnnotatedString(
    correctColor: Color,
    wrongColor: Color,
    missingColor: Color,
    extraColor: Color
): AnnotatedString = buildAnnotatedString {
    forEach { dc ->
        val color = when (dc.match) {
            CharMatch.CORRECT -> correctColor
            CharMatch.WRONG   -> wrongColor
            CharMatch.MISSING -> missingColor
            CharMatch.EXTRA   -> extraColor
        }
        pushStyle(SpanStyle(color = color, fontFamily = FontFamily.Monospace,
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            background = color.copy(alpha = 0.12f)))
        append(dc.char)
        pop()
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onFinished: () -> Unit,
    vm: PasswordViewModel = hiltViewModel()
) {
    val currentCard  by vm.currentReviewCard.collectAsState()
    val sessionStats by vm.reviewSessionStats.collectAsState()

    LaunchedEffect(Unit) {
        vm.startReviewSession()
        vm.uiEvent.collect { event ->
            if (event is UiEvent.ReviewSessionComplete) { /* handled inline */ }
        }
    }

    when {
        currentCard == null && sessionStats.total > 0 -> {
            SessionCompleteScreen(stats = sessionStats, onDone = onFinished)
            return
        }
        currentCard == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No cards due!", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onFinished) { Text("Back to Dashboard") }
                }
            }
            return
        }
    }

    val card = currentCard!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review Session", fontWeight = FontWeight.Bold)
                        Text(
                            "${sessionStats.reviewed}/${sessionStats.total} reviewed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinished) { Icon(Icons.Default.Close, "Exit") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(
                progress   = { if (sessionStats.total > 0) sessionStats.reviewed.toFloat() / sessionStats.total else 0f },
                modifier   = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            DifficultyBadge(card = card)

            // Route to the right review mode
            if (card.reviewType == ReviewType.INPUT) {
                InputReviewCard(
                    card       = card,
                    revealPw   = { vm.revealPassword(card) },
                    onRate     = { rating -> vm.submitRating(card, rating) },
                    modifier   = Modifier.weight(1f)
                )
            } else {
                VisualReviewCard(
                    card     = card,
                    revealPw = { vm.revealPassword(card) },
                    onRate   = { rating -> vm.submitRating(card, rating) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Visual (flip-card) mode ───────────────────────────────────────────────────

@Composable
private fun VisualReviewCard(
    card: PasswordCard,
    revealPw: () -> String,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFlipped        by remember(card.id) { mutableStateOf(false) }
    var revealedPassword by remember(card.id) { mutableStateOf<String?>(null) }

    val rotation by animateFloatAsState(
        targetValue   = if (isFlipped) 180f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label         = "flip"
    )

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (rotation <= 90f) {
                FlipCardFace(
                    modifier = Modifier.graphicsLayer { rotationY = rotation },
                    isFront  = true, card = card, revealedPassword = null,
                    onFlip   = { isFlipped = true }
                )
            }
            if (rotation > 90f) {
                val pw = revealedPassword ?: revealPw().also { revealedPassword = it }
                FlipCardFace(
                    modifier = Modifier.graphicsLayer {
                        rotationY = rotation - 180f; cameraDistance = 12f * density
                    },
                    isFront = false, card = card, revealedPassword = pw, onFlip = {}
                )
            }
        }

        if (isFlipped) {
            RatingButtons(onRate = onRate)
        } else {
            OutlinedButton(
                onClick  = { isFlipped = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Text("Reveal Password & Rate", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun FlipCardFace(
    modifier: Modifier,
    isFront: Boolean,
    card: PasswordCard,
    revealedPassword: String?,
    onFlip: () -> Unit
) {
    Card(
        onClick   = onFlip,
        shape     = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier  = modifier.fillMaxWidth().aspectRatio(1.6f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
            if (isFront) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.category, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(card.title, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(card.username, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (card.hint.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Hint: ${card.hint}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Tap to reveal", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Password", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        revealedPassword ?: "...",
                        fontFamily    = FontFamily.Monospace,
                        style         = MaterialTheme.typography.headlineSmall,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign     = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("How well did you remember it?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Input mode ────────────────────────────────────────────────────────────────

/**
 * INPUT mode flow:
 * 1. User sees card prompt (title, username, hint)
 * 2. User types the password in a masked field
 * 3. On submit → character-level diff is shown:
 *      GREEN  = correct character
 *      RED    = wrong character (actual shown)
 *      YELLOW = missing character (not typed)
 *      GRAY   = extra character (typed too many)
 * 4. Rating buttons appear (pre-suggested based on accuracy)
 */
@Composable
private fun InputReviewCard(
    card: PasswordCard,
    revealPw: () -> String,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var typed           by remember(card.id) { mutableStateOf("") }
    var submitted       by remember(card.id) { mutableStateOf(false) }
    var actualPassword  by remember(card.id) { mutableStateOf<String?>(null) }
    var showActual      by remember(card.id) { mutableStateOf(false) }
    val focusRequester  = remember { FocusRequester() }

    // Derived diff — only computed after submit
    val diff: List<DiffChar>? = remember(submitted, typed, actualPassword) {
        if (submitted && actualPassword != null) diffPasswords(typed, actualPassword!!) else null
    }

    // Auto-suggest rating based on character accuracy
    val suggestedRating: Int? = remember(diff, actualPassword) {
        if (diff == null || actualPassword == null) return@remember null
        val correct = diff.count { it.match == CharMatch.CORRECT }
        val total   = actualPassword!!.length
        when {
            correct == total            -> FSRS5Algorithm.RATING_EASY
            correct.toFloat() / total >= 0.85f -> FSRS5Algorithm.RATING_GOOD
            correct.toFloat() / total >= 0.60f -> FSRS5Algorithm.RATING_HARD
            else                        -> FSRS5Algorithm.RATING_AGAIN
        }
    }

    LaunchedEffect(card.id) {
        if (!submitted) focusRequester.requestFocus()
    }

    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Prompt card ───────────────────────────────────────────────────────
        Card(
            shape    = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp).fillMaxWidth()
            ) {
                Text(card.category, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(card.title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(card.username, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card.hint.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Hint: ${card.hint}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
                }
            }
        }

        // ── Input field (hidden after submit) ─────────────────────────────────
        if (!submitted) {
            OutlinedTextField(
                value           = typed,
                onValueChange   = { typed = it },
                label           = { Text("Type the password") },
                leadingIcon     = { Icon(Icons.Default.VisibilityOff, null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        actualPassword = revealPw()
                        submitted = true
                    }
                ),
                singleLine  = true,
                shape       = RoundedCornerShape(14.dp),
                modifier    = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )

            Button(
                onClick  = { actualPassword = revealPw(); submitted = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                enabled  = typed.isNotEmpty()
            ) {
                Text("Submit & Check")
            }
        }

        // ── Diff result ───────────────────────────────────────────────────────
        if (submitted && diff != null && actualPassword != null) {
            val correctColor = MaterialTheme.colorScheme.primary
            val wrongColor   = MaterialTheme.colorScheme.error
            val missingColor = MaterialTheme.colorScheme.tertiary
            val extraColor   = MaterialTheme.colorScheme.onSurfaceVariant

            Card(
                shape    = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Result", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    // Character diff display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = diff.toAnnotatedString(
                                correctColor = correctColor,
                                wrongColor   = wrongColor,
                                missingColor = missingColor,
                                extraColor   = extraColor
                            ),
                            letterSpacing = 1.5.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LegendDot("Correct",  correctColor)
                        LegendDot("Wrong",    wrongColor)
                        LegendDot("Missing",  missingColor)
                        LegendDot("Extra",    extraColor)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Accuracy stat
                    val correct = diff.count { it.match == CharMatch.CORRECT }
                    val total   = actualPassword!!.length
                    val pct     = if (total > 0) correct * 100 / total else 0
                    LinearProgressIndicator(
                        progress   = { if (total > 0) correct.toFloat() / total else 0f },
                        modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color      = when {
                            pct >= 85 -> correctColor
                            pct >= 60 -> MaterialTheme.colorScheme.tertiary
                            else      -> wrongColor
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$correct / $total characters correct ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Toggle reveal actual password
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showActual = !showActual },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            if (showActual) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showActual) "Hide" else "Show actual password")
                    }
                    if (showActual) {
                        Text(
                            actualPassword!!,
                            fontFamily    = FontFamily.Monospace,
                            style         = MaterialTheme.typography.bodyMedium,
                            letterSpacing = 1.sp,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(10.dp)
                        )
                    }
                }
            }

            // ── Rating buttons (suggested rating pre-highlighted) ─────────────
            RatingButtons(
                onRate          = onRate,
                suggestedRating = suggestedRating
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun RatingButtons(
    onRate: (Int) -> Unit,
    suggestedRating: Int? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rate your recall:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (suggestedRating != null) {
                val label = when (suggestedRating) {
                    FSRS5Algorithm.RATING_AGAIN -> "Again"
                    FSRS5Algorithm.RATING_HARD  -> "Hard"
                    FSRS5Algorithm.RATING_GOOD  -> "Good"
                    else                        -> "Easy"
                }
                AssistChip(
                    onClick = {},
                    label   = { Text("Suggested: $label", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RatingBtn(
                label     = "Again",
                color     = MaterialTheme.colorScheme.error,
                suggested = suggestedRating == FSRS5Algorithm.RATING_AGAIN,
                modifier  = Modifier.weight(1f)
            ) { onRate(FSRS5Algorithm.RATING_AGAIN) }

            RatingBtn(
                label     = "Hard",
                color     = MaterialTheme.colorScheme.tertiary,
                suggested = suggestedRating == FSRS5Algorithm.RATING_HARD,
                modifier  = Modifier.weight(1f)
            ) { onRate(FSRS5Algorithm.RATING_HARD) }

            RatingBtn(
                label     = "Good",
                color     = MaterialTheme.colorScheme.primary,
                suggested = suggestedRating == FSRS5Algorithm.RATING_GOOD,
                modifier  = Modifier.weight(1f)
            ) { onRate(FSRS5Algorithm.RATING_GOOD) }

            RatingBtn(
                label     = "Easy",
                color     = MaterialTheme.colorScheme.secondary,
                suggested = suggestedRating == FSRS5Algorithm.RATING_EASY,
                modifier  = Modifier.weight(1f)
            ) { onRate(FSRS5Algorithm.RATING_EASY) }
        }
    }
}

@Composable
private fun RatingBtn(
    label: String,
    color: Color,
    suggested: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val borderMod = if (suggested)
        Modifier.border(2.dp, color, RoundedCornerShape(14.dp))
    else Modifier

    Button(
        onClick        = onClick,
        colors         = ButtonDefaults.buttonColors(
            containerColor = if (suggested) color else color.copy(alpha = 0.55f)
        ),
        shape          = RoundedCornerShape(14.dp),
        modifier       = modifier.height(52.dp).then(borderMod),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DifficultyBadge(card: PasswordCard) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("Stability: ${String.format("%.1f", card.stability)}d") })
        AssistChip(onClick = {}, label = { Text("Diff: ${String.format("%.1f", card.difficulty)}/10") })
        AssistChip(onClick = {}, label = { Text("Rep: ${card.repetitions}") })
    }
}

// ── Session complete ──────────────────────────────────────────────────────────

@Composable
private fun SessionCompleteScreen(stats: ReviewSessionStats, onDone: () -> Unit) {
    val accuracy = if (stats.reviewed > 0) stats.correct * 100 / stats.reviewed else 0
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(if (accuracy >= 80) "Session Complete!" else "Keep Practicing!",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Card(
                colors   = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow("Reviewed", stats.reviewed.toString())
                    StatRow("Correct",  stats.correct.toString())
                    StatRow("Again",    stats.again.toString())
                    StatRow("Accuracy", "$accuracy%")
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Dashboard")
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
