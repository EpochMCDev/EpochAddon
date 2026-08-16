package com.epochaddon.common.command

import com.epochaddon.common.scoreboard.ScoreboardService
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class ScoreboardCommand(
    private val scoreboardService: ScoreboardService,
    private val reloadAction: () -> Boolean,
    private val providerStatus: () -> List<String>,
) : TabExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        if (args.size == 1 && args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission(PERMISSION)) {
                sender.sendMessage(Component.text("你没有权限重载统一计分板。"))
                return true
            }
            val reloaded = reloadAction()
            sender.sendMessage(Component.text(if (reloaded) "统一计分板配置已重载。" else "统一计分板配置重载失败，请检查控制台。"))
            return true
        }

        if (args.size == 1 && args[0].equals("providers", ignoreCase = true)) {
            if (!sender.hasPermission(PERMISSION)) {
                sender.sendMessage(Component.text("你没有权限查看计分板提供器。"))
                return true
            }
            val providers = providerStatus()
            sender.sendMessage(Component.text("已注册计分板提供器："))
            if (providers.isEmpty()) {
                sender.sendMessage(Component.text("- 无"))
            } else {
                providers.forEach { sender.sendMessage(Component.text("- $it")) }
            }
            return true
        }

        if (args.isEmpty() || args.size > 2) {
            sendUsage(sender, label)
            return true
        }

        val action = args.last().lowercase()
        if (action !in ACTIONS) {
            sendUsage(sender, label)
            return true
        }

        val targets = if (args.size == 1) {
            val player = sender as? Player
            if (player == null) {
                sender.sendMessage(Component.text("控制台请指定玩家：/$label <玩家|@a> <on|off|toggle|status>"))
                return true
            }
            listOf(player)
        } else {
            if (!sender.hasPermission(PERMISSION)) {
                sender.sendMessage(Component.text("你没有权限操作其他玩家的计分板。"))
                return true
            }
            resolveTargets(sender, args[0]) ?: return true
        }

        when (action) {
            "on" -> setState(sender, targets, true)
            "off" -> setState(sender, targets, false)
            "toggle" -> toggleState(sender, targets)
            "status" -> sendStatus(sender, targets)
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): List<String> {
        if (args.size == 1) {
            val values = ACTIONS.toMutableList()
            if (sender.hasPermission(PERMISSION)) {
                values += ADMIN_ACTIONS
                values += "@a"
                values += Bukkit.getOnlinePlayers().map { it.name }
            }
            return values.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && sender.hasPermission(PERMISSION)) {
            return ACTIONS.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun setState(sender: CommandSender, targets: List<Player>, enabled: Boolean) {
        targets.forEach { scoreboardService.setEnabled(it, enabled) }
        sender.sendMessage(
            Component.text(
                "已${if (enabled) "开启" else "关闭"} ${targetsLabel(targets)} 的统一计分板。",
            ),
        )
    }

    private fun toggleState(sender: CommandSender, targets: List<Player>) {
        targets.forEach { player ->
            scoreboardService.setEnabled(player, !scoreboardService.isEnabled(player))
        }
        sender.sendMessage(Component.text("已切换 ${targetsLabel(targets)} 的统一计分板状态。"))
    }

    private fun sendStatus(sender: CommandSender, targets: List<Player>) {
        targets.forEach { player ->
            val state = if (scoreboardService.isEnabled(player)) "开启" else "关闭"
            sender.sendMessage(Component.text("${player.name} 的统一计分板当前已$state。"))
        }
    }

    private fun resolveTargets(sender: CommandSender, selector: String): List<Player>? {
        val targets = when (selector.lowercase()) {
            "@a" -> Bukkit.getOnlinePlayers().toList()
            "@s" -> listOfNotNull(sender as? Player)
            else -> listOfNotNull(Bukkit.getPlayerExact(selector))
        }
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("找不到符合条件的在线玩家。"))
            return null
        }
        return targets
    }

    private fun targetsLabel(targets: List<Player>): String {
        return if (targets.size == 1) {
            targets.first().name
        } else {
            "${targets.size} 名玩家"
        }
    }

    private fun sendUsage(sender: CommandSender, label: String) {
        sender.sendMessage(
            Component.text(
                "用法：/$label <on|off|toggle|status> 或 /$label <玩家|@a> <on|off|toggle|status>",
            ),
        )
        if (sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("管理：/$label <reload|providers>"))
        }
    }

    companion object {
        private val ACTIONS = listOf("on", "off", "toggle", "status")
        private val ADMIN_ACTIONS = listOf("reload", "providers")
        private const val PERMISSION = "epochcommon.admin"
    }
}
