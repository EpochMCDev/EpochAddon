package com.epochaddon.skills.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class SkillsInventoryHolder(
    val screen: SkillsScreen,
) : InventoryHolder {
    private lateinit var backingInventory: Inventory

    fun bind(inventory: Inventory) {
        backingInventory = inventory
    }

    override fun getInventory(): Inventory = backingInventory
}
