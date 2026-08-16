package com.epochaddon.common.scoreboard

import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

internal data class VaultBalance(
    val amount: Double,
    val formatted: String?,
)

internal class VaultEconomyBridge(private val plugin: JavaPlugin) {

    private data class Access(
        val provider: Any,
        val getBalance: Method,
        val format: Method?,
    )

    private var access: Access? = null

    fun balance(player: OfflinePlayer): VaultBalance? {
        if (!plugin.server.pluginManager.isPluginEnabled("Vault")) {
            access = null
            return null
        }

        val current = access ?: runCatching { resolveAccess() }.getOrNull()?.also { access = it } ?: return null
        return runCatching {
            val amount = (current.getBalance.invoke(current.provider, player) as Number).toDouble()
            val formatted = current.format?.invoke(current.provider, amount) as? String
            VaultBalance(amount, formatted)
        }.getOrElse {
            access = null
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveAccess(): Access? {
        val vault = plugin.server.pluginManager.getPlugin("Vault") ?: return null
        val economyClass = Class.forName(
            "net.milkbowl.vault.economy.Economy",
            true,
            vault.javaClass.classLoader,
        ) as Class<Any>
        val provider = plugin.server.servicesManager.getRegistration(economyClass)?.provider ?: return null
        return Access(
            provider = provider,
            getBalance = economyClass.getMethod("getBalance", OfflinePlayer::class.java),
            format = economyClass.methods.firstOrNull { method ->
                method.name == "format" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == java.lang.Double.TYPE
            },
        )
    }
}
