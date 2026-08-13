package io.github.thebusybiscuit.slimefun4.epochrebirth.item

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

/**
 * 自定义物品的贴图复用原版方块（地狱岩等），一旦放置就会丢失 NBT 变成普通方块。
 * 这里禁止放置本插件的所有物品。
 */
class ItemGuardListener(
    private val items: RebirthItems,
    private val language: LanguageService
) : Listener {

    @EventHandler
    fun onPlace(event: BlockPlaceEvent) {
        if (items.identityOf(event.itemInHand) == null) return
        event.isCancelled = true
        event.player.sendMessage(language.component("messages.cannot-place"))
    }
}