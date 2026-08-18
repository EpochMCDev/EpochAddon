package com.epochaddon.skills.service

import com.epochaddon.skills.model.ProfessionProgress
import com.epochaddon.skills.model.SkillNodeDefinition
import com.epochaddon.skills.model.SkillRequirement
import com.epochaddon.skills.model.SkillTreeDefinition
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressionEvaluatorTest {
    private val tree = SkillTreeDefinition(
        id = "digging_skt_01",
        professionId = "gathering.digging",
        professionName = "Digging",
        title = "Digging",
        page = 1,
        nodes = listOf(
            node("digger", 1, emptySet(), SkillRequirement.Counter("stone", 16L, setOf(Material.STONE)), autoUnlock = true),
            node("prospector", 2, setOf("digger"), SkillRequirement.Experience(57L)),
            node("practice", 3, setOf("prospector"), SkillRequirement.Experience(102L)),
        ),
        connectionSlots = emptyList(),
    )

    @Test
    fun `counter unlocks first node only at required amount`() {
        val progress = ProfessionProgress(counters = mutableMapOf("stone" to 15L))
        assertTrue(ProgressionEvaluator.newlyUnlocked(tree, progress).isEmpty())

        progress.counters["stone"] = 16L
        assertEquals(listOf("digger"), ProgressionEvaluator.newlyUnlocked(tree, progress).map { it.id })
    }

    @Test
    fun `experience nodes respect prerequisites and unlock in order`() {
        val progress = ProfessionProgress(experience = 102L, counters = mutableMapOf("stone" to 16L))
        assertEquals(
            listOf("digger"),
            ProgressionEvaluator.newlyUnlocked(tree, progress).map { it.id },
        )
    }

    private fun node(
        id: String,
        level: Int,
        prerequisites: Set<String>,
        requirement: SkillRequirement,
        autoUnlock: Boolean = false,
    ): SkillNodeDefinition {
        return SkillNodeDefinition(
            id = id,
            level = level,
            name = id,
            description = emptyList(),
            slot = level,
            icon = Material.STONE,
            autoUnlock = autoUnlock,
            prerequisites = prerequisites,
            requirement = requirement,
            grants = setOf("unlock:$id"),
        )
    }
}
