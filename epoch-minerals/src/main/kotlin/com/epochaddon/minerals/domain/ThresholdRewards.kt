package com.epochaddon.minerals.domain

import kotlin.math.floor

data class RewardRule<T>(
    val reward: T,
    val points: Double,
) {
    init {
        require(points.isFinite() && points > 0.0) { "Reward points must be finite and greater than zero" }
    }
}

data class RewardGrant<T>(
    val reward: T,
    val amount: Int,
)

object ThresholdRewards {
    fun <T> crossed(
        previousPoints: Double,
        currentPoints: Double,
        rules: List<RewardRule<T>>,
    ): List<RewardGrant<T>> {
        if (!previousPoints.isFinite() || !currentPoints.isFinite() || currentPoints <= previousPoints) {
            return emptyList()
        }

        return rules.mapNotNull { rule ->
            val previousCount = completedRewards(previousPoints, rule.points)
            val currentCount = completedRewards(currentPoints, rule.points)
            val amount = (currentCount - previousCount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            if (amount > 0) RewardGrant(rule.reward, amount) else null
        }
    }

    private fun completedRewards(points: Double, threshold: Double): Long {
        if (points <= 0.0) {
            return 0L
        }

        // The tolerance only absorbs floating-point drift at an exact configured threshold.
        val adjustedPoints = points + threshold * 1.0e-10
        return floor(adjustedPoints / threshold)
            .coerceAtMost(Long.MAX_VALUE.toDouble())
            .toLong()
    }
}
