package com.epochaddon.minerals.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RewardProgressTest {

    @Test
    fun `reports progress within the current reward cycle`() {
        assertEquals(7.0, RewardProgress.cycleProgress(17.0, 10.0), 1.0e-9)
        assertEquals(0.0, RewardProgress.cycleProgress(20.0, 10.0), 1.0e-9)
    }

    @Test
    fun `reports points remaining until the next reward`() {
        assertEquals(3.0, RewardProgress.remaining(17.0, 10.0), 1.0e-9)
        assertEquals(10.0, RewardProgress.remaining(20.0, 10.0), 1.0e-9)
    }

    @Test
    fun `selects all rewards at the nearest upcoming threshold`() {
        val rules = listOf(
            RewardRule("coal", 10.0),
            RewardRule("redstone", 10.0),
            RewardRule("lapis", 16.0),
        )

        assertEquals(
            UpcomingRewards(listOf("coal", "redstone"), 10.0, 1.0),
            RewardProgress.next(9.0, rules),
        )
    }

    @Test
    fun `moves exact thresholds to the next reward cycle`() {
        val rules = listOf(
            RewardRule("coal", 10.0),
            RewardRule("lapis", 16.0),
        )

        assertEquals(
            UpcomingRewards(listOf("lapis"), 16.0, 6.0),
            RewardProgress.next(10.0, rules),
        )
    }
}
