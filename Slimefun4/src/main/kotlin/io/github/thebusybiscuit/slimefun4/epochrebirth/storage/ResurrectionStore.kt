package io.github.thebusybiscuit.slimefun4.epochrebirth.storage

import io.github.thebusybiscuit.slimefun4.epochrebirth.item.PdcKeys
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

class ResurrectionStore(private val keys: PdcKeys, private val maxCount: () -> Int) {

    fun count(player: Player, tier: Tier): Int =
        player.persistentDataContainer.getOrDefault(keys.counts.getValue(tier), PersistentDataType.INTEGER, 0)

    fun setCount(player: Player, tier: Tier, count: Int) {
        player.persistentDataContainer.set(keys.counts.getValue(tier), PersistentDataType.INTEGER, count.coerceIn(0, maxCount()))
    }

    fun add(player: Player, tier: Tier, delta: Int = 1): Int? {
        val next = count(player, tier) + delta
        if (next > maxCount()) return null
        setCount(player, tier, next)
        return next
    }

    fun order(player: Player, default: List<Tier>): List<Tier> {
        val bytes = player.persistentDataContainer.get(keys.order, PersistentDataType.BYTE_ARRAY)
        if (bytes == null) return default
        return normalize(bytes.map { Tier.entries.getOrNull(it.toInt()) }.filterNotNull(), default)
    }

    fun setOrder(player: Player, order: List<Tier>) {
        player.persistentDataContainer.set(
            keys.order,
            PersistentDataType.BYTE_ARRAY,
            order.map { it.ordinal.toByte() }.toByteArray()
        )
    }

    fun lastDeath(player: Player): String? =
        player.persistentDataContainer.get(keys.lastDeath, PersistentDataType.STRING)

    fun setLastDeath(player: Player, id: String) {
        player.persistentDataContainer.set(keys.lastDeath, PersistentDataType.STRING, id)
    }

    fun clearLastDeath(player: Player) {
        player.persistentDataContainer.remove(keys.lastDeath)
    }

    /** 已累计扣除的生命上限（每次死亡叠加，直到上限只剩 minHealth） */
    fun healthPenalty(player: Player): Double =
        player.persistentDataContainer.getOrDefault(keys.healthPenalty, PersistentDataType.DOUBLE, 0.0)

    /** 叠加生命惩罚，返回累计值（不超过 20 - minHealth） */
    fun addHealthPenalty(player: Player, reduction: Double, minHealth: Double): Double {
        val next = (healthPenalty(player) + reduction).coerceAtMost(20.0 - minHealth)
        player.persistentDataContainer.set(keys.healthPenalty, PersistentDataType.DOUBLE, next)
        return next
    }

    fun setHealthPenalty(player: Player, value: Double, minHealth: Double) {
        player.persistentDataContainer.set(
            keys.healthPenalty,
            PersistentDataType.DOUBLE,
            value.coerceIn(0.0, 20.0 - minHealth)
        )
    }

    private fun normalize(tiers: List<Tier>, default: List<Tier>): List<Tier> {
        val distinct = tiers.distinct()
        return distinct + default.filter { it !in distinct }
    }
}
