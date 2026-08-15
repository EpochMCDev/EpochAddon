package io.github.thebusybiscuit.slimefun4.epochrebirth.join

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.hud.RebirthHud
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class FirstJoinGiftListener(
    private val store: ResurrectionStore,
    private val hud: RebirthHud,
    private val language: LanguageService
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (player.hasPlayedBefore()) return

        val before = store.count(player, Tier.ULTIMATE)
        store.setCount(player, Tier.ULTIMATE, before + GIFT_COUNT)
        val after = store.count(player, Tier.ULTIMATE)
        val granted = after - before
        if (granted <= 0) return

        hud.update(player)
        player.sendMessage(language.component(
            "messages.first-join-gift",
            mapOf(
                "amount" to granted.toString(),
                "count" to after.toString()
            )
        ))
    }

    private companion object {
        private const val GIFT_COUNT = 3
    }
}
