package io.github.thebusybiscuit.slimefun4.epochrebirth.hud

import com.epochaddon.common.scoreboard.ScoreboardProvider
import com.epochaddon.common.scoreboard.ScoreboardService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class RebirthHud(
    private val plugin: Plugin,
    private val scoreboard: ScoreboardService,
    private val store: ResurrectionStore,
    private val config: RebirthConfig,
    private val language: LanguageService
) {

    init {
        scoreboard.registerProvider(plugin, PROVIDER_ID, PROVIDER_ORDER, object : ScoreboardProvider {
            override fun lines(player: Player): List<Component> = scoreboardLines(player)
        })
    }

    fun update(player: Player) {
        scoreboard.refresh(player)
    }

    fun refreshAll() {
        scoreboard.refreshAll()
    }

    fun disable() {
        scoreboard.unregisterProvider(plugin, PROVIDER_ID)
    }

    private fun scoreboardLines(player: Player): List<Component> {
        if (!config.hudEnabled) {
            return emptyList()
        }

        return buildList {
            add(language.component("hud.section-title"))
            Tier.entries.forEach { tier ->
                val count = store.count(player, tier)
                add(language.component("hud.${tier.id}-line", mapOf("count" to count.toString())))
            }
        }
    }

    companion object {
        private const val PROVIDER_ID = "rebirth"
        private const val PROVIDER_ORDER = 200
    }
}
