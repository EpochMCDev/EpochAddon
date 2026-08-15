package com.epochaddon.minerals.service

import com.epochaddon.common.scoreboard.ScoreboardService
import com.epochaddon.minerals.config.VeinSettings
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

data class VeinSnapshot(
    val multiplier: Double,
    val remainingSeconds: Long,
)

class VeinService(
    private val plugin: JavaPlugin,
    private val settings: VeinSettings,
    private val messages: MessageService,
    private val scoreboardService: ScoreboardService,
    private val boostService: MiningBoostService,
    private val random: Random = Random.Default,
) {
    private val activeUntil = mutableMapOf<UUID, Long>()

    fun isActive(playerId: UUID): Boolean {
        return snapshot(playerId) != null
    }

    fun snapshot(playerId: UUID): VeinSnapshot? {
        val expiresAt = activeUntil[playerId] ?: return null
        val remainingMillis = expiresAt - System.currentTimeMillis()
        if (remainingMillis <= 0L) {
            activeUntil.remove(playerId, expiresAt)
            return null
        }

        return VeinSnapshot(
            multiplier = settings.multiplier,
            remainingSeconds = (remainingMillis + 999L) / 1_000L,
        )
    }

    fun tryStart(player: Player): Boolean {
        val playerId = player.uniqueId
        if (isActive(playerId)) {
            return true
        }
        if (settings.chance <= 0.0 || random.nextDouble() >= settings.chance) {
            return false
        }

        if (boostService.extend(player, settings.durationSeconds)) {
            messages.showVeinExtended(player, settings.durationSeconds)
            return false
        }

        val expiresAt = System.currentTimeMillis() + settings.durationSeconds * 1_000L
        activeUntil[playerId] = expiresAt
        messages.showVeinStart(player, settings.durationSeconds, settings.multiplier)
        scoreboardService.refresh(player)

        plugin.server.scheduler.runTaskLater(
            plugin,
            Runnable { finish(playerId, expiresAt) },
            settings.durationSeconds * 20L,
        )
        return true
    }

    fun close() {
        activeUntil.clear()
    }

    private fun finish(playerId: UUID, expectedExpiry: Long) {
        if (!activeUntil.remove(playerId, expectedExpiry)) {
            return
        }
        plugin.server.getPlayer(playerId)?.let(scoreboardService::refresh)
    }
}
