package com.epochaddon.minerals.service

import com.epochaddon.common.scoreboard.ScoreboardService
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

data class MiningBoostSnapshot(
    val multiplier: Double,
    val remainingSeconds: Long,
)

class MiningBoostService(
    private val plugin: JavaPlugin,
    private val scoreboardService: ScoreboardService,
) {
    private data class ActiveBoost(
        val multiplier: Double,
        val expiresAtMillis: Long,
    )

    private val activeBoosts = mutableMapOf<UUID, ActiveBoost>()
    private var refreshTask: BukkitTask? = null

    fun start(players: Collection<Player>, multiplier: Double, durationSeconds: Long) {
        require(multiplier.isFinite() && multiplier in 0.0..MAX_MULTIPLIER && multiplier > 0.0) {
            "Multiplier is outside the supported range"
        }
        require(durationSeconds in 1..MAX_DURATION_SECONDS) { "Duration is outside the supported range" }

        val expiresAtMillis = System.currentTimeMillis() + durationSeconds * 1_000L
        val playerIds = players.mapTo(mutableSetOf()) { it.uniqueId }
        playerIds.forEach { playerId ->
            activeBoosts[playerId] = ActiveBoost(multiplier, expiresAtMillis)
        }
        ensureRefreshTask()
        refreshPlayers(playerIds)
    }

    fun stop(players: Collection<Player>): Int {
        val playerIds = players.mapTo(mutableSetOf()) { it.uniqueId }
        var stopped = 0
        playerIds.forEach { playerId ->
            if (activeBoosts.remove(playerId) != null) {
                stopped++
            }
        }
        if (activeBoosts.isEmpty()) {
            cancelRefreshTask()
        }
        refreshPlayers(playerIds)
        return stopped
    }

    fun extend(player: Player, durationSeconds: Long): Boolean {
        require(durationSeconds in 1..MAX_DURATION_SECONDS) { "Duration is outside the supported range" }

        val playerId = player.uniqueId
        val activeBoost = activeBoosts[playerId] ?: return false
        val now = System.currentTimeMillis()
        if (activeBoost.expiresAtMillis <= now) {
            activeBoosts.remove(playerId, activeBoost)
            if (activeBoosts.isEmpty()) {
                cancelRefreshTask()
            }
            return false
        }

        val extensionMillis = durationSeconds * 1_000L
        val expiresAtMillis = activeBoost.expiresAtMillis
            .coerceAtMost(Long.MAX_VALUE - extensionMillis)
            .plus(extensionMillis)
        activeBoosts[playerId] = activeBoost.copy(expiresAtMillis = expiresAtMillis)
        scoreboardService.refresh(player)
        return true
    }

    fun multiplier(playerId: UUID): Double {
        val boost = activeBoosts[playerId] ?: return 1.0
        return if (boost.expiresAtMillis > System.currentTimeMillis()) boost.multiplier else 1.0
    }

    fun snapshot(playerId: UUID): MiningBoostSnapshot? {
        val boost = activeBoosts[playerId] ?: return null
        val remainingMillis = boost.expiresAtMillis - System.currentTimeMillis()
        if (remainingMillis <= 0L) {
            return null
        }
        return MiningBoostSnapshot(
            multiplier = boost.multiplier,
            remainingSeconds = (remainingMillis + 999L) / 1_000L,
        )
    }

    fun close() {
        activeBoosts.clear()
        cancelRefreshTask()
    }

    private fun tick() {
        val affectedPlayers = activeBoosts.keys.toSet()
        val now = System.currentTimeMillis()
        activeBoosts.entries.removeAll { it.value.expiresAtMillis <= now }
        refreshPlayers(affectedPlayers)
        if (activeBoosts.isEmpty()) {
            cancelRefreshTask()
        }
    }

    private fun ensureRefreshTask() {
        if (refreshTask != null || activeBoosts.isEmpty()) {
            return
        }
        refreshTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable(::tick),
            20L,
            20L,
        )
    }

    private fun cancelRefreshTask() {
        refreshTask?.cancel()
        refreshTask = null
    }

    private fun refreshPlayers(playerIds: Collection<UUID>) {
        playerIds.mapNotNull { plugin.server.getPlayer(it) }.forEach(scoreboardService::refresh)
    }

    companion object {
        const val MAX_MULTIPLIER = 10_000.0
        const val MAX_DURATION_SECONDS = 86_400L
    }
}
