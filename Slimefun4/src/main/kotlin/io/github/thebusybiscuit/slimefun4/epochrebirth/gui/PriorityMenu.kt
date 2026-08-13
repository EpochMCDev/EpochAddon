package io.github.thebusybiscuit.slimefun4.epochrebirth.gui

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class PriorityMenu(
    private val store: ResurrectionStore,
    private val config: RebirthConfig,
    private val items: RebirthItems,
    private val language: LanguageService
) : Listener {

    private val slots = mapOf(Tier.BASIC to 2, Tier.ADVANCED to 4, Tier.ULTIMATE to 6)

    fun open(player: Player) {
        if (!player.hasPermission("epochrebirth.use")) {
            player.sendMessage(language.component("messages.menu-no-permission"))
            return
        }
        val holder = PriorityHolder()
        val inventory = Bukkit.createInventory(holder, 9, language.component("menu.title"))
        fill(player, inventory)
        player.openInventory(inventory)
    }

    private fun fill(player: Player, inventory: Inventory) {
        val order = store.order(player, config.priorityDefault)
        slots.forEach { (tier, slot) ->
            val icon = items.create(tier.item)
            val meta = icon.itemMeta
            meta.lore(listOf(
                language.component("menu.count-lore", mapOf(
                    "count" to store.count(player, tier).toString(),
                    "max" to config.maxCount.toString()
                )),
                language.component("menu.order-lore", mapOf("order" to (order.indexOf(tier) + 1).toString())),
                language.component("menu.click-hint")
            ).map { it.decoration(TextDecoration.ITALIC, false) })
            icon.itemMeta = meta
            inventory.setItem(slot, icon)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is PriorityHolder) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val tier = slots.entries.firstOrNull { it.value == event.slot }?.key ?: return

        val order = store.order(player, config.priorityDefault).toMutableList()
        order.remove(tier)
        order.add(0, tier)
        store.setOrder(player, order)

        player.sendMessage(language.component("messages.priority-set", mapOf(
            "tier" to language.plain("menu.tier-name-${tier.id}")
        ), rawValues = setOf("tier")))
        fill(player, event.view.topInventory)
    }
}

class PriorityHolder : InventoryHolder {
    override fun getInventory(): Inventory = Bukkit.createInventory(this, 9)
}
