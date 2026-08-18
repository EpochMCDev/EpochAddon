package com.epochaddon.skills.storage

import com.epochaddon.skills.model.PlayerSkillProfile
import com.epochaddon.skills.model.ProfessionProgress
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class PlayerSkillStore(
    private val file: File,
    private val logger: Logger,
) {
    private val profiles = mutableMapOf<UUID, PlayerSkillProfile>()
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
            var loadedProfiles = 0
            for (rawPlayerId in players?.getKeys(false).orEmpty()) {
                val playerId = runCatching { UUID.fromString(rawPlayerId) }.getOrNull()
                val section = players?.getConfigurationSection(rawPlayerId)
                if (playerId == null || section == null) {
                    logger.warning("Ignoring invalid EpochSkills player record: $rawPlayerId")
                    continue
                }

                val profile = PlayerSkillProfile(
                    playerId = playerId,
                    playerName = section.getString("name", playerId.toString()) ?: playerId.toString(),
                    unlocks = section.getStringList("unlocks").filter { it.isNotBlank() }.toMutableSet(),
                )
                val professions = section.getConfigurationSection("professions")
                for (professionId in professions?.getKeys(false).orEmpty()) {
                    val professionSection = professions?.getConfigurationSection(professionId) ?: continue
                    val experience = professionSection.getLong("experience", 0L).coerceAtLeast(0L)
                    val counters = mutableMapOf<String, Long>()
                    val countersSection = professionSection.getConfigurationSection("counters")
                    for (counterId in countersSection?.getKeys(false).orEmpty()) {
                        counters[counterId] = countersSection?.getLong(counterId, 0L)?.coerceAtLeast(0L) ?: 0L
                    }
                    profile.professions[professionId] = ProfessionProgress(
                        experience = experience,
                        counters = counters,
                        unlockedNodes = professionSection.getStringList("unlocked-nodes")
                            .filter { it.isNotBlank() }
                            .toMutableSet(),
                    )
                }
                profiles[playerId] = profile
                loadedProfiles++
            }
            loaded = true
            logger.info("Loaded $loadedProfiles EpochSkills player profile(s)")
            true
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "Failed to load EpochSkills player data; saving is disabled", exception)
            false
        }
    }

    fun profile(player: Player): PlayerSkillProfile {
        val profile = profiles.getOrPut(player.uniqueId) {
            PlayerSkillProfile(player.uniqueId, player.name)
        }
        profile.playerName = player.name
        return profile
    }

    fun profile(playerId: UUID): PlayerSkillProfile? = profiles[playerId]

    fun findPlayerIdByName(name: String): UUID? {
        return profiles.values.firstOrNull { it.playerName.equals(name, ignoreCase = true) }?.playerId
    }

    fun knownPlayerNames(): List<String> = profiles.values
        .map { it.playerName }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun reset(playerId: UUID): Boolean = profiles.remove(playerId) != null

    fun save() {
        if (!loaded) {
            logger.severe("EpochSkills player data was not loaded; skipping save to protect existing data")
            return
        }

        try {
            Files.createDirectories(file.parentFile.toPath())
            val config = YamlConfiguration()
            config.set("config-version", 1)
            for ((playerId, profile) in profiles.entries.sortedBy { it.key.toString() }) {
                val base = "players.$playerId"
                config.set("$base.name", profile.playerName)
                config.set("$base.unlocks", profile.unlocks.sorted())
                for ((professionId, progress) in profile.professions.entries.sortedBy { it.key }) {
                    val professionBase = "$base.professions.$professionId"
                    config.set("$professionBase.experience", progress.experience)
                    config.set("$professionBase.unlocked-nodes", progress.unlockedNodes.sorted())
                    for ((counterId, value) in progress.counters.entries.sortedBy { it.key }) {
                        config.set("$professionBase.counters.$counterId", value)
                    }
                }
            }

            val temporaryFile = File(file.parentFile, "${file.name}.tmp")
            config.save(temporaryFile)
            if (file.isFile) {
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            moveIntoPlace(temporaryFile)
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "Failed to save EpochSkills player data", exception)
        }
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
            Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
