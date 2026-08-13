package io.github.thebusybiscuit.slimefun4.epochrebirth.death

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.economy.EconomyService
import io.github.thebusybiscuit.slimefun4.epochrebirth.hud.RebirthHud
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
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
        val healthPenalty = store.addHealthPenalty(player, penalty.maxHealthReduction, config.minHealth)
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

    /** 重生/上线后延迟 1 tick 应用效果与生命上限，避免被原版复活流程清掉 */
    private fun schedulePending(player: Player) {
        val id = store.lastDeath(player) ?: return
        store.clearLastDeath(player)
        val penalty = config.penaltyFor(id)
        val healthPenalty = pendingPenalties.remove(player.uniqueId) ?: store.healthPenalty(player)
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            store.setHealthPenalty(player, healthPenalty, config.minHealth)
            applyMaxHealth(player, healthPenalty)
            penalty.effects.forEach { player.addPotionEffect(it) }
        })
    }

    /** 用固定 UUID 的 AttributeModifier 修改生命上限，避免原版重生流程覆盖 baseValue */
    private fun applyMaxHealth(player: Player, healthPenalty: Double) {
        val attribute = player.getAttribute(Attribute.MAX_HEALTH) ?: return
        removePenaltyModifiers(attribute)
        if (healthPenalty > 0.0) {
            attribute.addModifier(
                AttributeModifier(
                    HEALTH_MODIFIER_UUID,
                    "epoch_rebirth_health_penalty",
                    -healthPenalty,
                    AttributeModifier.Operation.ADD_NUMBER
                )
            )
        }
    }

    /**
     * 移除生命惩罚 modifier。
     * Paper 1.21.5+ 的 getModifiers() 可能返回服务端内部生成的 UUID，
     * 因此同时按 UUID、name 匹配，并用接口的按 UUID 移除方法兜底。
     */
    private fun removePenaltyModifiers(attribute: org.bukkit.attribute.AttributeInstance) {
        attribute.getModifiers()
            .filter { it.uniqueId == HEALTH_MODIFIER_UUID || it.name == "epoch_rebirth_health_penalty" }
            .forEach { attribute.removeModifier(it) }
        attribute.removeModifier(HEALTH_MODIFIER_UUID)
    }

    /** 重置玩家的生命上限惩罚（清空 PDC 并移除 modifier），供 /erb resethealth 使用 */
    fun resetHealthPenalty(player: Player) {
        store.setHealthPenalty(player, 0.0, config.minHealth)
        // 死亡未重生时内存暂存了待生效惩罚，必须一并清掉，否则重生时会被重新扣回
        pendingPenalties.remove(player.uniqueId)
        val attribute = player.getAttribute(Attribute.MAX_HEALTH)
        plugin.logger.info(
            "[resethealth] before: max=${player.maxHealth} health=${player.health} " +
                "value=${attribute?.value} base=${attribute?.baseValue} default=${attribute?.defaultValue} " +
                "mods=${attribute?.modifiers?.map { "${it.uniqueId}:${it.name}:${it.amount}" }}"
        )
        attribute?.let {
            removePenaltyModifiers(it)
            // 兜底：本插件从不修改 baseValue，若被污染则恢复为默认 20
            if (it.baseValue != it.defaultValue) {
                it.setBaseValue(it.defaultValue)
            }
        }
        // 上限恢复后把当前血量补满，否则玩家看到的仍是扣减后的血量
        player.health = player.maxHealth
        plugin.logger.info(
            "[resethealth] after: max=${player.maxHealth} health=${player.health} " +
                "value=${attribute?.value} base=${attribute?.baseValue} " +
                "mods=${attribute?.modifiers?.map { "${it.uniqueId}:${it.name}:${it.amount}" }}"
        )
    }

    companion object {
        private val HEALTH_MODIFIER_UUID = UUID.fromString("9c6f7a4e-5b3d-4c1a-9e2f-8d7b6a5c4e3d")
    }
}