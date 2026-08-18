package com.epochaddon.skills.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class SkillsGuiListener(
    private val gui: SkillsGuiService,
) : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? SkillsInventoryHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (event.rawSlot !in 0 until event.view.topInventory.size) {
            return
        }
        gui.handleClick(player, holder.screen, event.rawSlot)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is SkillsInventoryHolder) {
            event.isCancelled = true
        }
    }
}
