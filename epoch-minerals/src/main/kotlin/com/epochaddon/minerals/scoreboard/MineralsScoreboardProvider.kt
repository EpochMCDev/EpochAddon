package com.epochaddon.minerals.scoreboard

import com.epochaddon.common.scoreboard.ScoreboardProvider
import com.epochaddon.skills.api.EpochSkillUnlocks
import com.epochaddon.skills.api.EpochSkillsService
import com.epochaddon.minerals.config.MiningSettings
import com.epochaddon.minerals.domain.RewardProgress
import com.epochaddon.minerals.service.MessageService
import com.epochaddon.minerals.service.MiningBoostService
import com.epochaddon.minerals.service.PlayerProgressStore
import com.epochaddon.minerals.service.VeinService
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import kotlin.math.abs

class MineralsScoreboardProvider(
    private val settings: MiningSettings,
    private val progressStore: PlayerProgressStore,
    private val boostService: MiningBoostService,
    private val veinService: VeinService,
    private val messages: MessageService,
    private val skillsService: EpochSkillsService,
) : ScoreboardProvider {

    override fun lines(player: Player): List<Component> {
        val totalPoints = progressStore.points(player)
        val templates = settings.scoreboardMessages
        val showDropProgress = skillsService.hasUnlock(player, EpochSkillUnlocks.DIGGING_NEXT_DROP_DISPLAY)
        val upcoming = if (showDropProgress) RewardProgress.next(totalPoints, settings.rewards) else null

        return buildList {
            if (upcoming != null && templates.nextRewardLine.isNotBlank()) {
                val rewardNames = upcoming.rewards.joinToString(templates.rewardSeparator) { it.scoreboardName }
                add(
                    messages.component(
                        templates.nextRewardLine,
                        mapOf("reward" to rewardNames),
                    ),
                )
            }
            if (upcoming != null && templates.remainingPointsLine.isNotBlank()) {
                add(
                    messages.component(
                        templates.remainingPointsLine,
                        mapOf("remaining" to messages.formatNumber(upcoming.remainingPoints)),
                    ),
                )
            }
            boostLine(player)?.let { add(it) }
        }
    }

    private fun boostLine(player: Player): Component? {
        val templates = settings.scoreboardMessages
        if (templates.boostLine.isBlank()) {
            return null
        }

        val commandBoost = boostService.snapshot(player.uniqueId)
        val veinBoost = if (commandBoost == null) veinService.snapshot(player.uniqueId) else null
        val timedMultiplier = commandBoost?.multiplier ?: veinBoost?.multiplier ?: 1.0
        val remainingSeconds = commandBoost?.remainingSeconds ?: veinBoost?.remainingSeconds
        val regionMultiplier = if (
            settings.isSpecialRegion(
                player.world.name,
                player.location.blockX,
                player.location.blockY,
                player.location.blockZ,
            )
        ) {
            settings.specialRegionMultiplier
        } else {
            1.0
        }
        val effectiveMultiplier = regionMultiplier * timedMultiplier
        if (abs(effectiveMultiplier - 1.0) < MULTIPLIER_EPSILON) {
            return null
        }

        val duration = if (remainingSeconds != null) {
            templates.timedBoostDuration.replace("{seconds}", remainingSeconds.toString())
        } else {
            templates.regionBoostDuration
        }
        return messages.component(
            templates.boostLine,
            mapOf(
                "multiplier" to messages.formatMultiplier(effectiveMultiplier),
                "duration" to duration,
                "seconds" to remainingSeconds?.toString().orEmpty(),
            ),
        )
    }

    companion object {
        private const val MULTIPLIER_EPSILON = 1.0e-9
    }
}
