package com.srspassword.app.data

import com.srspassword.app.algorithm.CardState
import com.srspassword.app.algorithm.FSRS5Algorithm
import com.srspassword.app.encryption.PasswordEncryptor
import com.srspassword.app.encryption.VaultExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepository @Inject constructor(
    private val dao: PasswordCardDao,
    private val encryptor: PasswordEncryptor,
    private val exporter: VaultExporter
) {

    // ── Observables ──────────────────────────────────────────────────────────

    fun getAllCards(): Flow<List<PasswordCard>> = dao.getAllCards()

    fun getDueCards(): Flow<List<PasswordCard>> = dao.getDueCards()

    fun getDueCardCount(): Flow<Int> = dao.getDueCardCount()

    fun getNewCards(): Flow<List<PasswordCard>> = dao.getNewCards()

    fun getAllCategories(): Flow<List<String>> = dao.getAllCategories()

    fun searchCards(query: String): Flow<List<PasswordCard>> = dao.searchCards(query)

    fun getTotalCount(): Flow<Int> = dao.getTotalCount()

    fun getMasteredCount(): Flow<Int> = dao.getMasteredCount()

    fun getDashboardStats(): Flow<DashboardStats> =
        dao.getAllCards().map { cards ->
            val now = System.currentTimeMillis()
            DashboardStats(
                total      = cards.size,
                dueNow     = cards.count { it.nextDueAt <= now },
                newCards   = cards.count { it.state == CardState.NEW },
                mastered   = cards.count { it.state == CardState.REVIEW && it.repetitions >= 4 },
                avgDiff    = if (cards.isEmpty()) 0.0 else cards.map { it.difficulty }.average(),
                streakCards = cards.count { it.correctStreak >= 3 }
            )
        }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    suspend fun addPasswordCard(
        title: String,
        username: String,
        plaintextPassword: String,
        hint: String = "",
        category: String = "General",
        tags: String = "",
        reviewType: ReviewType = ReviewType.VISUAL
    ) {
        val (ciphertext, iv) = encryptor.encrypt(plaintextPassword)
        val card = PasswordCard(
            title = title, username = username,
            encryptedPassword = ciphertext, encryptionIv = iv,
            hint = hint, category = category, tags = tags,
            reviewType = reviewType
        )
        dao.insertCard(card)
    }

    suspend fun updatePasswordCard(
        card: PasswordCard,
        newPlaintextPassword: String? = null
    ) {
        val updated = if (newPlaintextPassword != null) {
            val (ciphertext, iv) = encryptor.encrypt(newPlaintextPassword)
            card.copy(encryptedPassword = ciphertext, encryptionIv = iv)
        } else card
        dao.updateCard(updated)
    }

    suspend fun deleteCard(card: PasswordCard) = dao.deleteCard(card)

    suspend fun getCardById(id: String): PasswordCard? = dao.getCardById(id)

    /** Decrypt and return the plaintext password for a card (only when user explicitly views). */
    fun revealPassword(card: PasswordCard): String =
        encryptor.decrypt(card.encryptedPassword, card.encryptionIv)

    // ── SRS Review ───────────────────────────────────────────────────────────

    /**
     * Process a user's rating for a card review.
     * Updates FSRS-5 state and schedules the next due time.
     */
    suspend fun submitReview(card: PasswordCard, rating: Int) {
        val now         = System.currentTimeMillis()
        val elapsedDays = card.lastReviewedAt
            ?.let { TimeUnit.MILLISECONDS.toDays(now - it).toDouble() }
            ?: 0.0

        val result = if (card.state == CardState.NEW) {
            FSRS5Algorithm.initCard(rating)
        } else {
            FSRS5Algorithm.scheduleReview(
                rating           = rating,
                currentStability = card.stability,
                currentDifficulty= card.difficulty,
                elapsedDays      = elapsedDays,
                state            = card.state,
                lapses           = card.lapses
            )
        }

        // Calculate absolute next due timestamp
        val nextDueAt = when {
            result.dueMinutes != null ->
                now + TimeUnit.MINUTES.toMillis(result.dueMinutes.toLong())
            result.scheduledDays > 0 ->
                now + TimeUnit.DAYS.toMillis(result.scheduledDays.toLong())
            else -> now + TimeUnit.MINUTES.toMillis(10)
        }

        val isCorrect = rating >= FSRS5Algorithm.RATING_GOOD

        val updatedCard = card.copy(
            stability        = result.stability,
            difficulty       = result.difficulty,
            scheduledDays    = result.scheduledDays,
            dueMinutes       = result.dueMinutes,
            state            = result.state,
            lapses           = if (rating == FSRS5Algorithm.RATING_AGAIN && card.state == CardState.REVIEW)
                                    card.lapses + 1 else card.lapses,
            repetitions      = card.repetitions + 1,
            lastReviewedAt   = now,
            nextDueAt        = nextDueAt,
            totalReviews     = card.totalReviews + 1,
            correctStreak    = if (isCorrect) card.correctStreak + 1 else 0
        )
        dao.updateCard(updatedCard)
    }

    // ── Export / Import ──────────────────────────────────────────────────────

    /** Export all cards to an encrypted binary blob (base64 string). */
    suspend fun exportVault(passphrase: String): String {
        // Use first() — never collect() on a Room Flow (hangs forever)
        val dtos = dao.getAllCards().first().map { it.toExportDto() }
        return exporter.exportVault(dtos, passphrase)
    }

    /**
     * Peek at vault metadata without decrypting.
     * Safe to call with no passphrase — used to show import preview.
     */
    fun previewVault(base64Data: String) = exporter.previewVault(base64Data)

    /**
     * Decrypt and insert imported cards using the chosen [ConflictStrategy].
     *
     * SKIP_DUPLICATES — if a card with the same ID already exists, leave it untouched.
     * REPLACE_ALL     — imported card always wins (overwrites existing).
     * KEEP_NEWER      — keep whichever card has the more recent [PasswordCard.lastReviewedAt].
     */
    suspend fun importVault(
        base64Data: String,
        passphrase: String,
        strategy: ConflictStrategy = ConflictStrategy.SKIP_DUPLICATES
    ): ImportResult {
        val payload = exporter.importVault(base64Data, passphrase)
            ?: return ImportResult.WrongPassphrase

        return try {
            val incoming = payload.cards.map { it.toCard() }
            var inserted = 0; var skipped = 0; var replaced = 0

            incoming.forEach { incoming ->
                val existing = dao.getCardById(incoming.id)
                when {
                    existing == null -> {
                        dao.insertCard(incoming); inserted++
                    }
                    strategy == ConflictStrategy.REPLACE_ALL -> {
                        dao.insertCard(incoming); replaced++   // REPLACE strategy in DAO
                    }
                    strategy == ConflictStrategy.KEEP_NEWER -> {
                        val incomingTs = incoming.lastReviewedAt ?: 0L
                        val existingTs = existing.lastReviewedAt ?: 0L
                        if (incomingTs > existingTs) { dao.insertCard(incoming); replaced++ }
                        else skipped++
                    }
                    else -> skipped++  // SKIP_DUPLICATES
                }
            }
            ImportResult.Success(total = incoming.size, inserted = inserted,
                replaced = replaced, skipped = skipped)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Unknown error")
        }
    }
}

enum class ConflictStrategy { SKIP_DUPLICATES, REPLACE_ALL, KEEP_NEWER }

data class DashboardStats(
    val total: Int,
    val dueNow: Int,
    val newCards: Int,
    val mastered: Int,
    val avgDiff: Double,
    val streakCards: Int
)

sealed class ImportResult {
    data class Success(
        val total: Int, val inserted: Int, val replaced: Int, val skipped: Int
    ) : ImportResult()
    object WrongPassphrase : ImportResult()
    data class Error(val message: String) : ImportResult()
}
