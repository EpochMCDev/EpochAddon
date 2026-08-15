package com.epochaddon.minerals.listener

import com.epochaddon.common.scoreboard.ScoreboardService
import com.epochaddon.minerals.config.MineralReward
import com.epochaddon.minerals.config.MiningSettings
import com.epochaddon.minerals.domain.RewardGrant
import com.epochaddon.minerals.domain.ThresholdRewards
import com.epochaddon.minerals.service.MiningBoostService
import com.epochaddon.minerals.service.PlayerProgressStore
import com.epochaddon.minerals.service.VeinService
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack

class MiningListener(
    private val settings: MiningSettings,
    private val progressStore: PlayerProgressStore,
    private val veinService: VeinService,
    private val boostService: MiningBoostService,
    private val scoreboardService: ScoreboardService,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player
        if (block.type !in settings.blocks || player.gameMode !in settings.gameModes) {
            return
        }

        val veinActive = veinService.isActive(player.uniqueId) || veinService.tryStart(player)
        val commandBoost = boostService.snapshot(player.uniqueId)
        var multiplier = 1.0
        if (settings.isSpecialRegion(block.world.name, block.x, block.y, block.z)) {
            multiplier *= settings.specialRegionMultiplier
        }
        // 指令倍率和矿脉倍率互斥，矿脉触发时由 VeinService 延长指令效果。
        if (commandBoost != null) {
            multiplier *= commandBoost.multiplier
        } else if (veinActive) {
            multiplier *= settings.vein.multiplier
        }

        val gainedPoints = settings.pointsPerBlock * multiplier
        val progress = progressStore.add(player, gainedPoints)
        val rewards = ThresholdRewards.crossed(progress.previousPoints, progress.currentPoints, settings.rewards)

        dropRewards(block.location, rewards)
        scoreboardService.refresh(player)
    }

    private fun dropRewards(location: Location, rewards: List<RewardGrant<MineralReward>>) {
        if (rewards.isEmpty()) {
            return
        }

        val world = location.world ?: return
        val dropLocation = location.clone().add(0.5, 0.5, 0.5)
        for (grant in rewards) {
            var remaining = grant.amount
            val maxStackSize = grant.reward.material.maxStackSize.coerceAtLeast(1)
            while (remaining > 0) {
                val stackSize = remaining.coerceAtMost(maxStackSize)
                world.dropItemNaturally(dropLocation, ItemStack(grant.reward.material, stackSize))
                remaining -= stackSize
            }
        }
    }
}
