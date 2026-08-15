package com.epochaddon.minerals.scoreboard

import com.epochaddon.common.scoreboard.ScoreboardProvider
import com.epochaddon.minerals.config.MiningSettings
import com.epochaddon.minerals.domain.RewardProgress
import com.epochaddon.minerals.service.MessageService
import com.epochaddon.minerals.service.MiningBoostService
import com.epochaddon.minerals.service.PlayerProgressStore
import com.epochaddon.minerals.service.VeinService
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class MineralsScoreboardProvider(
    private val settings: MiningSettings,
    private val progressStore: PlayerProgressStore,
    private val boostService: MiningBoostService,
    private val veinService: VeinService,
    private val messages: MessageService,
) : ScoreboardProvider {

    override fun lines(player: Player): List<Component> {
        val totalPoints = progressStore.points(player)
        val templates = settings.scoreboardMessages
        val boost = boostService.snapshot(player.uniqueId)
        val vein = veinService.snapshot(player.uniqueId)
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
        val timedMultiplier = boost?.multiplier ?: vein?.multiplier ?: 1.0
        val effectiveMultiplier = regionMultiplier * timedMultiplier
        val bonusAmount = effectiveMultiplier - 1.0
        val bonusText = if (kotlin.math.abs(bonusAmount) < 1.0e-9) {
            ""
        } else if (bonusAmount > 0.0) {
            " +${messages.formatDecimal(bonusAmount)}"
        } else {
            " ${messages.formatDecimal(bonusAmount)}"
        }
        val durationText = when {
            boost != null -> " (${boost.remainingSeconds}s)"
            vein != null -> " (${vein.remainingSeconds}s)"
            else -> ""
        }

        return buildList {
            if (templates.sectionTitle.isNotBlank()) {
                add(messages.component(templates.sectionTitle))
            }
            if (templates.pointsLine.isNotBlank()) {
                add(
                    messages.component(
                        templates.pointsLine,
                        mapOf("points" to messages.formatNumber(totalPoints)),
                    ),
                )
            }
            if (templates.remainingLine.isNotBlank()) {
                settings.rewards.chunked(PROGRESS_SEGMENTS_PER_LINE).forEach { rules ->
                    add(
                        joinSegments(
                            rules.map { rule ->
                                messages.component(
                                    templates.remainingLine,
                                    mapOf(
                                        "name" to rule.reward.scoreboardName,
                                        "remaining" to messages.formatNumber(
                                            RewardProgress.remaining(totalPoints, rule.points),
                                        ),
                                    ),
                                )
                            },
                        ),
                    )
                }
            }
            if (templates.statusLine.isNotBlank()) {
                add(
                    messages.component(
                        templates.statusLine,
                        mapOf(
                            "base" to "1",
                            "bonus" to bonusText,
                            "duration" to durationText,
                        ),
                    ),
                )
            }
        }
    }

    private fun joinSegments(segments: List<Component>): Component {
        return segments.drop(1).fold(segments.firstOrNull() ?: Component.empty()) { line, segment ->
            line.append(Component.text(" ")).append(segment)
        }
    }

    companion object {
        private const val PROGRESS_SEGMENTS_PER_LINE = 3
    }
}
