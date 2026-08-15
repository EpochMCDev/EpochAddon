package io.github.thebusybiscuit.slimefun4.epochrebirth.storage

import io.github.thebusybiscuit.slimefun4.epochrebirth.item.HealingTier
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.PdcKeys
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.nio.ByteBuffer

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

    fun restoreHealthPenalty(player: Player, amount: Double, minHealth: Double): Double {
        val next = (healthPenalty(player) - amount.coerceAtLeast(0.0)).coerceAtLeast(0.0)
        setHealthPenalty(player, next, minHealth)
        return next
    }

    fun healingState(player: Player): HealingState? {
        val container = player.persistentDataContainer
        val encodedQueue = container.get(keys.healingQueue, PersistentDataType.BYTE_ARRAY)
            ?: return migrateLegacyHealingState(player)
        val batches = decodeHealingQueue(encodedQueue)
        if (batches.isNullOrEmpty()) {
            clearHealingState(player)
            return null
        }
        val currentInterval = batches.first().tier.intervalMillis
        val remainingDelay = container.getOrDefault(
            keys.healingRemainingDelay,
            PersistentDataType.LONG,
            currentInterval
        ).coerceIn(1L, currentInterval)
        val nextRecovery = container.get(keys.healingNextRecovery, PersistentDataType.LONG)
        return HealingState(batches, remainingDelay, nextRecovery)
    }

    fun setHealingState(player: Player, state: HealingState) {
        val container = player.persistentDataContainer
        container.set(keys.healingQueue, PersistentDataType.BYTE_ARRAY, encodeHealingQueue(state.batches))
        container.set(keys.healingRemainingDelay, PersistentDataType.LONG, state.remainingDelayMillis)
        state.nextRecoveryAt?.let {
            container.set(keys.healingNextRecovery, PersistentDataType.LONG, it)
        } ?: container.remove(keys.healingNextRecovery)
        container.remove(keys.healingTier)
        container.remove(keys.healingRecoveriesLeft)
    }

    fun clearHealingState(player: Player) {
        val container = player.persistentDataContainer
        container.remove(keys.healingQueue)
        container.remove(keys.healingRemainingDelay)
        container.remove(keys.healingTier)
        container.remove(keys.healingNextRecovery)
        container.remove(keys.healingRecoveriesLeft)
    }

    private fun migrateLegacyHealingState(player: Player): HealingState? {
        val container = player.persistentDataContainer
        val tierId = container.get(keys.healingTier, PersistentDataType.STRING)
        val nextRecovery = container.get(keys.healingNextRecovery, PersistentDataType.LONG)
        val recoveriesLeft = container.get(keys.healingRecoveriesLeft, PersistentDataType.INTEGER)
        if (tierId == null && nextRecovery == null && recoveriesLeft == null) return null

        val tier = tierId?.let(HealingTier::fromId)
        if (tier == null || nextRecovery == null || recoveriesLeft == null || recoveriesLeft <= 0) {
            clearHealingState(player)
            return null
        }
        val state = HealingState(
            listOf(HealingBatch(tier, recoveriesLeft)),
            tier.intervalMillis,
            nextRecovery
        )
        setHealingState(player, state)
        return state
    }

    private fun encodeHealingQueue(batches: List<HealingBatch>): ByteArray {
        val buffer = ByteBuffer.allocate(batches.size * HEALING_BATCH_BYTES)
        batches.forEach { batch ->
            buffer.put(batch.tier.ordinal.toByte())
            buffer.putInt(batch.recoveries)
        }
        return buffer.array()
    }

    private fun decodeHealingQueue(bytes: ByteArray): List<HealingBatch>? {
        if (bytes.isEmpty() || bytes.size % HEALING_BATCH_BYTES != 0) return null
        val buffer = ByteBuffer.wrap(bytes)
        val batches = mutableListOf<HealingBatch>()
        while (buffer.hasRemaining()) {
            val tier = HealingTier.entries.getOrNull(buffer.get().toInt()) ?: return null
            val recoveries = buffer.int
            if (recoveries <= 0) return null
            if (batches.lastOrNull()?.tier == tier) {
                val last = batches.removeLast()
                batches.add(last.copy(recoveries = last.recoveries + recoveries))
            } else {
                batches.add(HealingBatch(tier, recoveries))
            }
        }
        return batches
    }

    private fun normalize(tiers: List<Tier>, default: List<Tier>): List<Tier> {
        val distinct = tiers.distinct()
        return distinct + default.filter { it !in distinct }
    }

    private companion object {
        private const val HEALING_BATCH_BYTES = 5
    }
}

