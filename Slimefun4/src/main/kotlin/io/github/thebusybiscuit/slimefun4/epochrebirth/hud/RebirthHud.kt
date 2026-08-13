package io.github.thebusybiscuit.slimefun4.epochrebirth.hud

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID

class RebirthHud(
    private val store: ResurrectionStore,
    private val config: RebirthConfig,
    private val language: LanguageService
) : Listener {

    private val serializer = LegacyComponentSerializer.legacySection()
    private val boards = HashMap<UUID, Scoreboard>()
    private val entriesByPlayer = HashMap<UUID, MutableSet<String>>()

    fun update(player: Player) {
        if (!config.hudEnabled) return
        val board = boards.getOrPut(player.uniqueId) {
            Bukkit.getScoreboardManager().newScoreboard.also { player.scoreboard = it }
        }
        val objective = board.getObjective(OBJECTIVE)
            ?: board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, language.component("hud.title"))
        objective.displaySlot = DisplaySlot.SIDEBAR

        entriesByPlayer[player.uniqueId]?.forEach { board.resetScores(it) }
        entriesByPlayer[player.uniqueId] = mutableSetOf()

        Tier.entries.forEachIndexed { index, tier ->
            val count = store.count(player, tier)
            val line = language.component("hud.${tier.id}-line", mapOf("count" to count.toString()))
            val entry = serializer.serialize(line)
            objective.getScore(entry).score = Tier.entries.size - index
            entriesByPlayer.getValue(player.uniqueId).add(entry)
        }
    }

    fun refreshAll() {
        Bukkit.getOnlinePlayers().forEach { update(it) }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        update(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        boards.remove(event.player.uniqueId)
        entriesByPlayer.remove(event.player.uniqueId)
    }

    fun disable() {
        val main = Bukkit.getScoreboardManager().mainScoreboard
        boards.keys.mapNotNull { Bukkit.getPlayer(it) }.forEach { it.scoreboard = main }
        boards.clear()
        entriesByPlayer.clear()
    }

    companion object {
        private const val OBJECTIVE = "epoch_rebirth"
    }
}
