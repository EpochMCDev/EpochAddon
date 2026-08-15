package com.epochaddon.minerals.service

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

data class ProgressChange(
    val previousPoints: Double,
    val currentPoints: Double,
)

data class MiningRankingEntry(
    val playerId: UUID,
    val playerName: String,
    val points: Double,
)

class PlayerProgressStore(
    private val file: File,
    private val logger: Logger,
) {
    private val points = mutableMapOf<UUID, Double>()
    private val playerNames = mutableMapOf<UUID, String>()
    private val backupFile = File(file.parentFile, "${file.name}.bak")
    private var loaded = false

    fun load(): Boolean {
        if (!file.isFile) {
            loaded = true
            return true
        }

        return try {
            val config = YamlConfiguration.loadConfiguration(file)
            val players = config.getConfigurationSection("players")
                ?: throw IllegalStateException("缺少 players 节点")
            val keys = players.getKeys(false)
            var loadedEntries = 0

            for (key in keys) {
                val playerId = runCatching { UUID.fromString(key) }.getOrNull()
                val section = players.getConfigurationSection(key)
                if (playerId == null || section == null || !section.contains("points")) {
                    logger.warning("忽略无效的积分记录：$key")
                    continue
                }

                val storedPoints = section.getDouble("points", Double.NaN)
                if (!storedPoints.isFinite() || storedPoints < 0.0) {
                    logger.warning("忽略无效的积分数值：$key.points")
                    continue
                }

                points[playerId] = storedPoints
                section.getString("name")?.let { playerNames[playerId] = it }
                loadedEntries++
            }

            if (keys.isNotEmpty() && loadedEntries == 0) {
                throw IllegalStateException("没有可用的玩家积分记录")
            }

            loaded = true
            logger.info("已加载 $loadedEntries 名玩家的矿物积分")
            true
        } catch (exception: Exception) {
            logger.log(
                Level.SEVERE,
                "无法加载玩家矿物积分，已跳过保存以保护原数据：${file.absolutePath}",
                exception,
            )
            false
        }
    }

    fun add(player: Player, gainedPoints: Double): ProgressChange {
        val previousPoints = points(player)
        if (!gainedPoints.isFinite() || gainedPoints <= 0.0) {
            return ProgressChange(previousPoints, previousPoints)
        }

        val currentPoints = previousPoints + gainedPoints
        if (!currentPoints.isFinite()) {
            logger.warning("玩家 ${player.name} 的矿物积分超出可存储范围，本次积分未计入")
            return ProgressChange(previousPoints, previousPoints)
        }
        points[player.uniqueId] = currentPoints
        playerNames[player.uniqueId] = player.name
        return ProgressChange(previousPoints, currentPoints)
    }

    fun points(playerId: UUID): Double = points[playerId] ?: 0.0

    fun points(player: Player): Double {
        val storedId = findStoredId(player)
        if (storedId != player.uniqueId) {
            migrateNameMatch(player, storedId)
        }
        return points[player.uniqueId] ?: 0.0
    }

    fun rankings(): List<MiningRankingEntry> = points.map { (playerId, storedPoints) ->
        MiningRankingEntry(
            playerId = playerId,
            playerName = playerNames[playerId] ?: playerId.toString(),
            points = storedPoints,
        )
    }.sortedWith(
        compareByDescending<MiningRankingEntry> { it.points }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.playerName }
            .thenBy { it.playerId.toString() },
    )

    fun save() {
        if (!loaded) {
            logger.severe("积分数据没有成功加载，本次跳过保存：${file.absolutePath}")
            return
        }

        try {
            Files.createDirectories(file.parentFile.toPath())
            val config = YamlConfiguration()
            for ((playerId, storedPoints) in points.entries.sortedBy { it.key.toString() }) {
                config.set("players.$playerId.name", playerNames[playerId])
                config.set("players.$playerId.points", storedPoints)
            }

            val temporaryFile = File(file.parentFile, "${file.name}.tmp")
            config.save(temporaryFile)
            if (file.isFile) {
                Files.copy(
                    file.toPath(),
                    backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            moveIntoPlace(temporaryFile)
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "无法保存玩家矿物积分", exception)
        }
    }

    private fun findStoredId(player: Player): UUID {
        if (points.containsKey(player.uniqueId)) {
            return player.uniqueId
        }

        return playerNames.entries.firstOrNull { (storedId, storedName) ->
            points.containsKey(storedId) && storedName.equals(player.name, ignoreCase = true)
        }?.key ?: player.uniqueId
    }

    private fun migrateNameMatch(player: Player, storedId: UUID) {
        val storedPoints = points.remove(storedId) ?: return
        playerNames.remove(storedId)
        points[player.uniqueId] = storedPoints
        playerNames[player.uniqueId] = player.name
        logger.info("检测到玩家 ${player.name} 的 UUID 变化，已按玩家名迁移矿物积分")
    }

    private fun moveIntoPlace(temporaryFile: File) {
        try {
            Files.move(
                temporaryFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
