package com.epochaddon.common.scoreboard

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

interface ScoreboardProvider {
    fun lines(player: Player): List<Component>
}
