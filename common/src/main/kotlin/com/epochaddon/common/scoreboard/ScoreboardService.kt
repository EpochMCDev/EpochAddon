package com.epochaddon.common.scoreboard

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

interface ScoreboardService {
    fun registerProvider(
        owner: Plugin,
        id: String,
        options: ScoreboardProviderOptions,
        provider: ScoreboardProvider,
    )

    fun registerProvider(owner: Plugin, id: String, order: Int, provider: ScoreboardProvider) {
        registerProvider(owner, id, ScoreboardProviderOptions(order), provider)
    }

    fun unregisterProvider(owner: Plugin, id: String)

    fun isEnabled(player: Player): Boolean

    fun setEnabled(player: Player, enabled: Boolean)

    fun refresh(player: Player)

    fun refreshAll()
}
