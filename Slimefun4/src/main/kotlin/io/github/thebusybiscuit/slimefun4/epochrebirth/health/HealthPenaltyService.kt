package io.github.thebusybiscuit.slimefun4.epochrebirth.health

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import java.util.UUID

class HealthPenaltyService(
    private val config: RebirthConfig,
    private val store: ResurrectionStore
) {

    fun addPenalty(player: Player, reduction: Double): Double =
        store.addHealthPenalty(player, reduction, config.minHealth)

    fun setPenalty(player: Player, penalty: Double) {
        store.setHealthPenalty(player, penalty, config.minHealth)
    }

    fun restore(player: Player, amount: Double): RestorationResult {
        val before = store.healthPenalty(player)
        val remaining = store.restoreHealthPenalty(player, amount, config.minHealth)
        applyStored(player)
        return RestorationResult(before - remaining, remaining)
    }

    fun applyStored(player: Player) {
        apply(player, store.healthPenalty(player))
    }

    fun reset(player: Player) {
        store.setHealthPenalty(player, 0.0, config.minHealth)
        apply(player, 0.0)
        if (!player.isDead) {
            player.getAttribute(Attribute.MAX_HEALTH)?.let { player.health = it.value }
        }
    }

    private fun apply(player: Player, penalty: Double) {
        val attribute = player.getAttribute(Attribute.MAX_HEALTH) ?: return
        removePenaltyModifiers(attribute)
        if (penalty > 0.0) {
            attribute.addModifier(
                AttributeModifier(
                    HEALTH_MODIFIER_KEY,
                    -penalty,
                    AttributeModifier.Operation.ADD_NUMBER
                )
            )
        }

        if (!player.isDead && player.health > attribute.value) {
            player.health = attribute.value.coerceAtLeast(0.5)
        }
    }

    @Suppress("DEPRECATION")
    private fun removePenaltyModifiers(attribute: AttributeInstance) {
        attribute.modifiers
            .filter {
                it.key == HEALTH_MODIFIER_KEY ||
                    it.uniqueId == LEGACY_HEALTH_MODIFIER_UUID ||
                    it.name == LEGACY_HEALTH_MODIFIER_NAME
            }
            .forEach(attribute::removeModifier)
        attribute.removeModifier(HEALTH_MODIFIER_KEY)
        attribute.removeModifier(LEGACY_HEALTH_MODIFIER_UUID)
    }

    companion object {
        private val HEALTH_MODIFIER_KEY = NamespacedKey("epochrebirth", "health_penalty")
        private const val LEGACY_HEALTH_MODIFIER_NAME = "epoch_rebirth_health_penalty"
        private val LEGACY_HEALTH_MODIFIER_UUID = UUID.fromString("9c6f7a4e-5b3d-4c1a-9e2f-8d7b6a5c4e3d")
    }
}

data class RestorationResult(val restored: Double, val remainingPenalty: Double)
