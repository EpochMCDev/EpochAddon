package com.epochaddon.common.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class JoinListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.logger.info("玩家 ${event.player.name} 加入服务器")
    }
}
