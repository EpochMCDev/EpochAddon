package com.epochaddon.minerals.command

import com.epochaddon.minerals.service.MessageService
import com.epochaddon.minerals.service.PlayerProgressStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class StoneCommand(
    private val progressStore: PlayerProgressStore,
    private val messages: MessageService,
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isNotEmpty()) {
            return false
        }

        messages.showLeaderboard(sender, progressStore.rankings())
        return true
    }
}
