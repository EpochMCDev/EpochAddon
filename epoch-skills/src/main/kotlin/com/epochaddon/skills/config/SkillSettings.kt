package com.epochaddon.skills.config

import com.epochaddon.skills.api.EpochSkillProfessions
import com.epochaddon.skills.model.ExperienceCurve
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.GameMode
import org.bukkit.plugin.java.JavaPlugin

data class SkillMessages(
    val noPermission: String,
    val playerOnly: String,
    val reloadSuccess: String,
    val reloadFailure: String,
    val nodeUnlocked: String,
    val nodeNotReady: String,
    val nodeAutoUnlock: String,
    val resetSuccess: String,
    val resetNotFound: String,
    val resetUsage: String,
    val unavailable: String,
)

data class SkillSettings(
    val autosaveSeconds: Long,
    val bossBarDurationTicks: Long,
    val bossBarColor: BossBar.Color,
    val bossBarTitle: String,
    val miningSpeedBonus: Double,
    val trackedGameModes: Set<GameMode>,
    val experienceCurve: ExperienceCurve,
    val sourceExperience: Map<String, Map<String, Long>>,
    val messages: SkillMessages,
) {
    fun sourceExperience(professionId: String, sourceId: String): Long {
        return sourceExperience[professionId]?.get(sourceId) ?: 0L
    }

    companion object {
        fun load(plugin: JavaPlugin): SkillSettings {
            val config = plugin.config
            val thresholds = config.getConfigurationSection("experience-thresholds")
                ?.getKeys(false)
                .orEmpty()
                .mapNotNull { key ->
                    val level = key.toIntOrNull()
                    val value = config.getLong("experience-thresholds.$key", -1L)
                    if (level == null || level <= 0 || value < 0L) {
                        plugin.logger.warning("Ignoring invalid experience threshold: $key")
                        null
                    } else {
                        level to value
                    }
                }
                .toMap()

            val sourceExperience = mutableMapOf<String, Map<String, Long>>()
            val sourceRoot = config.getConfigurationSection("experience-sources")
            for (groupId in sourceRoot?.getKeys(false).orEmpty()) {
                val professionSection = sourceRoot?.getConfigurationSection(groupId) ?: continue
                val professionId = professionSection.getString("profession-id", groupId) ?: groupId
                val values = professionSection.getConfigurationSection("values") ?: professionSection
                sourceExperience[professionId] = values.getKeys(false).mapNotNull { sourceId ->
                    if (sourceId == "profession-id") {
                        return@mapNotNull null
                    }
                    val value = values.getLong(sourceId, -1L)
                    if (value < 0L) {
                        plugin.logger.warning("Ignoring negative experience source: $professionId.$sourceId")
                        null
                    } else {
                        sourceId to value
                    }
                }.toMap()
            }

            val configuredColor = config.getString("bossbar.color", "BLUE").orEmpty().uppercase()
            val bossBarColor = runCatching { BossBar.Color.valueOf(configuredColor) }
                .getOrElse {
                    plugin.logger.warning("Invalid bossbar.color $configuredColor; using BLUE")
                    BossBar.Color.BLUE
                }

            val gameModes = config.getStringList("digging.game-modes")
                .mapNotNull { raw ->
                    runCatching { GameMode.valueOf(raw.uppercase()) }
                        .onFailure { plugin.logger.warning("Ignoring invalid digging game mode: $raw") }
                        .getOrNull()
                }
                .toSet()
                .ifEmpty { setOf(GameMode.SURVIVAL) }

            return SkillSettings(
                autosaveSeconds = config.getLong("storage.autosave-seconds", 300L).coerceAtLeast(0L),
                bossBarDurationTicks = config.getLong("bossbar.duration-ticks", 60L).coerceAtLeast(1L),
                bossBarColor = bossBarColor,
                bossBarTitle = config.getString("bossbar.title", DEFAULT_BOSSBAR_TITLE) ?: DEFAULT_BOSSBAR_TITLE,
                miningSpeedBonus = config.getDouble("digging.mining-speed-bonus", 0.35)
                    .takeIf { it.isFinite() && it >= 0.0 }
                    ?: 0.35,
                trackedGameModes = gameModes,
                experienceCurve = ExperienceCurve(thresholds.ifEmpty { DEFAULT_THRESHOLDS }),
                sourceExperience = sourceExperience.ifEmpty { DEFAULT_SOURCE_EXPERIENCE },
                messages = SkillMessages(
                    noPermission = config.getString("messages.no-permission", DEFAULT_NO_PERMISSION)
                        ?: DEFAULT_NO_PERMISSION,
                    playerOnly = config.getString("messages.player-only", DEFAULT_PLAYER_ONLY) ?: DEFAULT_PLAYER_ONLY,
                    reloadSuccess = config.getString("messages.reload-success", DEFAULT_RELOAD_SUCCESS)
                        ?: DEFAULT_RELOAD_SUCCESS,
                    reloadFailure = config.getString("messages.reload-failure", DEFAULT_RELOAD_FAILURE)
                        ?: DEFAULT_RELOAD_FAILURE,
                    nodeUnlocked = config.getString("messages.node-unlocked", DEFAULT_NODE_UNLOCKED)
                        ?: DEFAULT_NODE_UNLOCKED,
                    nodeNotReady = config.getString("messages.node-not-ready", DEFAULT_NODE_NOT_READY)
                        ?: DEFAULT_NODE_NOT_READY,
                    nodeAutoUnlock = config.getString("messages.node-auto-unlock", DEFAULT_NODE_AUTO_UNLOCK)
                        ?: DEFAULT_NODE_AUTO_UNLOCK,
                    resetSuccess = config.getString("messages.reset-success", DEFAULT_RESET_SUCCESS)
                        ?: DEFAULT_RESET_SUCCESS,
                    resetNotFound = config.getString("messages.reset-not-found", DEFAULT_RESET_NOT_FOUND)
                        ?: DEFAULT_RESET_NOT_FOUND,
                    resetUsage = config.getString("messages.reset-usage", DEFAULT_RESET_USAGE)
                        ?: DEFAULT_RESET_USAGE,
                    unavailable = config.getString("messages.unavailable", DEFAULT_UNAVAILABLE) ?: DEFAULT_UNAVAILABLE,
                ),
            )
        }

        private val DEFAULT_THRESHOLDS = mapOf(
            1 to 30L,
            2 to 57L,
            3 to 102L,
            4 to 173L,
            5 to 272L,
            6 to 399L,
            7 to 542L,
            8 to 678L,
            9 to 848L,
            10 to 2657L,
            11 to 1326L,
            12 to 1658L,
            13 to 2073L,
            14 to 2592L,
            15 to 3240L,
            16 to 4050L,
            17 to 5063L,
            18 to 6329L,
            19 to 7911L,
            20 to 10000L,
        )
        private val DEFAULT_SOURCE_EXPERIENCE = mapOf(
            EpochSkillProfessions.DIGGING to mapOf(
                "coal" to 9L,
                "raw-copper" to 13L,
                "raw-iron" to 19L,
                "quartz" to 20L,
                "raw-gold" to 19L,
                "redstone" to 10L,
                "lapis" to 12L,
                "diamond" to 192L,
                "emerald" to 384L,
            ),
        )

        private const val DEFAULT_BOSSBAR_TITLE =
            "<gold>{profession}</gold> <gray>({experience} <green>+{gained}</green>)</gray>"
        private const val DEFAULT_NO_PERMISSION = "<red>You do not have permission to use this command.</red>"
        private const val DEFAULT_PLAYER_ONLY = "<red>This command can only be used by a player.</red>"
        private const val DEFAULT_RELOAD_SUCCESS = "<green>EpochSkills configuration reloaded.</green>"
        private const val DEFAULT_RELOAD_FAILURE = "<red>EpochSkills reload failed. Check the console.</red>"
        private const val DEFAULT_NODE_UNLOCKED = "<green>Unlocked skill: <white>{skill}</white></green>"
        private const val DEFAULT_NODE_NOT_READY = "<yellow>该技能尚未满足解锁条件。</yellow>"
        private const val DEFAULT_NODE_AUTO_UNLOCK = "<gray>该技能满足条件后会自动解锁。</gray>"
        private const val DEFAULT_RESET_SUCCESS = "<green>已重置玩家 <white>{player}</white> 的全部技能进度。</green>"
        private const val DEFAULT_RESET_NOT_FOUND = "<red>找不到玩家 <white>{player}</white> 的技能数据。</red>"
        private const val DEFAULT_RESET_USAGE = "<yellow>用法：/eskill reset <玩家名></yellow>"
        private const val DEFAULT_UNAVAILABLE = "<gray>This skill tree has not been implemented yet.</gray>"
    }
}
