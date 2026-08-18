package com.epochaddon.skills

import com.epochaddon.skills.api.EpochSkillsService
import com.epochaddon.skills.command.EpochSkillsCommand
import com.epochaddon.skills.config.SkillSettings
import com.epochaddon.skills.config.SkillTreeLoader
import com.epochaddon.skills.gui.SkillsGuiListener
import com.epochaddon.skills.gui.SkillsGuiService
import com.epochaddon.skills.listener.SkillProgressListener
import com.epochaddon.skills.model.SkillTreeDefinition
import com.epochaddon.skills.service.DefaultEpochSkillsService
import com.epochaddon.skills.service.MiningSpeedService
import com.epochaddon.skills.service.SkillBossBarService
import com.epochaddon.skills.storage.PlayerSkillStore
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class EpochSkillsPlugin : JavaPlugin() {
    private lateinit var settings: SkillSettings
    private lateinit var trees: List<SkillTreeDefinition>
    private lateinit var store: PlayerSkillStore
    private lateinit var skills: DefaultEpochSkillsService
    private lateinit var gui: SkillsGuiService

    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()
        saveResource(DEFAULT_TREE_RESOURCE, false)

        try {
            settings = SkillSettings.load(this)
            trees = SkillTreeLoader(logger).load(dataFolder.resolve("trees"))
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "EpochSkills configuration failed to load", exception)
            server.pluginManager.disablePlugin(this)
            return
        }

        store = PlayerSkillStore(dataFolder.resolve("players.yml"), logger)
        if (!store.load()) {
            server.pluginManager.disablePlugin(this)
            return
        }

        val bossBars = SkillBossBarService(this)
        val miningSpeed = MiningSpeedService(this)
        skills = DefaultEpochSkillsService(this, store, settings, trees, bossBars, miningSpeed)
        gui = SkillsGuiService(this, skills, ::settings)
        skills.setGuiRefresh(gui::refresh)

        server.servicesManager.register(EpochSkillsService::class.java, skills, this, ServicePriority.Normal)
        server.pluginManager.registerEvents(SkillProgressListener(skills), this)
        server.pluginManager.registerEvents(SkillsGuiListener(gui), this)

        val command = getCommand(COMMAND_NAME)
            ?: throw IllegalStateException("plugin.yml is missing $COMMAND_NAME")
        val executor = EpochSkillsCommand(gui, skills, ::settings, ::reloadSkills)
        command.setExecutor(executor)
        command.tabCompleter = executor

        server.onlinePlayers.forEach(skills::onJoin)
        scheduleAutosave(settings.autosaveSeconds)
        logger.info("EpochSkills enabled with ${trees.size} skill tree page(s)")
    }

    override fun onDisable() {
        if (::skills.isInitialized) {
            server.servicesManager.unregister(EpochSkillsService::class.java, skills)
            skills.close()
        }
        if (::store.isInitialized) {
            store.save()
        }
        logger.info("EpochSkills disabled")
    }

    private fun reloadSkills(): Boolean {
        return try {
            reloadConfig()
            val newSettings = SkillSettings.load(this)
            val newTrees = SkillTreeLoader(logger).load(dataFolder.resolve("trees"))
            settings = newSettings
            trees = newTrees
            skills.updateConfiguration(newSettings, newTrees)
            true
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "EpochSkills reload failed", exception)
            false
        }
    }

    private fun scheduleAutosave(intervalSeconds: Long) {
        if (intervalSeconds <= 0L) {
            return
        }
        server.scheduler.runTaskTimer(
            this,
            Runnable(store::save),
            intervalSeconds * 20L,
            intervalSeconds * 20L,
        )
    }

    companion object {
        private const val COMMAND_NAME = "eskills"
        private const val DEFAULT_TREE_RESOURCE = "trees/digging_skt_01.yml"
    }
}
