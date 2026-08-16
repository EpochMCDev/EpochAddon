package com.epochaddon.common.scoreboard

import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

internal class PlaceholderApiBridge(private val plugin: JavaPlugin) {

    private data class Access(
        val owner: Plugin,
        val setPlaceholders: Method,
    )

    private var access: Access? = null

    fun resolve(player: OfflinePlayer, placeholder: String): String? {
        val placeholderApi = plugin.server.pluginManager.getPlugin(PLACEHOLDER_API_PLUGIN_NAME)
            ?.takeIf { it.isEnabled }
            ?: run {
                access = null
                return null
            }
        val current = access?.takeIf { it.owner === placeholderApi }
            ?: runCatching { resolveAccess(placeholderApi) }.getOrNull()?.also { access = it }
            ?: return null

        return runCatching {
            (current.setPlaceholders.invoke(null, player, placeholder) as? String)
                ?.takeUnless { it == placeholder }
        }.getOrElse {
            access = null
            null
        }
    }

    private fun resolveAccess(placeholderApi: Plugin): Access {
        val apiClass = Class.forName(
            "me.clip.placeholderapi.PlaceholderAPI",
            true,
            placeholderApi.javaClass.classLoader,
        )
        return Access(
            owner = placeholderApi,
            setPlaceholders = apiClass.getMethod(
                "setPlaceholders",
                OfflinePlayer::class.java,
                String::class.java,
            ),
        )
    }

    companion object {
        private const val PLACEHOLDER_API_PLUGIN_NAME = "PlaceholderAPI"
    }
}
