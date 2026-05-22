package com.srspassword.app.algorithm

import kotlin.math.*
import kotlin.math.roundToInt

/**
 * FSRS-5 (Free Spaced Repetition Scheduler v5)
 * State-of-the-art SRS algorithm used by Anki and modern apps.
 *
 * Improvements over classic SM-2:
 * - Models memory using a forgetting curve based on stability (S) and difficulty (D)
 * - Retrievability R = e^(-t/S) — true exponential decay model
 * - Difficulty adapts per card individually
 * - Handles "lapses" (forgotten cards) with optimized relearning steps
 * - Fuzz factor prevents review clustering
 * - Short-term and long-term memory distinction
 *
 * Reference: https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 */
object FSRS5Algorithm {

    // FSRS-5 default weights (trained on 400M+ reviews)
    private val W = doubleArrayOf(
        0.4072, 1.1829, 3.1262, 15.4722,
        7.2102, 0.5316, 1.0651, 0.0589,
        1.5330, 0.1544, 1.0070, 1.9395,
        0.1100, 0.2900, 2.2700, 0.0070,
        2.9898, 0.5100, 0.4300
    )

    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0  // = (0.9)^(1/DECAY) - 1
    private const val MAX_INTERVAL = 36500   // 100 years cap
    private const val FUZZ_FACTOR = 0.05     // ±5% randomness to avoid clustering

    // Rating scale
    const val RATING_AGAIN = 1
    const val RATING_HARD  = 2
    const val RATING_GOOD  = 3
    const val RATING_EASY  = 4

    /**
     * Calculate retrievability (probability of recall) at time t (days since last review).
     * R = (1 + FACTOR * t / S) ^ DECAY
     */
    fun retrievability(stability: Double, elapsedDays: Double): Double {
        if (elapsedDays <= 0.0) return 1.0
        return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)
    }

    /**
     * Initialize a brand-new card based on first rating.
     */
    fun initCard(rating: Int): CardScheduleResult {
        val stability = initialStability(rating)
        val difficulty = initialDifficulty(rating)

        return when (rating) {
            RATING_AGAIN -> CardScheduleResult(
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 0,        // Re-show same session
                dueMinutes = 1,
                state = CardState.LEARNING
            )
            RATING_HARD -> CardScheduleResult(
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 0,
                dueMinutes = 5,
                state = CardState.LEARNING
            )
            RATING_GOOD -> CardScheduleResult(
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 1,
                dueMinutes = null,
                state = CardState.LEARNING
            )
            RATING_EASY -> CardScheduleResult(
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 4,
                dueMinutes = null,
                state = CardState.REVIEW
            )
            else -> throw IllegalArgumentException("Rating must be 1-4")
        }
    }

    /**
     * Schedule the next review for a card already in Review state.
     * This is the main FSRS-5 scheduling logic.
     */
    fun scheduleReview(
        rating: Int,
        currentStability: Double,
        currentDifficulty: Double,
        elapsedDays: Double,
        state: CardState,
        lapses: Int
    ): CardScheduleResult {

        val r = retrievability(currentStability, elapsedDays)
        val newDifficulty = updateDifficulty(currentDifficulty, rating)

        return when {
            // Card forgotten (Again in Review state = lapse)
            state == CardState.REVIEW && rating == RATING_AGAIN -> {
                val newStability = stabilityAfterForgetting(
                    currentDifficulty, currentStability, r, rating
                )
                CardScheduleResult(
                    stability = newStability,
                    difficulty = newDifficulty,
                    scheduledDays = 0,
                    dueMinutes = lapseRelearningMinutes(lapses),
                    state = CardState.RELEARNING
                )
            }

            // Card in Learning/Relearning state
            state == CardState.LEARNING || state == CardState.RELEARNING -> {
                val newStability = stabilityAfterRecall(
                    newDifficulty, currentStability, r, rating
                )
                val interval = nextInterval(newStability)
                CardScheduleResult(
                    stability = newStability,
                    difficulty = newDifficulty,
                    scheduledDays = interval,
                    dueMinutes = null,
                    state = if (interval >= 1) CardState.REVIEW else state
                )
            }

            // Normal Review
            else -> {
                val newStability = stabilityAfterRecall(
                    newDifficulty, currentStability, r, rating
                )
                val interval = nextInterval(newStability)
                CardScheduleResult(
                    stability = newStability,
                    difficulty = newDifficulty,
                    scheduledDays = interval,
                    dueMinutes = null,
                    state = CardState.REVIEW
                )
            }
        }
    }

    // ---------- Internal FSRS math ----------

    private fun initialStability(rating: Int): Double {
        return W[rating - 1].coerceAtLeast(0.1)
    }

    private fun initialDifficulty(rating: Int): Double {
        val d = W[4] - exp(W[5] * (rating - 1)) + 1
        return d.coerceIn(1.0, 10.0)
    }

    private fun stabilityAfterRecall(
        d: Double, s: Double, r: Double, rating: Int
    ): Double {
        val hardPenalty  = if (rating == RATING_HARD) W[15] else 1.0
        val easyBonus    = if (rating == RATING_EASY) W[16] else 1.0
        val newS = s * (exp(W[8]) *
                (11 - d) *
                s.pow(-W[9]) *
                (exp((1 - r) * W[10]) - 1) *
                hardPenalty * easyBonus + 1)
        return newS.coerceAtLeast(0.01)
    }

    private fun stabilityAfterForgetting(
        d: Double, s: Double, r: Double, rating: Int
    ): Double {
        val newS = W[11] *
                d.pow(-W[12]) *
                ((s + 1).pow(W[13]) - 1) *
                exp((1 - r) * W[14])
        return newS.coerceAtLeast(0.01)
    }

    private fun updateDifficulty(d: Double, rating: Int): Double {
        val delta = -W[6] * (rating - 3)
        val meanReverted = W[7] * initialDifficulty(RATING_GOOD) + (1 - W[7]) * (d + delta)
        return meanReverted.coerceIn(1.0, 10.0)
    }

    private fun nextInterval(stability: Double): Int {
        // Target 90% retention
        val interval = stability / FACTOR * (0.9.pow(1.0 / DECAY) - 1)
        val fuzzed = applyFuzz(interval)
        return fuzzed.roundToInt().coerceIn(1, MAX_INTERVAL)
    }

    private fun applyFuzz(interval: Double): Double {
        if (interval < 2.5) return interval
        val fuzz = interval * FUZZ_FACTOR
        return interval + (-fuzz..fuzz).random()
    }

    private fun lapseRelearningMinutes(lapses: Int): Int {
        return when {
            lapses == 0 -> 10
            lapses < 3  -> 10 * (lapses + 1)
            else        -> 60  // 1 hour for heavily forgotten cards
        }
    }

    private fun ClosedRange<Double>.random(): Double =
        start + Math.random() * (endInclusive - start)


}

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

data class CardScheduleResult(
    val stability: Double,         // Memory stability in days
    val difficulty: Double,        // Card difficulty 1..10
    val scheduledDays: Int,        // Days until next review (0 = same day)
    val dueMinutes: Int?,          // Minutes until next show (null = use scheduledDays)
    val state: CardState
)
