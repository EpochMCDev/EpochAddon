package com.epochaddon.minerals.config

import com.epochaddon.minerals.domain.RewardRule
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin

data class MineralReward(
    val id: String,
    val material: Material,
    val displayName: String,
    val scoreboardName: String,
)

data class CuboidRegion(
    val id: String,
    val worldName: String,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {
    fun contains(worldName: String, x: Int, y: Int, z: Int): Boolean {
        return this.worldName == worldName &&
            x in minX..maxX &&
            y in minY..maxY &&
            z in minZ..maxZ
    }
}

data class VeinSettings(
    val chance: Double,
    val multiplier: Double,
    val durationSeconds: Long,
)

data class PluginMessages(
    val veinStart: String,
    val veinExtended: String,
    val leaderboardHeader: String,
    val leaderboardEntry: String,
    val leaderboardEmpty: String,
    val boostStarted: String,
    val boostStopped: String,
    val boostNotActive: String,
    val commandUsage: String,
    val invalidMultiplier: String,
    val invalidDuration: String,
    val targetNotFound: String,
    val noPermission: String,
)

data class ScoreboardMessages(
    val nextRewardLine: String,
    val remainingPointsLine: String,
    val rewardSeparator: String,
    val boostLine: String,
    val timedBoostDuration: String,
    val regionBoostDuration: String,
)

data class MiningSettings(
    val blocks: Set<Material>,
    val gameModes: Set<GameMode>,
    val pointsPerBlock: Double,
    val requireResourceSystemUnlock: Boolean,
    val rewards: List<RewardRule<MineralReward>>,
    val specialRegionMultiplier: Double,
    val specialRegions: List<CuboidRegion>,
    val vein: VeinSettings,
    val autosaveSeconds: Long,
    val messages: PluginMessages,
    val scoreboardMessages: ScoreboardMessages,
) {
    fun isSpecialRegion(worldName: String, x: Int, y: Int, z: Int): Boolean {
        return specialRegions.any { it.contains(worldName, x, y, z) }
    }

    companion object {
        fun load(plugin: JavaPlugin): MiningSettings {
            val config = plugin.config
            val blocks = config.getStringList("mining.blocks")
                .mapNotNull { raw ->
                    val material = Material.matchMaterial(raw)
                    if (material == null || !material.isBlock) {
                        plugin.logger.warning("忽略无效的挖掘方块：$raw")
                        null
                    } else {
                        material
                    }
                }
                .toSet()
                .ifEmpty { setOf(Material.STONE, Material.DEEPSLATE) }

            val gameModes = config.getStringList("mining.game-modes")
                .mapNotNull { raw ->
                    runCatching { GameMode.valueOf(raw.uppercase()) }
                        .onFailure { plugin.logger.warning("忽略无效的游戏模式：$raw") }
                        .getOrNull()
                }
                .toSet()
                .ifEmpty { setOf(GameMode.SURVIVAL) }

            val rewards = loadRewards(plugin)
            val regions = loadRegions(plugin)
            val configuredVeinChance = config.getDouble("vein.chance", 0.01)

            return MiningSettings(
                blocks = blocks,
                gameModes = gameModes,
                pointsPerBlock = positiveOrDefault(config.getDouble("mining.points-per-block"), 1.0),
                requireResourceSystemUnlock = config.getBoolean(
                    "skills.require-resource-system-unlock",
                    false,
                ),
                rewards = rewards,
                specialRegionMultiplier = positiveOrDefault(
                    config.getDouble("special-regions.multiplier"),
                    1.25,
                ),
                specialRegions = regions,
                vein = VeinSettings(
                    chance = if (configuredVeinChance.isFinite()) {
                        configuredVeinChance.coerceIn(0.0, 1.0)
                    } else {
                        0.01
                    },
                    multiplier = positiveOrDefault(config.getDouble("vein.multiplier"), 3.0),
                    durationSeconds = config.getLong("vein.duration-seconds", 13L).coerceAtLeast(1L),
                ),
                autosaveSeconds = config.getLong("storage.autosave-seconds", 300L).coerceAtLeast(0L),
                messages = PluginMessages(
                    veinStart = config.getString("messages.vein-start", "") ?: "",
                    veinExtended = config.getString("messages.vein-extended", DEFAULT_VEIN_EXTENDED)
                        ?: DEFAULT_VEIN_EXTENDED,
                    leaderboardHeader = config.getString(
                        "messages.leaderboard-header",
                        DEFAULT_LEADERBOARD_HEADER,
                    ) ?: DEFAULT_LEADERBOARD_HEADER,
                    leaderboardEntry = config.getString(
                        "messages.leaderboard-entry",
                        DEFAULT_LEADERBOARD_ENTRY,
                    ) ?: DEFAULT_LEADERBOARD_ENTRY,
                    leaderboardEmpty = config.getString(
                        "messages.leaderboard-empty",
                        DEFAULT_LEADERBOARD_EMPTY,
                    ) ?: DEFAULT_LEADERBOARD_EMPTY,
                    boostStarted = config.getString("messages.boost-started", DEFAULT_BOOST_STARTED)
                        ?: DEFAULT_BOOST_STARTED,
                    boostStopped = config.getString("messages.boost-stopped", DEFAULT_BOOST_STOPPED)
                        ?: DEFAULT_BOOST_STOPPED,
                    boostNotActive = config.getString("messages.boost-not-active", DEFAULT_BOOST_NOT_ACTIVE)
                        ?: DEFAULT_BOOST_NOT_ACTIVE,
                    commandUsage = config.getString("messages.command-usage", DEFAULT_COMMAND_USAGE)
                        ?: DEFAULT_COMMAND_USAGE,
                    invalidMultiplier = config.getString(
                        "messages.invalid-multiplier",
                        DEFAULT_INVALID_MULTIPLIER,
                    ) ?: DEFAULT_INVALID_MULTIPLIER,
                    invalidDuration = config.getString("messages.invalid-duration", DEFAULT_INVALID_DURATION)
                        ?: DEFAULT_INVALID_DURATION,
                    targetNotFound = config.getString("messages.target-not-found", DEFAULT_TARGET_NOT_FOUND)
                        ?: DEFAULT_TARGET_NOT_FOUND,
                    noPermission = config.getString("messages.no-permission", DEFAULT_NO_PERMISSION)
                        ?: DEFAULT_NO_PERMISSION,
                ),
                scoreboardMessages = ScoreboardMessages(
                    nextRewardLine = config.getString("scoreboard.next-reward-line", DEFAULT_NEXT_REWARD_LINE)
                        ?: DEFAULT_NEXT_REWARD_LINE,
                    remainingPointsLine = config.getString(
                        "scoreboard.remaining-points-line",
                        DEFAULT_REMAINING_POINTS_LINE,
                    ) ?: DEFAULT_REMAINING_POINTS_LINE,
                    rewardSeparator = config.getString("scoreboard.reward-separator", DEFAULT_REWARD_SEPARATOR)
                        ?: DEFAULT_REWARD_SEPARATOR,
                    boostLine = config.getString("scoreboard.boost-line", DEFAULT_BOOST_LINE)
                        ?: DEFAULT_BOOST_LINE,
                    timedBoostDuration = config.getString(
                        "scoreboard.timed-boost-duration",
                        DEFAULT_TIMED_BOOST_DURATION,
                    ) ?: DEFAULT_TIMED_BOOST_DURATION,
                    regionBoostDuration = config.getString(
                        "scoreboard.region-boost-duration",
                        DEFAULT_REGION_BOOST_DURATION,
                    ) ?: DEFAULT_REGION_BOOST_DURATION,
                ),
            )
        }

        private fun loadRewards(plugin: JavaPlugin): List<RewardRule<MineralReward>> {
            val root = plugin.config.getConfigurationSection("rewards")
            val rewards = root?.getKeys(false).orEmpty().mapNotNull { id ->
                val section = root?.getConfigurationSection(id) ?: return@mapNotNull null
                val materialName = section.getString("material").orEmpty()
                val material = Material.matchMaterial(materialName)
                val points = section.getDouble("points")

                if (material == null || !material.isItem) {
                    plugin.logger.warning("忽略奖励 $id：无效的物品材质 $materialName")
                    return@mapNotNull null
                }
                if (!points.isFinite() || points <= 0.0) {
                    plugin.logger.warning("忽略奖励 $id：points 必须大于 0")
                    return@mapNotNull null
                }

                val displayName = section.getString("display-name", id) ?: id
                RewardRule(
                    reward = MineralReward(
                        id = id,
                        material = material,
                        displayName = displayName,
                        scoreboardName = section.getString("scoreboard-name") ?: defaultScoreboardName(id),
                    ),
                    points = points,
                )
            }

            if (rewards.isNotEmpty()) {
                return rewards
            }

            plugin.logger.warning("未读取到有效矿物奖励，使用策划文档中的默认配置")
            return defaultRewards()
        }

        private fun loadRegions(plugin: JavaPlugin): List<CuboidRegion> {
            val root = plugin.config.getConfigurationSection("special-regions.regions") ?: return emptyList()
            return root.getKeys(false).mapNotNull { id ->
                val section = root.getConfigurationSection(id) ?: return@mapNotNull null
                val worldName = section.getString("world")
                val min = section.getConfigurationSection("min")
                val max = section.getConfigurationSection("max")

                if (worldName.isNullOrBlank() || !hasCoordinates(min) || !hasCoordinates(max)) {
                    plugin.logger.warning("忽略特殊矿区 $id：需要 world、min 和 max 坐标")
                    return@mapNotNull null
                }

                CuboidRegion(
                    id = id,
                    worldName = worldName,
                    minX = min!!.getInt("x").coerceAtMost(max!!.getInt("x")),
                    minY = min.getInt("y").coerceAtMost(max.getInt("y")),
                    minZ = min.getInt("z").coerceAtMost(max.getInt("z")),
                    maxX = min.getInt("x").coerceAtLeast(max.getInt("x")),
                    maxY = min.getInt("y").coerceAtLeast(max.getInt("y")),
                    maxZ = min.getInt("z").coerceAtLeast(max.getInt("z")),
                )
            }
        }

        private fun hasCoordinates(section: ConfigurationSection?): Boolean {
            return section != null && section.contains("x") && section.contains("y") && section.contains("z")
        }

        private fun positiveOrDefault(value: Double, defaultValue: Double): Double {
            return if (value.isFinite() && value > 0.0) value else defaultValue
        }

        private fun defaultRewards(): List<RewardRule<MineralReward>> {
            return listOf(
                defaultReward("coal", Material.COAL, "煤炭", "煤", 18.0),
                defaultReward("raw-copper", Material.RAW_COPPER, "粗铜", "铜", 26.0),
                defaultReward("raw-iron", Material.RAW_IRON, "粗铁", "铁", 38.0),
                defaultReward("quartz", Material.QUARTZ, "下界石英", "石英", 40.0),
                defaultReward("raw-gold", Material.RAW_GOLD, "粗金", "金", 38.0),
                defaultReward("redstone", Material.REDSTONE, "红石粉", "红石", 20.0),
                defaultReward("lapis", Material.LAPIS_LAZULI, "青金石", "青金", 24.0),
                defaultReward("diamond", Material.DIAMOND, "钻石", "钻", 384.0),
                defaultReward("emerald", Material.EMERALD, "绿宝石", "绿宝石", 768.0),
            )
        }

        private fun defaultReward(
            id: String,
            material: Material,
            displayName: String,
            scoreboardName: String,
            points: Double,
        ): RewardRule<MineralReward> =
            RewardRule(MineralReward(id, material, displayName, scoreboardName), points)

        private fun defaultScoreboardName(id: String): String = when (id) {
            "coal" -> "煤"
            "raw-copper" -> "铜"
            "raw-iron" -> "铁"
            "quartz" -> "石英"
            "raw-gold" -> "金"
            "redstone" -> "红石"
            "lapis" -> "青金"
            "diamond" -> "钻"
            "emerald" -> "绿宝石"
            else -> id
        }

        private const val DEFAULT_NEXT_REWARD_LINE = "<gray>下次掉落 <white>{reward}</white></gray>"
        private const val DEFAULT_REMAINING_POINTS_LINE = "<gray>还需积分 <yellow>{remaining}</yellow></gray>"
        private const val DEFAULT_REWARD_SEPARATOR = "<dark_gray> + </dark_gray>"
        private const val DEFAULT_BOOST_LINE =
            "<yellow>挖矿增益 <gold>x{multiplier}</gold>{duration}</yellow>"
        private const val DEFAULT_TIMED_BOOST_DURATION = " <gray>剩余 {seconds}s</gray>"
        private const val DEFAULT_REGION_BOOST_DURATION = " <gray>特殊矿区</gray>"
        private const val DEFAULT_VEIN_EXTENDED =
            "<gold><bold>找到矿脉！</bold></gold> <yellow>挖矿积分加成延长 {seconds} 秒。</yellow>"
        private const val DEFAULT_LEADERBOARD_HEADER =
            "<gold><bold>挖矿积分排行榜</bold></gold> <gray>共 {count} 名玩家</gray>"
        private const val DEFAULT_LEADERBOARD_ENTRY =
            "<yellow>#{rank}</yellow> <white>{player}</white> <gray>-</gray> <gold>{points}</gold>"
        private const val DEFAULT_LEADERBOARD_EMPTY = "<gray>目前还没有玩家获得挖矿积分。"
        private const val DEFAULT_BOOST_STARTED =
            "<green>已为 <white>{targets}</white> 开启挖矿积分加成：<yellow>x{multiplier}</yellow>，持续 <white>{seconds}</white> 秒。"
        private const val DEFAULT_BOOST_STOPPED =
            "<yellow>已结束 <white>{targets}</white> 的挖矿积分加成，共处理 {count} 名玩家。"
        private const val DEFAULT_BOOST_NOT_ACTIVE =
            "<gray><white>{targets}</white></gray> 当前没有生效中的挖矿积分加成。"
        private const val DEFAULT_COMMAND_USAGE =
            "<gray>用法：<white>/miningpoints target multiplier seconds</white> 或 <white>/miningpoints target stop</white>"
        private const val DEFAULT_INVALID_MULTIPLIER = "<red>积分倍数必须是大于 0 且不超过 10000 的数字。"
        private const val DEFAULT_INVALID_DURATION = "<red>持续时间必须是 1~86400 秒的整数。"
        private const val DEFAULT_TARGET_NOT_FOUND = "<red>找不到符合条件的在线玩家。"
        private const val DEFAULT_NO_PERMISSION = "<red>你没有权限使用这个指令。"
    }
}
