package com.epochaddon.skills.config

import com.epochaddon.skills.model.SkillNodeDefinition
import com.epochaddon.skills.model.SkillRequirement
import com.epochaddon.skills.model.SkillTreeDefinition
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

class SkillTreeLoader(
    private val logger: Logger,
) {
    fun load(directory: File): List<SkillTreeDefinition> {
        val files = directory.listFiles { file ->
            file.isFile && file.name.matches(Regex(".+_skt_\\d+\\.ya?ml", RegexOption.IGNORE_CASE))
        }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) {
            throw IllegalStateException("No *_skt_XX.yml skill tree files found in ${directory.absolutePath}")
        }

        val trees = files.map(::loadTree)
        val duplicateIds = trees.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            throw IllegalStateException("Duplicate skill tree ids: ${duplicateIds.joinToString()}")
        }
        return trees
    }

    private fun loadTree(file: File): SkillTreeDefinition {
        val config = YamlConfiguration.loadConfiguration(file)
        val id = requiredString(config, "id", file)
        val professionId = requiredString(config, "profession-id", file)
        val professionName = requiredString(config, "profession-name", file)
        val title = requiredString(config, "title", file)
        val page = config.getInt("page", 1).coerceAtLeast(1)
        val nodesRoot = config.getConfigurationSection("nodes")
            ?: throw IllegalStateException("${file.name} is missing nodes")

        val nodes = nodesRoot.getKeys(false).map { nodeId ->
            val section = nodesRoot.getConfigurationSection(nodeId)
                ?: throw IllegalStateException("${file.name}: invalid node $nodeId")
            loadNode(file, nodeId, section)
        }.sortedBy { it.level }

        if (nodes.isEmpty()) {
            throw IllegalStateException("${file.name} has no nodes")
        }
        val nodeIds = nodes.map { it.id }.toSet()
        for (node in nodes) {
            val missing = node.prerequisites - nodeIds
            if (missing.isNotEmpty()) {
                throw IllegalStateException("${file.name}: node ${node.id} has missing prerequisites $missing")
            }
        }

        val connectionSlots = config.getIntegerList("connection-slots").map { oneBasedSlot ->
            validateSlot(file, "connection", oneBasedSlot)
        }

        logger.info("Loaded skill tree $id from ${file.name} with ${nodes.size} node(s)")
        return SkillTreeDefinition(
            id = id,
            professionId = professionId,
            professionName = professionName,
            title = title,
            page = page,
            nodes = nodes,
            connectionSlots = connectionSlots,
        )
    }

    private fun loadNode(file: File, nodeId: String, section: ConfigurationSection): SkillNodeDefinition {
        val materialName = requiredString(section, "icon", file)
        val material = Material.matchMaterial(materialName)
            ?.takeIf { it.isItem }
            ?: throw IllegalStateException("${file.name}: node $nodeId has invalid icon $materialName")
        val requirementSection = section.getConfigurationSection("requirement")
            ?: throw IllegalStateException("${file.name}: node $nodeId is missing requirement")

        val requirement = when (requiredString(requirementSection, "type", file).lowercase()) {
            "experience" -> SkillRequirement.Experience(
                requirementSection.getLong("amount", -1L).also {
                    require(it >= 0L) { "${file.name}: node $nodeId experience amount must be non-negative" }
                },
            )

            "counter" -> {
                val key = requiredString(requirementSection, "key", file)
                val amount = requirementSection.getLong("amount", -1L)
                require(amount > 0L) { "${file.name}: node $nodeId counter amount must be positive" }
                val materials = requirementSection.getStringList("materials").map { raw ->
                    Material.matchMaterial(raw)
                        ?.takeIf { it.isBlock }
                        ?: throw IllegalStateException("${file.name}: node $nodeId has invalid block $raw")
                }.toSet()
                require(materials.isNotEmpty()) { "${file.name}: node $nodeId counter requires materials" }
                SkillRequirement.Counter(key, amount, materials)
            }

            else -> throw IllegalStateException("${file.name}: node $nodeId has unsupported requirement type")
        }

        return SkillNodeDefinition(
            id = nodeId,
            level = section.getInt("level", 0).also {
                require(it > 0) { "${file.name}: node $nodeId level must be positive" }
            },
            name = requiredString(section, "name", file),
            description = section.getStringList("description"),
            slot = validateSlot(file, nodeId, section.getInt("slot", 0)),
            icon = material,
            autoUnlock = section.getBoolean("auto-unlock", false),
            prerequisites = section.getStringList("prerequisites").toSet(),
            requirement = requirement,
            grants = section.getStringList("grants").filter { it.isNotBlank() }.toSet(),
        )
    }

    private fun validateSlot(file: File, owner: String, oneBasedSlot: Int): Int {
        require(oneBasedSlot in 1..54) { "${file.name}: $owner slot must be within 1..54" }
        return oneBasedSlot - 1
    }

    private fun requiredString(section: ConfigurationSection, path: String, file: File): String {
        return section.getString(path)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("${file.name} is missing $path")
    }
}
