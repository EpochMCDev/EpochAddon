package com.epochaddon.common

import com.epochaddon.common.command.ScoreboardCommand
import com.epochaddon.common.listener.JoinListener
import com.epochaddon.common.scoreboard.EpochScoreboardService
import com.epochaddon.common.scoreboard.ScoreboardService
import com.epochaddon.common.scoreboard.ScoreboardSettings
import com.epochaddon.common.util.VersionUtil
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class EpochCommon : JavaPlugin() {

    private lateinit var scoreboardService: EpochScoreboardService

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(JoinListener(this), this)

        scoreboardService = EpochScoreboardService(
            plugin = this,
            initialSettings = ScoreboardSettings.load(config, logger),
        )
        server.servicesManager.register(
            ScoreboardService::class.java,
            scoreboardService,
            this,
            ServicePriority.Normal,
        )
        scoreboardService.start()

        val scoreboardCommand = getCommand(SCOREBOARD_COMMAND)
            ?: throw IllegalStateException("plugin.yml 缺少 $SCOREBOARD_COMMAND 指令")
        val commandExecutor = ScoreboardCommand(
            scoreboardService,
            ::reloadScoreboard,
            scoreboardService::providerStatusLines,
        )
        scoreboardCommand.setExecutor(commandExecutor)
        scoreboardCommand.tabCompleter = commandExecutor

        logger.info("EpochCommon 已启用，服务端版本：${VersionUtil.serverVersion(server)}")
    }

    override fun onDisable() {
        if (::scoreboardService.isInitialized) {
            scoreboardService.stop()
            server.servicesManager.unregister(ScoreboardService::class.java, scoreboardService)
        }
        logger.info("EpochCommon 已禁用")
    }

    private fun reloadScoreboard(): Boolean {
        return try {
            reloadConfig()
            scoreboardService.updateSettings(ScoreboardSettings.load(config, logger))
            true
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "统一计分板配置重载失败", exception)
            false
        }
    }

    companion object {
        private const val SCOREBOARD_COMMAND = "esb"
    }
}
