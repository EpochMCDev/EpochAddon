package com.epochaddon.skills.command

import com.epochaddon.skills.config.SkillSettings
import com.epochaddon.skills.gui.SkillsGuiService
import com.epochaddon.skills.service.DefaultEpochSkillsService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class EpochSkillsCommand(
    private val gui: SkillsGuiService,
    private val skills: DefaultEpochSkillsService,
    private val settings: () -> SkillSettings,
    private val reload: () -> Boolean,
) : TabExecutor {
    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendMessage(text(settings().messages.playerOnly))
                return true
            }
            if (!sender.hasPermission(USE_PERMISSION)) {
                sender.sendMessage(text(settings().messages.noPermission))
                return true
            }
            gui.openRoot(sender)
            return true
        }

        if (args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(text(settings().messages.noPermission))
                return true
            }
            if (args.size != 1) {
                sender.sendMessage(text(settings().messages.resetUsage))
                return true
            }
            val successful = reload()
            val message = if (successful) settings().messages.reloadSuccess else settings().messages.reloadFailure
            sender.sendMessage(text(message))
            return true
        }

        if (args[0].equals("reset", ignoreCase = true)) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(text(settings().messages.noPermission))
                return true
            }
            if (args.size != 2) {
                sender.sendMessage(text(settings().messages.resetUsage))
                return true
            }
            val playerName = args[1]
            val playerId = skills.findPlayerIdByName(playerName)
            if (playerId == null || !skills.resetPlayer(playerId)) {
                sender.sendMessage(
                    text(settings().messages.resetNotFound.replace("{player}", playerName)),
                )
                return true
            }
            sender.sendMessage(
                text(settings().messages.resetSuccess.replace("{player}", playerName)),
            )
            return true
        }

        return false
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): List<String> {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return emptyList()
        }
        if (args.size == 1) {
            return listOf("reload", "reset").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("reset", ignoreCase = true)) {
            return skills.knownPlayerNames().filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun text(raw: String): Component = miniMessage.deserialize("<italic:false>$raw")

    companion object {
        private const val USE_PERMISSION = "epochskills.use"
        private const val ADMIN_PERMISSION = "epochskills.admin"
    }
}
