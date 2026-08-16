package com.epochaddon.common.scoreboard

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

internal data class NodesMembership(
    val town: String?,
    val nation: String?,
)

internal class NodesBridge(private val plugin: JavaPlugin) {

    private data class Access(
        val owner: Plugin,
        val nodes: Any,
        val getResident: Method,
        val getTown: Method,
        val getNation: Method,
        val getTownName: Method,
        val getTownNation: Method,
        val getNationName: Method,
    )

    private var access: Access? = null

    fun membership(player: Player): NodesMembership? {
        val nodesPlugin = plugin.server.pluginManager.getPlugin(NODES_PLUGIN_NAME)
            ?.takeIf { it.isEnabled }
            ?: run {
                access = null
                return null
            }
        val current = access?.takeIf { it.owner === nodesPlugin }
            ?: runCatching { resolveAccess(nodesPlugin) }.getOrNull()?.also { access = it }
            ?: return null

        return runCatching {
            val resident = current.getResident.invoke(current.nodes, player)
                ?: return@runCatching NodesMembership(null, null)
            val town = current.getTown.invoke(resident)
            val nation = current.getNation.invoke(resident)
                ?: if (town != null) current.getTownNation.invoke(town) else null
            NodesMembership(
                town = town?.let { current.getTownName.invoke(it) as? String },
                nation = nation?.let { current.getNationName.invoke(it) as? String },
            )
        }.getOrElse {
            access = null
            null
        }
    }

    private fun resolveAccess(nodesPlugin: Plugin): Access {
        val loader = nodesPlugin.javaClass.classLoader
        val nodesClass = Class.forName("phonon.nodes.Nodes", true, loader)
        val residentClass = Class.forName("phonon.nodes.objects.Resident", true, loader)
        val townClass = Class.forName("phonon.nodes.objects.Town", true, loader)
        val nationClass = Class.forName("phonon.nodes.objects.Nation", true, loader)
        return Access(
            owner = nodesPlugin,
            nodes = nodesClass.getField("INSTANCE").get(null),
            getResident = nodesClass.getMethod("getResident", Player::class.java),
            getTown = residentClass.getMethod("getTown"),
            getNation = residentClass.getMethod("getNation"),
            getTownName = townClass.getMethod("getName"),
            getTownNation = townClass.getMethod("getNation"),
            getNationName = nationClass.getMethod("getName"),
        )
    }

    companion object {
        private const val NODES_PLUGIN_NAME = "nodes"
    }
}
