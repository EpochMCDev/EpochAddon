package com.epochaddon.common

import com.epochaddon.common.command.ScoreboardCommand
import com.epochaddon.common.listener.JoinListener
import com.epochaddon.common.scoreboard.EpochScoreboardService
import com.epochaddon.common.scoreboard.ScoreboardService
import com.epochaddon.common.util.VersionUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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
            defaultEnabled = config.getBoolean(
                "scoreboard.default-enabled",
                config.getBoolean("scoreboard.enabled", true),
            ),
            title = scoreboardTitle(),
            refreshTicks = config.getLong("scoreboard.refresh-ticks", 20L).coerceAtLeast(1L),
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
        val commandExecutor = ScoreboardCommand(scoreboardService)
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

    private fun scoreboardTitle(): Component {
        val configured = config.getString("scoreboard.title", DEFAULT_SCOREBOARD_TITLE) ?: DEFAULT_SCOREBOARD_TITLE
        return try {
            MiniMessage.miniMessage().deserialize(configured)
        } catch (exception: Exception) {
            logger.log(Level.WARNING, "计分板标题 MiniMessage 格式无效，使用默认标题", exception)
            MiniMessage.miniMessage().deserialize(DEFAULT_SCOREBOARD_TITLE)
        }
    }

    companion object {
        private const val DEFAULT_SCOREBOARD_TITLE = "<dark_red>☠ <red>EpochMC"
        private const val SCOREBOARD_COMMAND = "esb"
    }
}
