package com.epochaddon.minerals.command

import com.epochaddon.minerals.service.MessageService
import com.epochaddon.minerals.service.MiningBoostService
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class MiningPointsCommand(
    private val boostService: MiningBoostService,
    private val messages: MessageService,
) : TabExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission(PERMISSION)) {
            messages.showNoPermission(sender)
            return true
        }

        if (args.size == 2 && args[1].equals("stop", ignoreCase = true)) {
            val targets = resolveTargets(sender, args[0])
            if (targets.isEmpty()) {
                messages.showTargetNotFound(sender)
                return true
            }

            val stopped = boostService.stop(targets)
            if (stopped > 0) {
                messages.showBoostStopped(sender, targetDescription(args[0], targets), stopped)
            } else {
                messages.showBoostNotActive(sender, targetDescription(args[0], targets))
            }
            return true
        }

        if (args.size != 3) {
            messages.showCommandUsage(sender)
            return true
        }

        val targets = resolveTargets(sender, args[0])
        if (targets.isEmpty()) {
            messages.showTargetNotFound(sender)
            return true
        }

        val multiplier = args[1].toDoubleOrNull()
        if (
            multiplier == null ||
            !multiplier.isFinite() ||
            multiplier <= 0.0 ||
            multiplier > MiningBoostService.MAX_MULTIPLIER
        ) {
            messages.showInvalidMultiplier(sender)
            return true
        }

        val durationSeconds = args[2].toLongOrNull()
        if (durationSeconds == null || durationSeconds !in 1..MiningBoostService.MAX_DURATION_SECONDS) {
            messages.showInvalidDuration(sender)
            return true
        }

        boostService.start(targets, multiplier, durationSeconds)
        messages.showBoostStarted(
            sender,
            targetDescription(args[0], targets),
            targets.size,
            multiplier,
            durationSeconds,
        )
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>,
    ): List<String> {
        if (!sender.hasPermission(PERMISSION)) {
            return emptyList()
        }

        return when (args.size) {
            1 -> (listOf("@a") + Bukkit.getOnlinePlayers().map { it.name })
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> listOf("2", "3", "stop").filter { it.startsWith(args[1], ignoreCase = true) }
            3 -> listOf("30", "60", "300").filter { it.startsWith(args[2]) }
            else -> emptyList()
        }
    }

    private fun resolveTargets(sender: CommandSender, input: String): List<Player> {
        val selected = if (input.startsWith("@")) {
            runCatching { Bukkit.selectEntities(sender, input) }
                .getOrElse { return emptyList() }
                .filterIsInstance<Player>()
        } else {
            listOfNotNull(Bukkit.getPlayerExact(input))
        }
        return selected.distinctBy { it.uniqueId }
    }

    private fun targetDescription(input: String, targets: List<Player>): String {
        return if (input.startsWith("@") || targets.size > 1) {
            "${targets.size} 名玩家"
        } else {
            targets.single().name
        }
    }

    companion object {
        private const val PERMISSION = "epochminerals.admin"
    }
}
