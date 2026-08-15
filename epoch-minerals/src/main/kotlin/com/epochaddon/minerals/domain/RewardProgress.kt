package com.epochaddon.minerals.domain

import kotlin.math.abs
import kotlin.math.floor

data class UpcomingRewards<T>(
    val rewards: List<T>,
    val targetPoints: Double,
    val remainingPoints: Double,
)

object RewardProgress {
    fun cycleProgress(currentPoints: Double, threshold: Double): Double {
        if (!currentPoints.isFinite() || !threshold.isFinite() || threshold <= 0.0) {
            return 0.0
        }

        val normalizedPoints = currentPoints.coerceAtLeast(0.0)
        val remainder = normalizedPoints % threshold
        val tolerance = threshold * 1.0e-10
        return if (remainder <= tolerance || threshold - remainder <= tolerance) {
            0.0
        } else {
            remainder
        }
    }

    fun remaining(currentPoints: Double, threshold: Double): Double {
        if (!threshold.isFinite() || threshold <= 0.0) {
            return 0.0
        }

        val progress = cycleProgress(currentPoints, threshold)
        return if (progress == 0.0) threshold else (threshold - progress).coerceAtLeast(0.0)
    }

    fun <T> next(currentPoints: Double, rules: List<RewardRule<T>>): UpcomingRewards<T>? {
        if (!currentPoints.isFinite() || rules.isEmpty()) {
            return null
        }

        val normalizedPoints = currentPoints.coerceAtLeast(0.0)
        val targets = rules.mapNotNull { rule ->
            nextTarget(normalizedPoints, rule.points)
                .takeIf { it.isFinite() }
                ?.let { rule to it }
        }
        val targetPoints = targets.minOfOrNull { it.second } ?: return null
        val rewards = targets
            .filter { (_, target) -> approximatelyEqual(target, targetPoints) }
            .map { (rule, _) -> rule.reward }

        return UpcomingRewards(
            rewards = rewards,
            targetPoints = targetPoints,
            remainingPoints = (targetPoints - normalizedPoints).coerceAtLeast(0.0),
        )
    }

    private fun nextTarget(currentPoints: Double, threshold: Double): Double {
        val completed = floor((currentPoints + threshold * 1.0e-10) / threshold)
        return (completed + 1.0) * threshold
    }

    private fun approximatelyEqual(first: Double, second: Double): Boolean {
        val scale = maxOf(1.0, abs(first), abs(second))
        return abs(first - second) <= scale * 1.0e-10
    }
}
