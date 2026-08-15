package com.epochaddon.common.scoreboard

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID
import java.util.logging.Level

class EpochScoreboardService(
    private val plugin: JavaPlugin,
    private val defaultEnabled: Boolean,
    private val title: Component,
    private val refreshTicks: Long,
) : ScoreboardService, Listener {

    private data class ProviderKey(val owner: Plugin, val id: String)

    private data class RegisteredProvider(
        val key: ProviderKey,
        val order: Int,
        val provider: ScoreboardProvider,
    )

    private data class ProviderFailure(val key: ProviderKey, val playerId: UUID)

    private val providers = mutableMapOf<ProviderKey, RegisteredProvider>()
    private val boards = mutableMapOf<UUID, Scoreboard>()
    private val playerStates = mutableMapOf<UUID, Boolean>()
    private val loggedFailures = mutableSetOf<ProviderFailure>()
    private var refreshTask: BukkitTask? = null
    private var running = false

    fun start() {
        running = true
        plugin.server.pluginManager.registerEvents(this, plugin)
        ensureRefreshTask()
        refreshAll()
    }

    fun stop() {
        running = false
        refreshTask?.cancel()
        refreshTask = null

        clearBoards()
        providers.clear()
        playerStates.clear()
        loggedFailures.clear()
    }

    override fun registerProvider(owner: Plugin, id: String, order: Int, provider: ScoreboardProvider) {
        require(id.isNotBlank()) { "Scoreboard provider id cannot be blank" }
        val key = ProviderKey(owner, id)
        providers[key] = RegisteredProvider(key, order, provider)
        loggedFailures.removeAll { it.key == key }
        refreshAll()
    }

    override fun unregisterProvider(owner: Plugin, id: String) {
        val key = ProviderKey(owner, id)
        providers.remove(key)
        loggedFailures.removeAll { it.key == key }
        refreshAll()
    }

    override fun isEnabled(player: Player): Boolean {
        return playerStates[player.uniqueId] ?: defaultEnabled
    }

    override fun setEnabled(player: Player, enabled: Boolean) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { setEnabled(player, enabled) })
            return
        }

        if (enabled == defaultEnabled) {
            playerStates.remove(player.uniqueId)
        } else {
            playerStates[player.uniqueId] = enabled
        }

        if (running) {
            refresh(player)
        }
    }

    override fun refresh(player: Player) {
        if (!running || !player.isOnline) {
            return
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { refresh(player) })
            return
        }

        if (isEnabled(player)) {
            render(player)
        } else {
            clearBoard(player)
        }
    }

    override fun refreshAll() {
        if (!running) {
            return
        }

        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable(::refreshAll))
            return
        }

        Bukkit.getOnlinePlayers().forEach { player ->
            if (isEnabled(player)) {
                render(player)
            } else {
                clearBoard(player)
            }
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable { refresh(event.player) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        boards.remove(event.player.uniqueId)
        loggedFailures.removeAll { it.playerId == event.player.uniqueId }
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val removedKeys = providers.keys.filter { it.owner === event.plugin }.toSet()
        if (removedKeys.isEmpty()) {
            return
        }

        providers.keys.removeAll(removedKeys)
        loggedFailures.removeAll { it.key in removedKeys }
        refreshAll()
    }

    private fun render(player: Player) {
        val board = boards.getOrPut(player.uniqueId) { createBoard() }
        if (player.scoreboard !== board) {
            player.scoreboard = board
        }

        val objective = board.getObjective(OBJECTIVE_NAME)
            ?: board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title)
        objective.displayName(title)
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.numberFormat(NumberFormat.blank())

        val lines = collectLines(player)
        for (index in 0 until MAX_LINES) {
            val entry = ENTRY_KEYS[index]
            val team = board.getTeam(teamName(index)) ?: board.registerNewTeam(teamName(index)).also {
                it.addEntry(entry)
            }

            if (index < lines.size) {
                team.prefix(lines[index])
                team.suffix(Component.empty())
                objective.getScore(entry).score = MAX_LINES - index
            } else {
                team.prefix(Component.empty())
                team.suffix(Component.empty())
                board.resetScores(entry)
            }
        }
    }

    private fun maintainBoards() {
        if (!running) {
            return
        }

        for (player in Bukkit.getOnlinePlayers()) {
            if (isEnabled(player)) {
                render(player)
            } else {
                clearBoard(player)
            }
        }
    }

    private fun ensureRefreshTask() {
        if (refreshTask != null) {
            return
        }

        refreshTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable(::maintainBoards),
            1L,
            refreshTicks,
        )
    }

    private fun clearBoards() {
        val mainScoreboard = Bukkit.getScoreboardManager().mainScoreboard
        for ((playerId, board) in boards) {
            val player = Bukkit.getPlayer(playerId) ?: continue
            if (player.scoreboard === board) {
                player.scoreboard = mainScoreboard
            }
        }
        boards.clear()
    }

    private fun clearBoard(player: Player) {
        val board = boards.remove(player.uniqueId) ?: return
        loggedFailures.removeAll { it.playerId == player.uniqueId }
        if (player.scoreboard === board) {
            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        }
    }

    private fun createBoard(): Scoreboard {
        return Bukkit.getScoreboardManager().newScoreboard
    }

    private fun collectLines(player: Player): List<Component> {
        val lines = mutableListOf<Component>()
        val registrations = providers.values.sortedWith(
            compareBy<RegisteredProvider> { it.order }
                .thenBy { it.key.owner.name }
                .thenBy { it.key.id },
        )

        for (registration in registrations) {
            val providerLines = providerLines(registration, player)
            if (providerLines.isEmpty()) {
                continue
            }
            if (lines.isNotEmpty()) {
                lines += Component.empty()
            }
            lines += providerLines
            if (lines.size >= MAX_LINES) {
                break
            }
        }
        return lines.take(MAX_LINES)
    }

    private fun providerLines(registration: RegisteredProvider, player: Player): List<Component> {
        val failure = ProviderFailure(registration.key, player.uniqueId)
        return try {
            registration.provider.lines(player).also { loggedFailures.remove(failure) }
        } catch (exception: Exception) {
            if (loggedFailures.add(failure)) {
                plugin.logger.log(
                    Level.WARNING,
                    "计分板提供器 ${registration.key.owner.name}:${registration.key.id} 为玩家 ${player.name} 生成内容失败",
                    exception,
                )
            }
            emptyList()
        }
    }

    private fun teamName(index: Int): String = "epoch_line_$index"

    companion object {
        private const val OBJECTIVE_NAME = "epoch_sidebar"
        private const val MAX_LINES = 15
        private val ENTRY_KEYS = (0 until MAX_LINES).map { "\u00a7${it.toString(16)}" }
    }
}
