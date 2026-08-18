package com.epochaddon.skills.api

import org.bukkit.entity.Player
import java.util.UUID

interface EpochSkillsService {
    fun hasUnlock(player: Player, unlockId: String): Boolean

    fun hasUnlock(playerId: UUID, unlockId: String): Boolean

    fun experience(playerId: UUID, professionId: String): Long

    fun addExperience(player: Player, professionId: String, amount: Long): Long

    fun sourceExperience(professionId: String, sourceId: String): Long
}

object EpochSkillProfessions {
    const val DIGGING = "gathering.digging"
}

object EpochSkillUnlocks {
    const val DIGGING_RESOURCE_SYSTEM = "epochminerals:resource_system"
    const val DIGGING_NEXT_DROP_DISPLAY = "epochminerals:next_drop_display"
    const val DIGGING_SPEED = "epochskills:digging_speed"
}
