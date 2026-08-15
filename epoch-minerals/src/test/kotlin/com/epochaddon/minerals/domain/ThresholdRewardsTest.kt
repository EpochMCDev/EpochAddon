package com.epochaddon.minerals.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThresholdRewardsTest {

    @Test
    fun `grants every reward whose threshold was crossed`() {
        val rules = listOf(
            RewardRule("coal", 10.0),
            RewardRule("redstone", 10.0),
            RewardRule("lapis", 16.0),
        )

        assertEquals(
            listOf(
                RewardGrant("coal", 1),
                RewardGrant("redstone", 1),
            ),
            ThresholdRewards.crossed(9.0, 10.0, rules),
        )
    }

    @Test
    fun `fractional multipliers cross a threshold once`() {
        val rules = listOf(RewardRule("coal", 10.0))

        assertEquals(
            listOf(RewardGrant("coal", 1)),
            ThresholdRewards.crossed(8.75, 12.5, rules),
        )
        assertEquals(emptyList<RewardGrant<String>>(), ThresholdRewards.crossed(12.5, 13.75, rules))
    }

    @Test
    fun `large point gains grant all completed reward cycles`() {
        val rules = listOf(
            RewardRule("coal", 10.0),
            RewardRule("diamond", 192.0),
            RewardRule("emerald", 512.0),
        )

        assertEquals(
            listOf(
                RewardGrant("coal", 51),
                RewardGrant("diamond", 2),
                RewardGrant("emerald", 1),
            ),
            ThresholdRewards.crossed(0.0, 512.0, rules),
        )
    }

    @Test
    fun `does not grant rewards when points do not increase`() {
        val rules = listOf(RewardRule("coal", 10.0))

        assertEquals(emptyList<RewardGrant<String>>(), ThresholdRewards.crossed(20.0, 20.0, rules))
        assertEquals(emptyList<RewardGrant<String>>(), ThresholdRewards.crossed(20.0, 19.0, rules))
    }
}
