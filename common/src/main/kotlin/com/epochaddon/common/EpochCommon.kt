package com.epochaddon.common

import com.epochaddon.common.listener.JoinListener
import com.epochaddon.common.util.VersionUtil
import org.bukkit.plugin.java.JavaPlugin

class EpochCommon : JavaPlugin() {

    override fun onEnable() {
        logger.info("EpochCommon 已启用，服务端版本：${VersionUtil.serverVersion(server)}")
        server.pluginManager.registerEvents(JoinListener(this), this)
    }

    override fun onDisable() {
        logger.info("EpochCommon 已禁用")
    }
}
