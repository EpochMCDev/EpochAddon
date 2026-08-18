package com.epochaddon.skills.service

import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class MiningSpeedService(
    plugin: JavaPlugin,
) {
    private val modifierKey = NamespacedKey(plugin, "digging_speed")

    fun refresh(player: Player, enabled: Boolean, amount: Double) {
        val attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED) ?: return
        attribute.modifiers
            .filter { it.key == modifierKey }
            .forEach(attribute::removeModifier)

        if (enabled && amount > 0.0) {
            attribute.addModifier(
                AttributeModifier(
                    modifierKey,
                    amount,
                    AttributeModifier.Operation.ADD_NUMBER,
                ),
            )
        }
    }

    fun clear(player: Player) {
        refresh(player, enabled = false, amount = 0.0)
    }
}
