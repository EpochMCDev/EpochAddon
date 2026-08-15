package io.github.thebusybiscuit.slimefun4.epochrebirth.item

import org.bukkit.NamespacedKey

class PdcKeys {

    private val namespace = "epochrebirth"

    val itemId: NamespacedKey = key("item_id")
    val soulCloud: NamespacedKey = key("soul_cloud")
    val counts: Map<Tier, NamespacedKey> = Tier.entries.associateWith { key("count.${it.id}") }
    val order: NamespacedKey = key("order")
    val lastDeath: NamespacedKey = key("last_death")
    val healthPenalty: NamespacedKey = key("health_penalty")
    val healingQueue: NamespacedKey = key("healing_queue")
    val healingRemainingDelay: NamespacedKey = key("healing_remaining_delay")
    val healingTier: NamespacedKey = key("healing_tier")
    val healingNextRecovery: NamespacedKey = key("healing_next_recovery")
    val healingRecoveriesLeft: NamespacedKey = key("healing_recoveries_left")

    private fun key(name: String): NamespacedKey = NamespacedKey(namespace, name)
}
