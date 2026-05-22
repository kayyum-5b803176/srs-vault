package com.srspassword.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.srspassword.app.algorithm.CardState
import java.util.UUID

enum class ReviewType { VISUAL, INPUT }

/**
 * Represents one password entry to be memorized via SRS.
 * The actual password value is AES-256-GCM encrypted at rest.
 */
@Entity(tableName = "password_cards")
data class PasswordCard(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Card metadata (shown as prompt)
    val title: String,
    val username: String,
    val hint: String = "",
    val category: String = "General",
    val tags: String = "",

    // Encrypted password (AES-256-GCM via Android Keystore)
    val encryptedPassword: String,
    val encryptionIv: String,

    // Review mode: VISUAL = flip card, INPUT = type to compare
    val reviewType: ReviewType = ReviewType.VISUAL,

    // FSRS-5 scheduling fields
    val stability: Double = 1.0,
    val difficulty: Double = 5.0,
    val scheduledDays: Int = 0,
    val dueMinutes: Int? = null,
    val state: CardState = CardState.NEW,
    val lapses: Int = 0,
    val repetitions: Int = 0,

    // Timestamps (epoch millis)
    val createdAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val nextDueAt: Long = System.currentTimeMillis(),

    // Stats
    val totalReviews: Int = 0,
    val correctStreak: Int = 0
)

data class PasswordCardSummary(
    val id: String,
    val title: String,
    val username: String,
    val category: String,
    val state: CardState,
    val difficulty: Double,
    val nextDueAt: Long,
    val repetitions: Int,
    val lapses: Int,
    val totalReviews: Int,
    val correctStreak: Int,
    val createdAt: Long,
    val reviewType: ReviewType
)

data class PasswordCardExportDto(
    val id: String,
    val title: String,
    val username: String,
    val hint: String,
    val category: String,
    val tags: String,
    val encryptedPassword: String,
    val encryptionIv: String,
    val reviewType: String,
    val stability: Double,
    val difficulty: Double,
    val scheduledDays: Int,
    val state: String,
    val lapses: Int,
    val repetitions: Int,
    val totalReviews: Int,
    val createdAt: Long,
    val nextDueAt: Long
)

fun PasswordCard.toSummary() = PasswordCardSummary(
    id = id, title = title, username = username, category = category,
    state = state, difficulty = difficulty, nextDueAt = nextDueAt,
    repetitions = repetitions, lapses = lapses, totalReviews = totalReviews,
    correctStreak = correctStreak, createdAt = createdAt, reviewType = reviewType
)

fun PasswordCard.toExportDto() = PasswordCardExportDto(
    id = id, title = title, username = username, hint = hint,
    category = category, tags = tags,
    encryptedPassword = encryptedPassword, encryptionIv = encryptionIv,
    reviewType = reviewType.name,
    stability = stability, difficulty = difficulty,
    scheduledDays = scheduledDays, state = state.name,
    lapses = lapses, repetitions = repetitions,
    totalReviews = totalReviews, createdAt = createdAt, nextDueAt = nextDueAt
)

fun PasswordCardExportDto.toCard() = PasswordCard(
    id = id, title = title, username = username, hint = hint,
    category = category, tags = tags,
    encryptedPassword = encryptedPassword, encryptionIv = encryptionIv,
    reviewType = runCatching { ReviewType.valueOf(reviewType) }.getOrDefault(ReviewType.VISUAL),
    stability = stability, difficulty = difficulty,
    scheduledDays = scheduledDays,
    state = CardState.valueOf(state),
    lapses = lapses, repetitions = repetitions,
    totalReviews = totalReviews, createdAt = createdAt, nextDueAt = nextDueAt
)
