package io.github.thebusybiscuit.slimefun4.epochrebirth.command

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.death.DeathHandler
import io.github.thebusybiscuit.slimefun4.epochrebirth.gui.PriorityMenu
import io.github.thebusybiscuit.slimefun4.epochrebirth.hud.RebirthHud
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItem
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class RebirthCommand(
    private val menu: PriorityMenu,
    private val store: ResurrectionStore,
    private val config: RebirthConfig,
    private val hud: RebirthHud,
    private val items: RebirthItems,
    private val language: LanguageService,
    private val deathHandler: DeathHandler,
    private val reloader: () -> Boolean
) : TabExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            return openMenu(sender)
        }
        return when (args[0].lowercase()) {
            "menu" -> openMenu(sender)
            "get" -> getCounts(sender, args.getOrNull(1))
            "set" -> setCount(sender, args.getOrNull(1), args.getOrNull(2), args.getOrNull(3))
            "give" -> giveItem(sender, args.getOrNull(1), args.getOrNull(2), args.getOrNull(3))
            "resethealth" -> resetHealth(sender, args.getOrNull(1))
            "reload" -> reload(sender)
            else -> {
                sender.sendMessage(language.component("messages.usage"))
                true
            }
        }
    }

    private fun giveItem(sender: CommandSender, name: String?, itemId: String?, amountText: String?): Boolean {
        if (!sender.hasPermission("epochrebirth.admin")) {
            sender.sendMessage(language.component("messages.no-permission"))
            return true
        }
        val target = name?.let { Bukkit.getPlayerExact(it) } ?: run {
            sender.sendMessage(language.component("messages.player-not-found"))
            return true
        }
        val item = itemId?.let { RebirthItem.fromId(it.lowercase()) } ?: run {
            sender.sendMessage(language.component("messages.invalid-item", mapOf("item" to (itemId ?: "null"))))
            return true
        }
        val amount = amountText?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        target.inventory.addItem(items.create(item, amount))
            .values
            .forEach { target.world.dropItem(target.location, it) }
        sender.sendMessage(language.component("messages.give-done", mapOf(
            "player" to target.name,
            "item" to language.plain("items.${item.id}.name"),
            "amount" to amount.toString()
        ), rawValues = setOf("item")))
        return true
    }

    private fun openMenu(sender: CommandSender): Boolean {
        if (sender is Player) {
            menu.open(sender)
        } else {
            sender.sendMessage(language.component("messages.usage"))
        }
        return true
    }

    private fun getCounts(sender: CommandSender, name: String?): Boolean {
        val target: Player? = if (name != null) {
            if (!sender.hasPermission("epochrebirth.admin")) {
                sender.sendMessage(language.component("messages.no-permission"))
                return true
            }
            Bukkit.getPlayerExact(name)
        } else {
            sender as? Player
        }
        if (target == null) {
            sender.sendMessage(language.component("messages.player-not-found"))
            return true
        }
        sender.sendMessage(language.component("messages.get-info", mapOf(
            "player" to target.name,
            "basic" to store.count(target, Tier.BASIC).toString(),
            "advanced" to store.count(target, Tier.ADVANCED).toString(),
            "ultimate" to store.count(target, Tier.ULTIMATE).toString()
        )))
        return true
    }

    private fun setCount(sender: CommandSender, name: String?, tierName: String?, countText: String?): Boolean {
        if (!sender.hasPermission("epochrebirth.admin")) {
            sender.sendMessage(language.component("messages.no-permission"))
            return true
        }
        val target = name?.let { Bukkit.getPlayerExact(it) } ?: run {
            sender.sendMessage(language.component("messages.player-not-found"))
            return true
        }
        val tier = tierName?.let { Tier.fromId(it.lowercase()) } ?: run {
            sender.sendMessage(language.component("messages.invalid-tier", mapOf("tier" to (tierName ?: "null"))))
            return true
        }
        val count = countText?.toIntOrNull() ?: run {
            sender.sendMessage(language.component("messages.invalid-count", mapOf("max" to config.maxCount.toString())))
            return true
        }
        if (count !in 0..config.maxCount) {
            sender.sendMessage(language.component("messages.invalid-count", mapOf("max" to config.maxCount.toString())))
            return true
        }
        store.setCount(target, tier, count)
        hud.update(target)
        sender.sendMessage(language.component("messages.set-done", mapOf(
            "player" to target.name,
            "tier" to language.plain("menu.tier-name-${tier.id}"),
            "count" to count.toString()
        ), rawValues = setOf("tier")))
        return true
    }

    private fun resetHealth(sender: CommandSender, name: String?): Boolean {
        if (!sender.hasPermission("epochrebirth.admin")) {
            sender.sendMessage(language.component("messages.no-permission"))
            return true
        }
        val target = name?.let { Bukkit.getPlayerExact(it) } ?: (sender as? Player) ?: run {
            sender.sendMessage(language.component("messages.player-not-found"))
            return true
        }
        deathHandler.resetHealthPenalty(target)
        hud.update(target)
        sender.sendMessage(language.component("messages.health-reset", mapOf("player" to target.name)))
        return true
    }

    private fun reload(sender: CommandSender): Boolean {
        if (!sender.hasPermission("epochrebirth.admin")) {
            sender.sendMessage(language.component("messages.no-permission"))
            return true
        }
        reloader()
        sender.sendMessage(language.component("messages.reload-done"))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        if (args.size == 1) {
            return listOf("menu", "get", "set", "give", "resethealth", "reload").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && (args[0].equals("get", true) || args[0].equals("set", true) || args[0].equals("give", true) || args[0].equals("resethealth", true))) {
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) }
        }
        if (args.size == 3 && args[0].equals("set", true)) {
            return listOf("basic", "advanced", "ultimate").filter { it.startsWith(args[2].lowercase()) }
        }
        if (args.size == 3 && args[0].equals("give", true)) {
            return RebirthItem.entries.map { it.id }.filter { it.startsWith(args[2].lowercase()) }
        }
        return emptyList()
    }
}