data class HealingBatch(val tier: HealingTier, val recoveries: Int)

data class HealingState(
    val batches: List<HealingBatch>,
    val remainingDelayMillis: Long,
    val nextRecoveryAt: Long?
) {
    init {
        require(batches.isNotEmpty())
        require(batches.all { it.recoveries > 0 })
    }

    val tier: HealingTier
        get() = batches.first().tier

    val recoveriesLeft: Int
        get() = batches.sumOf { it.recoveries }

    fun remainingMillis(now: Long): Long = HealingTier.entries.sumOf { remainingMillis(it, now) }

    fun remainingMillis(tier: HealingTier, now: Long): Long {
        val fullDuration = batches
            .filter { it.tier == tier }
            .sumOf { it.tier.intervalMillis * it.recoveries }
        if (this.tier != tier || fullDuration == 0L) return fullDuration

        val currentInterval = tier.intervalMillis
        val currentDelay = nextRecoveryAt
            ?.let { (it - now).coerceIn(0L, currentInterval) }
            ?: remainingDelayMillis.coerceIn(0L, currentInterval)
        return fullDuration - currentInterval + currentDelay
    }

    fun append(tier: HealingTier, recoveries: Int = tier.recoveries): HealingState {
        if (recoveries <= 0) return this
        val updated = batches.toMutableList()
        if (updated.last().tier == tier) {
            val last = updated.removeLast()
            updated.add(last.copy(recoveries = last.recoveries + recoveries))
        } else {
            updated.add(HealingBatch(tier, recoveries))
        }
        return copy(batches = updated)
    }

    fun pause(now: Long): HealingState {
        val scheduled = nextRecoveryAt ?: return this
        val interval = tier.intervalMillis
        val remaining = (scheduled - now).takeIf { it > 0L && it <= interval } ?: interval
        return copy(remainingDelayMillis = remaining, nextRecoveryAt = null)
    }

    fun resume(now: Long): HealingState {
        if (nextRecoveryAt != null) return this
        val interval = tier.intervalMillis
        val delay = remainingDelayMillis.coerceIn(1L, interval)
        return copy(remainingDelayMillis = delay, nextRecoveryAt = now + delay)
    }

    fun advance(now: Long, maximumRecoveries: Int = Int.MAX_VALUE): HealingAdvance {
        var scheduled = nextRecoveryAt ?: return HealingAdvance(0, this)
        if (maximumRecoveries <= 0 || now < scheduled) return HealingAdvance(0, this)

        val remaining = batches.toMutableList()
        var consumed = 0
        while (consumed < maximumRecoveries && scheduled <= now && remaining.isNotEmpty()) {
            val first = remaining.first()
            if (first.recoveries == 1) {
                remaining.removeFirst()
            } else {
                remaining[0] = first.copy(recoveries = first.recoveries - 1)
            }
            consumed++
            if (remaining.isNotEmpty()) {
                scheduled += remaining.first().tier.intervalMillis
            }
        }

        if (remaining.isEmpty()) return HealingAdvance(consumed, null)
        val nextInterval = remaining.first().tier.intervalMillis
        return HealingAdvance(
            consumed,
            HealingState(remaining, nextInterval, scheduled)
        )
    }

    companion object {
        fun create(tier: HealingTier, now: Long, running: Boolean): HealingState = HealingState(
            listOf(HealingBatch(tier, tier.recoveries)),
            tier.intervalMillis,
            if (running) now + tier.intervalMillis else null
        )
    }
}

data class HealingAdvance(val dueRecoveries: Int, val nextState: HealingState?)
