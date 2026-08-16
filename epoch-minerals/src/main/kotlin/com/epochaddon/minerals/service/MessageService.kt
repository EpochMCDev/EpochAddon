package com.epochaddon.minerals.service

import com.epochaddon.minerals.config.PluginMessages
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

class MessageService(private val messages: PluginMessages) {
    private val miniMessage = MiniMessage.miniMessage()
    private val plainNumberFormat = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT))
    private val compactNumberFormat = DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT))

    fun showVeinStart(player: Player, durationSeconds: Long, multiplier: Double) {
        sendMessage(
            player,
            messages.veinStart,
            mapOf(
                "seconds" to durationSeconds.toString(),
                "multiplier" to formatNumber(multiplier),
            ),
        )
    }

    fun showVeinExtended(player: Player, durationSeconds: Long) {
        sendMessage(
            player,
            messages.veinExtended,
            mapOf("seconds" to durationSeconds.toString()),
        )
    }

    fun showLeaderboard(sender: CommandSender, rankings: List<MiningRankingEntry>) {
        if (rankings.isEmpty()) {
            send(sender, messages.leaderboardEmpty)
            return
        }

        send(sender, messages.leaderboardHeader, mapOf("count" to rankings.size.toString()))
        rankings.forEachIndexed { index, entry ->
            send(
                sender,
                messages.leaderboardEntry,
                mapOf(
                    "rank" to (index + 1).toString(),
                    "player" to entry.playerName,
                    "points" to formatNumber(entry.points),
                ),
            )
        }
    }

    fun showBoostStarted(
        sender: CommandSender,
        targets: String,
        targetCount: Int,
        multiplier: Double,
        durationSeconds: Long,
    ) {
        send(
            sender,
            messages.boostStarted,
            mapOf(
                "targets" to targets,
                "count" to targetCount.toString(),
                "multiplier" to formatNumber(multiplier),
                "seconds" to durationSeconds.toString(),
            ),
        )
    }

    fun showBoostStopped(sender: CommandSender, targets: String, stoppedCount: Int) {
        send(
            sender,
            messages.boostStopped,
            mapOf("targets" to targets, "count" to stoppedCount.toString()),
        )
    }

    fun showBoostNotActive(sender: CommandSender, targets: String) {
        send(sender, messages.boostNotActive, mapOf("targets" to targets))
    }

    fun showCommandUsage(sender: CommandSender) {
        send(sender, messages.commandUsage)
    }

    fun showInvalidMultiplier(sender: CommandSender) {
        send(sender, messages.invalidMultiplier)
    }

    fun showInvalidDuration(sender: CommandSender) {
        send(sender, messages.invalidDuration)
    }

    fun showTargetNotFound(sender: CommandSender) {
        send(sender, messages.targetNotFound)
    }

    fun showNoPermission(sender: CommandSender) {
        send(sender, messages.noPermission)
    }

    private fun sendMessage(player: Player, template: String, placeholders: Map<String, String>) {
        if (template.isBlank()) {
            return
        }
        player.sendMessage(component(template, placeholders))
    }

    private fun send(
        sender: CommandSender,
        template: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        if (template.isBlank()) {
            return
        }
        sender.sendMessage(component(template, placeholders))
    }

    fun component(template: String, placeholders: Map<String, String> = emptyMap()): Component = miniMessage.deserialize(
        placeholders.entries.fold(template) { text, (key, value) -> text.replace("{$key}", value) },
    )

    fun formatNumber(value: Double): String {
        val absolute = abs(value)
        return when {
            absolute >= MILLION -> "${compactNumberFormat.format(value / MILLION)}M"
            absolute >= THOUSAND -> "${compactNumberFormat.format(value / THOUSAND)}k"
            else -> plainNumberFormat.format(value)
        }
    }

    fun formatMultiplier(value: Double): String = plainNumberFormat.format(value)

    companion object {
        private const val THOUSAND = 1_000.0
        private const val MILLION = 1_000_000.0
    }
}
