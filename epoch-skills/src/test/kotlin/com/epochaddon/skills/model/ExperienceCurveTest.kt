package com.epochaddon.skills.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExperienceCurveTest {
    @Test
    fun `finds current level and next threshold`() {
        val curve = ExperienceCurve(mapOf(1 to 30L, 2 to 57L, 3 to 102L))

        assertEquals(0, curve.level(29L))
        assertEquals(1, curve.level(30L))
        assertEquals(2, curve.level(80L))
        assertEquals(30L, curve.nextThreshold(0L))
        assertEquals(102L, curve.nextThreshold(57L))
        assertNull(curve.nextThreshold(102L))
    }
}
