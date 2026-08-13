package io.github.thebusybiscuit.slimefun4.epochrebirth.totem

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.hud.RebirthHud
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItem
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class TotemListener(
    private val config: RebirthConfig,
    private val store: ResurrectionStore,
    private val items: RebirthItems,
    private val hud: RebirthHud,
    private val language: LanguageService
) : Listener {

    /** 复活图腾贴图是不死图腾，禁止它触发原版的不死图腾复活 */
    @EventHandler
    fun onResurrect(event: EntityResurrectEvent) {
        val player = event.entity as? Player ?: return
        if (isTotem(player.inventory.itemInMainHand) || isTotem(player.inventory.itemInOffHand)) {
            event.isCancelled = true
        }
    }

    private fun isTotem(stack: ItemStack?): Boolean = when (items.identityOf(stack)) {
        RebirthItem.TOTEM_BASIC, RebirthItem.TOTEM_ADVANCED, RebirthItem.TOTEM_ULTIMATE -> true
        else -> false
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val player = event.player
        val hand = player.inventory.itemInMainHand
        val tier = when (items.identityOf(hand)) {
            RebirthItem.TOTEM_BASIC -> Tier.BASIC
            RebirthItem.TOTEM_ADVANCED -> Tier.ADVANCED
            RebirthItem.TOTEM_ULTIMATE -> Tier.ULTIMATE
            else -> return
        }

        val added = store.add(player, tier, 1)
        if (added == null) {
            player.sendMessage(language.component("messages.totem-full", mapOf("max" to config.maxCount.toString())))
            event.isCancelled = true
            return
        }

        if (hand.amount <= 1) {
            player.inventory.setItemInMainHand(null)
        } else {
            hand.amount -= 1
        }
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        player.sendMessage(language.component("messages.totem-added", mapOf(
            "tier" to language.plain("menu.tier-name-${tier.id}"),
            "count" to added.toString(),
            "max" to config.maxCount.toString()
        ), rawValues = setOf("tier")))
        hud.update(player)
        event.isCancelled = true
    }
}
