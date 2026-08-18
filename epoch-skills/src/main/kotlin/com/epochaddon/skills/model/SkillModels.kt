package com.epochaddon.skills.model

import org.bukkit.Material
import java.util.UUID

sealed interface SkillRequirement {
    data class Experience(val amount: Long) : SkillRequirement

    data class Counter(
        val key: String,
        val amount: Long,
        val materials: Set<Material>,
    ) : SkillRequirement
}

data class SkillNodeDefinition(
    val id: String,
    val level: Int,
    val name: String,
    val description: List<String>,
    val slot: Int,
    val icon: Material,
    val autoUnlock: Boolean,
    val prerequisites: Set<String>,
    val requirement: SkillRequirement,
    val grants: Set<String>,
)

data class SkillTreeDefinition(
    val id: String,
    val professionId: String,
    val professionName: String,
    val title: String,
    val page: Int,
    val nodes: List<SkillNodeDefinition>,
    val connectionSlots: List<Int>,
)

data class ProfessionProgress(
    var experience: Long = 0L,
    val counters: MutableMap<String, Long> = mutableMapOf(),
    val unlockedNodes: MutableSet<String> = mutableSetOf(),
)

data class PlayerSkillProfile(
    val playerId: UUID,
    var playerName: String,
    val professions: MutableMap<String, ProfessionProgress> = mutableMapOf(),
    val unlocks: MutableSet<String> = mutableSetOf(),
) {
    fun profession(id: String): ProfessionProgress = professions.getOrPut(id) { ProfessionProgress() }
}
