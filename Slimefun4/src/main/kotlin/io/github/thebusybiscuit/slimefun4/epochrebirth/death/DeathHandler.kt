package io.github.thebusybiscuit.slimefun4.epochrebirth.death

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.economy.EconomyService
import io.github.thebusybiscuit.slimefun4.epochrebirth.health.HealthPenaltyService
import io.github.thebusybiscuit.slimefun4.epochrebirth.hud.RebirthHud
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import java.util.UUID

class DeathHandler(
    private val plugin: JavaPlugin,
    private val config: RebirthConfig,
    private val store: ResurrectionStore,
    private val health: HealthPenaltyService,
    private val economy: EconomyService,
    private val hud: RebirthHud,
    private val language: LanguageService
) : Listener {

    /** 死亡时写入的 PDC 可能因 Paper 复活重读玩家数据而丢失，这里用内存暂存保底 */
    private val pendingPenalties = HashMap<UUID, Double>()

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.player
        val usedTier = store.order(player, config.priorityDefault).firstOrNull { store.count(player, it) > 0 }
        if (usedTier != null) {
            store.setCount(player, usedTier, store.count(player, usedTier) - 1)
        }
        val penalty = config.penaltyFor(usedTier)

        val moneyOk = economy.withdraw(player, penalty.money)
        val healthPenalty = health.addPenalty(player, penalty.maxHealthReduction)
        pendingPenalties[player.uniqueId] = healthPenalty
        store.setLastDeath(player, usedTier?.id ?: "none")

        val tierName = language.plain("menu.tier-name-" + (usedTier?.id ?: "none"))
        val moneyText = if (moneyOk) String.format(Locale.ROOT, "%.0f", penalty.money) else "?"
        val messageKey = if (usedTier == null) "messages.death-no-totem" else "messages.death-consumed"
        player.sendMessage(language.component(messageKey, mapOf("tier" to tierName, "money" to moneyText), rawValues = setOf("tier")))
        if (!moneyOk) {
            player.sendMessage(language.component("messages.economy-failed"))
        }
        hud.update(player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        schedulePending(event.player)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        schedulePending(event.player)
    }

    /** 重生/上线后延迟 1 tick 应用效果与生命上限，避免被原版复活流程清掉。 */
    private fun schedulePending(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            pendingPenalties.remove(player.uniqueId)?.let { health.setPenalty(player, it) }
            health.applyStored(player)

            val id = store.lastDeath(player)
            if (id != null) {
                store.clearLastDeath(player)
                config.penaltyFor(id).effects.forEach { player.addPotionEffect(it) }
            }
            hud.update(player)
        })
    }

    /** 重置玩家的生命上限惩罚（清空 PDC 并移除 modifier），供 /erb resethealth 使用 */
    fun resetHealthPenalty(player: Player) {
        pendingPenalties.remove(player.uniqueId)
        health.reset(player)
    }
}
