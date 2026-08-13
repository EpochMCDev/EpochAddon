package io.github.thebusybiscuit.slimefun4.epochrebirth.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.RegisteredServiceProvider

class EconomyService {

    private var economy: Economy? = null

    fun setup(): Boolean {
        val registration: RegisteredServiceProvider<Economy>? =
            Bukkit.getServicesManager().getRegistration(Economy::class.java)
        economy = registration?.provider
        return economy != null
    }

    /**
     * 扣除金币，余额允许被扣成负数。
     * 先尝试正常提款；若经济插件拒绝，再尝试存入负数金额作为回退。
     */
    fun withdraw(player: Player, amount: Double): Boolean {
        val economy = economy ?: return false
        if (amount <= 0) return true
        if (economy.withdrawPlayer(player, amount).transactionSuccess()) return true
        return try {
            economy.depositPlayer(player, -amount).transactionSuccess()
        } catch (ignored: Exception) {
            false
        }
    }
}
