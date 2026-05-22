package com.srspassword.app.data

import com.srspassword.app.algorithm.CardState
import com.srspassword.app.algorithm.FSRS5Algorithm
import com.srspassword.app.encryption.PasswordEncryptor
import com.srspassword.app.encryption.VaultExporter
import kotlinx.coroutines.flow.Flow
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

    suspend fun exportVault(passphrase: String): String {
        val cards = dao.getAllCards()
        // One-shot collect for export
        var result = ""
        cards.collect { list ->
            val dtos = list.map { it.toExportDto() }
            result = exporter.exportVault(dtos, passphrase)
        }
        return result
    }

    suspend fun importVault(base64Data: String, passphrase: String): ImportResult {
        val payload = exporter.importVault(base64Data, passphrase)
            ?: return ImportResult.WrongPassphrase

        return try {
            val cards = payload.cards.map { it.toCard() }
            dao.insertCards(cards)
            ImportResult.Success(cards.size)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Unknown error")
        }
    }
}

data class DashboardStats(
    val total: Int,
    val dueNow: Int,
    val newCards: Int,
    val mastered: Int,
    val avgDiff: Double,
    val streakCards: Int
)

sealed class ImportResult {
    data class Success(val count: Int) : ImportResult()
    object WrongPassphrase : ImportResult()
    data class Error(val message: String) : ImportResult()
}
