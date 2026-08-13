package com.epochaddon.plugintwo

import com.epochaddon.common.util.VersionUtil
import org.bukkit.plugin.java.JavaPlugin

class PluginTwo : JavaPlugin() {

    override fun onEnable() {
        logger.info("PluginTwo 已启用，服务端版本：${VersionUtil.serverVersion(server)}")
    }

    override fun onDisable() {
        logger.info("PluginTwo 已禁用")
    }
}
