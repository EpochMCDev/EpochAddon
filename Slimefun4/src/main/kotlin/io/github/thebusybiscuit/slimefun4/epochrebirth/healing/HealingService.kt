package io.github.thebusybiscuit.slimefun4.epochrebirth.healing

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.health.HealthPenaltyService
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.HealingTier
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.HealingState
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import kotlin.math.ceil

class HealingService(
    private val plugin: JavaPlugin,
    private val store: ResurrectionStore,
    private val health: HealthPenaltyService,
    private val items: RebirthItems,
    private val language: LanguageService,
    private val clock: () -> Long = System::currentTimeMillis
) : Listener {

    fun start() {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            plugin.server.onlinePlayers.forEach { tick(it, clock()) }
        }, 20L, 20L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (event.player.isOnline) {
                tick(event.player, clock())
            }
        })
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val hand = event.hand ?: return
        val player = event.player
        val stack = itemInHand(player, hand)
        val tier = HealingTier.fromItem(items.identityOf(stack)) ?: return
        event.isCancelled = true

        val now = clock()
        tick(player, now)
        val running = store.healthPenalty(player) > 0.0
        val state = store.healingState(player)
            ?.append(tier)
            ?: HealingState.create(tier, now, running)
        consumeOne(player, hand)
        store.setHealingState(player, state)
        player.sendMessage(language.component(
            "messages.healing-started",
            mapOf(
                "tier" to language.plain("healing.tier-name-${tier.id}"),
                "time" to formatHealingDuration(state.remainingMillis(now)),
                "i_time" to formatHealingDuration(state.remainingMillis(HealingTier.I, now)),
                "ii_time" to formatHealingDuration(state.remainingMillis(HealingTier.II, now))
            ),
            rawValues = setOf("tier")
        ))
        showActionBar(player, state, now, !running)
        player.damage(2.0)
    }

    private fun tick(player: Player, now: Long) {
        if (player.isDead) return
        val state = store.healingState(player) ?: return
        val penalty = store.healthPenalty(player)
        if (penalty <= 0.0) {
            val paused = state.pause(now)
            store.setHealingState(player, paused)
            showActionBar(player, paused, now, true)
            return
        }

        val running = state.resume(now)
        val recoveriesNeeded = ceil(penalty / HEALTH_PER_RECOVERY).toInt()
        val advance = running.advance(now, recoveriesNeeded)
        if (advance.dueRecoveries <= 0) {
            store.setHealingState(player, running)
            showActionBar(player, running, now, false)
            return
        }

        val result = health.restore(player, advance.dueRecoveries * HEALTH_PER_RECOVERY)
        if (result.restored > 0.0) {
            player.sendMessage(language.component(
                "messages.healing-restored",
                mapOf("hearts" to formatHearts(result.restored / 2.0))
            ))
        }

        val nextState = advance.nextState
        if (nextState == null) {
            store.clearHealingState(player)
            player.sendMessage(language.component("messages.healing-complete"))
            player.sendActionBar(Component.empty())
        } else {
            val updated = if (result.remainingPenalty <= 0.0) nextState.pause(now) else nextState
            store.setHealingState(player, updated)
            showActionBar(player, updated, now, result.remainingPenalty <= 0.0)
        }
    }

    private fun showActionBar(player: Player, state: HealingState, now: Long, stored: Boolean) {
        val key = if (stored) "actionbar.healing-stored" else "actionbar.healing-active"
        player.sendActionBar(language.component(
            key,
            mapOf(
                "tier" to language.plain("healing.tier-name-${state.tier.id}"),
                "time" to formatHealingDuration(state.remainingMillis(now)),
                "i_time" to formatHealingDuration(state.remainingMillis(HealingTier.I, now)),
                "ii_time" to formatHealingDuration(state.remainingMillis(HealingTier.II, now))
            ),
            rawValues = setOf("tier")
        ))
    }

    private fun itemInHand(player: Player, hand: EquipmentSlot) =
        if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand

    private fun consumeOne(player: Player, hand: EquipmentSlot) {
        val stack = itemInHand(player, hand)
        if (stack.amount > 1) {
            stack.amount -= 1
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.inventory.setItemInOffHand(null)
        } else {
            player.inventory.setItemInMainHand(null)
        }
    }

    private fun formatHearts(hearts: Double): String {
        val rounded = hearts.toInt()
        return if (hearts == rounded.toDouble()) {
            rounded.toString()
        } else {
            String.format(Locale.ROOT, "%.1f", hearts)
        }
    }

    companion object {
        private const val HEALTH_PER_RECOVERY = 2.0
    }
}

fun formatHealingDuration(milliseconds: Long): String {
    val totalSeconds = ((milliseconds.coerceAtLeast(0L) + 999L) / 1000L)
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}
