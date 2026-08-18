package com.epochaddon.skills.service

import com.epochaddon.skills.model.ProfessionProgress
import com.epochaddon.skills.model.SkillNodeDefinition
import com.epochaddon.skills.model.SkillRequirement
import com.epochaddon.skills.model.SkillTreeDefinition

object ProgressionEvaluator {
    fun newlyUnlocked(
        tree: SkillTreeDefinition,
        progress: ProfessionProgress,
    ): List<SkillNodeDefinition> {
        val knownNodes = progress.unlockedNodes.toMutableSet()
        val newlyUnlocked = mutableListOf<SkillNodeDefinition>()

        var changed: Boolean
        do {
            changed = false
            for (node in tree.nodes.sortedBy { it.level }) {
                if (!node.autoUnlock || node.id in knownNodes || !knownNodes.containsAll(node.prerequisites)) {
                    continue
                }
                if (!requirementMet(node.requirement, progress)) {
                    continue
                }
                knownNodes += node.id
                newlyUnlocked += node
                changed = true
            }
        } while (changed)

        return newlyUnlocked
    }

    fun requirementMet(requirement: SkillRequirement, progress: ProfessionProgress): Boolean {
        return when (requirement) {
            is SkillRequirement.Experience -> progress.experience >= requirement.amount
            is SkillRequirement.Counter -> (progress.counters[requirement.key] ?: 0L) >= requirement.amount
        }
    }
}
