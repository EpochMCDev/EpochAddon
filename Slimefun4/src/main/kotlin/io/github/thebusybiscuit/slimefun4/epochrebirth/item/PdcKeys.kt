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

    private fun key(name: String): NamespacedKey = NamespacedKey(namespace, name)
}
