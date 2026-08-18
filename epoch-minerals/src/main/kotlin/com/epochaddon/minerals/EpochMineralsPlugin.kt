package com.epochaddon.minerals

import com.epochaddon.common.scoreboard.ScoreboardService
import com.epochaddon.common.scoreboard.ScoreboardProviderOptions
import com.epochaddon.common.util.VersionUtil
import com.epochaddon.skills.api.EpochSkillsService
import com.epochaddon.minerals.command.MiningPointsCommand
import com.epochaddon.minerals.command.StoneCommand
import com.epochaddon.minerals.config.MiningSettings
import com.epochaddon.minerals.listener.MiningListener
import com.epochaddon.minerals.scoreboard.MineralsScoreboardProvider
import com.epochaddon.minerals.service.MessageService
import com.epochaddon.minerals.service.MiningBoostService
import com.epochaddon.minerals.service.PlayerProgressStore
import com.epochaddon.minerals.service.VeinService
import org.bukkit.plugin.java.JavaPlugin

class EpochMineralsPlugin : JavaPlugin() {

    private lateinit var progressStore: PlayerProgressStore
    private lateinit var veinService: VeinService
    private lateinit var scoreboardService: ScoreboardService
    private lateinit var boostService: MiningBoostService
    private lateinit var skillsService: EpochSkillsService

    override fun onEnable() {
        saveDefaultConfig()

        val settings = MiningSettings.load(this)
        val messages = MessageService(settings.messages)

        progressStore = PlayerProgressStore(dataFolder.resolve("players.yml"), logger)
        if (!progressStore.load()) {
            logger.severe("EpochMinerals 因积分数据加载失败而停用，请先检查 players.yml")
            server.pluginManager.disablePlugin(this)
            return
        }

        scoreboardService = server.servicesManager.load(ScoreboardService::class.java) ?: run {
            logger.severe("EpochCommon 计分板服务不可用，EpochMinerals 无法启动")
            server.pluginManager.disablePlugin(this)
            return
        }
        skillsService = server.servicesManager.load(EpochSkillsService::class.java) ?: run {
            logger.severe("EpochSkills 服务不可用，EpochMinerals 无法启动")
            server.pluginManager.disablePlugin(this)
            return
        }
        boostService = MiningBoostService(this, scoreboardService)
        veinService = VeinService(this, settings.vein, messages, scoreboardService, boostService)
        scoreboardService.registerProvider(
            this,
            SCOREBOARD_PROVIDER_ID,
            // Avoid a Kotlin DefaultConstructorMarker in this cross-plugin API call.
            ScoreboardProviderOptions(
                order = SCOREBOARD_ORDER,
                enabledByDefault = true,
                maxLines = SCOREBOARD_MAX_LINES,
                separatorBefore = true,
                permission = null,
            ),
            MineralsScoreboardProvider(settings, progressStore, boostService, veinService, messages, skillsService),
        )

        server.pluginManager.registerEvents(
            MiningListener(settings, progressStore, veinService, boostService, scoreboardService, skillsService),
            this,
        )

        val miningPointsCommand = getCommand(MINING_POINTS_COMMAND)
            ?: throw IllegalStateException("plugin.yml 缺少 $MINING_POINTS_COMMAND 指令")
        val commandExecutor = MiningPointsCommand(boostService, messages)
        miningPointsCommand.setExecutor(commandExecutor)
        miningPointsCommand.tabCompleter = commandExecutor

        val stoneCommand = getCommand(STONE_COMMAND)
            ?: throw IllegalStateException("plugin.yml 缺少 $STONE_COMMAND 指令")
        stoneCommand.setExecutor(StoneCommand(progressStore, messages))

        scheduleAutosave(settings.autosaveSeconds)
        logger.info("EpochMinerals 已启用，服务端版本：${VersionUtil.serverVersion(server)}")
    }

    override fun onDisable() {
        if (::scoreboardService.isInitialized) {
            scoreboardService.unregisterProvider(this, SCOREBOARD_PROVIDER_ID)
        }
        if (::boostService.isInitialized) {
            boostService.close()
        }
        if (::veinService.isInitialized) {
            veinService.close()
        }
        if (::progressStore.isInitialized) {
            progressStore.save()
        }
        logger.info("EpochMinerals 已禁用")
    }

    private fun scheduleAutosave(intervalSeconds: Long) {
        if (intervalSeconds <= 0) {
            return
        }

        val intervalTicks = intervalSeconds * 20L
        server.scheduler.runTaskTimer(
            this,
            Runnable { progressStore.save() },
            intervalTicks,
            intervalTicks,
        )
    }

    companion object {
        private const val SCOREBOARD_PROVIDER_ID = "minerals"
        private const val SCOREBOARD_ORDER = 100
        private const val SCOREBOARD_MAX_LINES = 3
        private const val MINING_POINTS_COMMAND = "miningpoints"
        private const val STONE_COMMAND = "stone"
    }
}
