package com.epochaddon.skills.service

import com.epochaddon.skills.api.EpochSkillUnlocks
import com.epochaddon.skills.api.EpochSkillsService
import com.epochaddon.skills.config.SkillSettings
import com.epochaddon.skills.model.PlayerSkillProfile
import com.epochaddon.skills.model.ProfessionProgress
import com.epochaddon.skills.model.SkillNodeDefinition
import com.epochaddon.skills.model.SkillRequirement
import com.epochaddon.skills.model.SkillTreeDefinition
import com.epochaddon.skills.storage.PlayerSkillStore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Level

class DefaultEpochSkillsService(
    private val plugin: JavaPlugin,
    private val store: PlayerSkillStore,
    initialSettings: SkillSettings,
    initialTrees: List<SkillTreeDefinition>,
    private val bossBars: SkillBossBarService,
    private val miningSpeed: MiningSpeedService,
) : EpochSkillsService {
    enum class NodeUnlockResult {
        UNLOCKED,
        ALREADY_UNLOCKED,
        AUTO_UNLOCK,
        PREREQUISITES_MISSING,
        REQUIREMENT_NOT_MET,
        NOT_FOUND,
    }

    private val miniMessage = MiniMessage.miniMessage()
    private var settings = initialSettings
    private var trees = initialTrees.associateBy { it.id }
    private var guiRefresh: (Player) -> Unit = {}

    fun setGuiRefresh(refresh: (Player) -> Unit) {
        guiRefresh = refresh
    }

    fun updateConfiguration(newSettings: SkillSettings, newTrees: List<SkillTreeDefinition>) {
        settings = newSettings
        trees = newTrees.associateBy { it.id }
        plugin.server.onlinePlayers.forEach {
            evaluateAll(it, notify = false)
            refreshEffects(it)
            guiRefresh(it)
        }
    }

    override fun hasUnlock(player: Player, unlockId: String): Boolean {
        return unlockId in store.profile(player).unlocks
    }

    override fun hasUnlock(playerId: UUID, unlockId: String): Boolean {
        return unlockId in (store.profile(playerId)?.unlocks ?: emptySet())
    }

    override fun experience(playerId: UUID, professionId: String): Long {
        return store.profile(playerId)?.professions?.get(professionId)?.experience ?: 0L
    }

    override fun addExperience(player: Player, professionId: String, amount: Long): Long {
        val profile = store.profile(player)
        val progress = profile.profession(professionId)
        if (amount <= 0L) {
            return progress.experience
        }

        val previousExperience = progress.experience
        progress.experience = runCatching { Math.addExact(previousExperience, amount) }
            .getOrElse {
                plugin.logger.warning("Experience overflow for ${player.name} in $professionId; clamping")
                Long.MAX_VALUE
            }

        val tree = trees.values.firstOrNull { it.professionId == professionId }
        if (tree != null) {
            evaluate(player, profile, tree, notify = true)
            val nextExperience = nextExperienceRequirement(tree, progress)
            val barProgress = if (nextExperience == null || progress.experience >= nextExperience) {
                1.0f
            } else {
                0.0f
            }
            bossBars.show(
                player = player,
                professionName = tree.professionName,
                experience = progress.experience,
                gained = progress.experience - previousExperience,
                progress = barProgress,
                settings = settings,
            )
        }
        guiRefresh(player)
        return progress.experience
    }

    override fun sourceExperience(professionId: String, sourceId: String): Long {
        return settings.sourceExperience(professionId, sourceId)
    }

    fun recordBlockBreak(player: Player, material: Material) {
        if (player.gameMode !in settings.trackedGameModes) {
            return
        }

        val profile = store.profile(player)
        for (tree in trees.values) {
            val matchingCounters = tree.nodes.mapNotNull { node ->
                (node.requirement as? SkillRequirement.Counter)
                    ?.takeIf { material in it.materials }
            }.distinctBy { it.key }
            if (matchingCounters.isEmpty()) {
                continue
            }

            val progress = profile.profession(tree.professionId)
            for (counter in matchingCounters) {
                val previous = progress.counters[counter.key] ?: 0L
                progress.counters[counter.key] = if (previous == Long.MAX_VALUE) {
                    Long.MAX_VALUE
                } else {
                    previous + 1L
                }
            }
            evaluate(player, profile, tree, notify = true)
        }
        guiRefresh(player)
    }

    fun onJoin(player: Player) {
        store.profile(player)
        evaluateAll(player, notify = false)
        refreshEffects(player)
    }

    fun onQuit(player: Player) {
        bossBars.hide(player)
        miningSpeed.clear(player)
    }

    fun tree(treeId: String): SkillTreeDefinition? = trees[treeId]

    fun profile(player: Player): PlayerSkillProfile = store.profile(player)

    fun findPlayerIdByName(name: String): UUID? {
        return plugin.server.onlinePlayers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.uniqueId
            ?: store.findPlayerIdByName(name)
    }

    fun knownPlayerNames(): List<String> = buildSet {
        addAll(store.knownPlayerNames())
        addAll(plugin.server.onlinePlayers.map { it.name })
    }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun resetPlayer(playerId: UUID): Boolean {
        val reset = store.reset(playerId)
        val online = Bukkit.getPlayer(playerId)
        if (online != null) {
            bossBars.hide(online)
            miningSpeed.clear(online)
            guiRefresh(online)
        }
        if (reset || online != null) {
            store.save()
            return true
        }
        return false
    }

    fun currentNode(player: Player, tree: SkillTreeDefinition): SkillNodeDefinition? {
        val unlocked = store.profile(player).profession(tree.professionId).unlockedNodes
        return tree.nodes.filter { it.id in unlocked }.maxByOrNull { it.level }
    }

    fun nextNode(player: Player, tree: SkillTreeDefinition): SkillNodeDefinition? {
        val unlocked = store.profile(player).profession(tree.professionId).unlockedNodes
        return tree.nodes.sortedBy { it.level }.firstOrNull { it.id !in unlocked }
    }

    fun requirementProgress(player: Player, tree: SkillTreeDefinition, node: SkillNodeDefinition): Pair<Long, Long> {
        val progress = store.profile(player).profession(tree.professionId)
        return when (val requirement = node.requirement) {
            is SkillRequirement.Experience -> progress.experience to requirement.amount
            is SkillRequirement.Counter -> (progress.counters[requirement.key] ?: 0L) to requirement.amount
        }
    }

    fun isNodeUnlocked(player: Player, tree: SkillTreeDefinition, node: SkillNodeDefinition): Boolean {
        return node.id in store.profile(player).profession(tree.professionId).unlockedNodes
    }

    fun isNodeAvailable(player: Player, tree: SkillTreeDefinition, node: SkillNodeDefinition): Boolean {
        val progress = store.profile(player).profession(tree.professionId)
        return !node.autoUnlock && progress.unlockedNodes.containsAll(node.prerequisites) &&
            ProgressionEvaluator.requirementMet(node.requirement, progress)
    }

    fun unlockNode(player: Player, treeId: String, nodeId: String): NodeUnlockResult {
        val tree = trees[treeId] ?: return NodeUnlockResult.NOT_FOUND
        val node = tree.nodes.firstOrNull { it.id == nodeId } ?: return NodeUnlockResult.NOT_FOUND
        val profile = store.profile(player)
        val progress = profile.profession(tree.professionId)
        if (node.id in progress.unlockedNodes) {
            return NodeUnlockResult.ALREADY_UNLOCKED
        }
        if (node.autoUnlock) {
            return NodeUnlockResult.AUTO_UNLOCK
        }
        if (!progress.unlockedNodes.containsAll(node.prerequisites)) {
            return NodeUnlockResult.PREREQUISITES_MISSING
        }
        if (!ProgressionEvaluator.requirementMet(node.requirement, progress)) {
            return NodeUnlockResult.REQUIREMENT_NOT_MET
        }

        if (node.requirement is SkillRequirement.Experience) {
            progress.experience -= node.requirement.amount
        }
        grantNode(player, profile, node)
        refreshEffects(player)
        guiRefresh(player)
        return NodeUnlockResult.UNLOCKED
    }

    fun close() {
        bossBars.close()
        plugin.server.onlinePlayers.forEach(miningSpeed::clear)
    }

    private fun evaluateAll(player: Player, notify: Boolean) {
        val profile = store.profile(player)
        trees.values.forEach { evaluate(player, profile, it, notify) }
    }

    private fun evaluate(
        player: Player,
        profile: PlayerSkillProfile,
        tree: SkillTreeDefinition,
        notify: Boolean,
    ) {
        val progress = profile.profession(tree.professionId)
        val unlocked = ProgressionEvaluator.newlyUnlocked(tree, progress)
        if (unlocked.isEmpty()) {
            return
        }

        for (node in unlocked) {
            grantNode(player, profile, node, notify)
        }
        refreshEffects(player)
    }

    private fun grantNode(
        player: Player,
        profile: PlayerSkillProfile,
        node: SkillNodeDefinition,
        notify: Boolean = true,
    ) {
        profile.profession(trees.values.first { node in it.nodes }.professionId).unlockedNodes += node.id
        profile.unlocks += node.grants
        if (notify) {
            val message = settings.messages.nodeUnlocked.replace("{skill}", node.name)
            player.sendMessage(text(message))
        }
    }

    private fun refreshEffects(player: Player) {
        try {
            miningSpeed.refresh(
                player,
                enabled = hasUnlock(player, EpochSkillUnlocks.DIGGING_SPEED),
                amount = settings.miningSpeedBonus,
            )
        } catch (exception: RuntimeException) {
            plugin.logger.log(Level.WARNING, "Failed to refresh skill effects for ${player.name}", exception)
        }
    }

    private fun text(raw: String): Component = miniMessage.deserialize("<italic:false>$raw")

    private fun nextExperienceRequirement(
        tree: SkillTreeDefinition,
        progress: ProfessionProgress,
    ): Long? {
        return tree.nodes
            .asSequence()
            .filter { it.id !in progress.unlockedNodes }
            .sortedBy { it.level }
            .mapNotNull { (it.requirement as? SkillRequirement.Experience)?.amount }
            .firstOrNull()
            ?: settings.experienceCurve.nextThreshold(progress.experience)
    }
}
