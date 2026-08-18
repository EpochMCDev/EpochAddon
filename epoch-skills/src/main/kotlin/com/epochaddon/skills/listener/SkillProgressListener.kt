package com.epochaddon.skills.listener

import com.epochaddon.skills.service.DefaultEpochSkillsService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class SkillProgressListener(
    private val skills: DefaultEpochSkillsService,
) : Listener {

    // Run before EpochMinerals' MONITOR gate so the first qualifying block can unlock resources immediately.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        skills.recordBlockBreak(event.player, event.block.type)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        skills.onJoin(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        skills.onQuit(event.player)
    }
}
