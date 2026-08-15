package io.github.thebusybiscuit.slimefun4.epochrebirth.config

import io.github.thebusybiscuit.slimefun4.epochrebirth.item.Tier
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.EntityType
import org.bukkit.potion.PotionEffect

/** 配置常量（原 config.yml 内容），API 与原文件版 RebirthConfig 一致 */
class RebirthConfig {

    val maxCount: Int = 8
    val minHealth: Double = 10.0
    val priorityDefault: List<Tier> = listOf(Tier.BASIC, Tier.ADVANCED, Tier.ULTIMATE)
    val hudEnabled: Boolean = true
    val soulCloudSeconds: Int = 2
    val soulAnimals: Set<EntityType> = setOf(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN)
    val boneDrops: Map<EntityType, IntRange> = mapOf(
        EntityType.COW to 1..2,
        EntityType.PIG to 1..2,
        EntityType.SHEEP to 1..2,
        EntityType.CHICKEN to 0..1
    )

    data class Penalty(val maxHealthReduction: Double, val money: Double, val effects: List<PotionEffect>)

    val penalties: Map<String, Penalty> = mapOf(
        "none" to Penalty(10.0, 3200.0, effectsOf(
            "HUNGER" to (3 to 60), "BLINDNESS" to (1 to 5), "MINING_FATIGUE" to (1 to 30),
            "SLOWNESS" to (1 to 120), "WEAKNESS" to (1 to 120)
        )),
        "basic" to Penalty(8.0, 1600.0, effectsOf(
            "HUNGER" to (2 to 60), "BLINDNESS" to (1 to 3), "SLOWNESS" to (1 to 90), "WEAKNESS" to (1 to 60)
        )),
        "advanced" to Penalty(6.0, 800.0, effectsOf(
            "HUNGER" to (1 to 60), "SLOWNESS" to (1 to 30), "WEAKNESS" to (1 to 30)
        )),
        "ultimate" to Penalty(2.0, 200.0, effectsOf(
            "ABSORPTION" to (2 to 180), "REGENERATION" to (2 to 30)
        ))
    )

    fun penaltyFor(id: String): Penalty = penalties[id] ?: Penalty(0.0, 0.0, emptyList())

    fun penaltyFor(tier: Tier?): Penalty = penaltyFor(tier?.id ?: "none")

    fun reload(): Boolean = true

    private fun effectsOf(vararg entries: Pair<String, Pair<Int, Int>>): List<PotionEffect> =
        entries.mapNotNull { (name, levelSeconds) ->
            val type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(name.lowercase())) ?: return@mapNotNull null
            PotionEffect(type, levelSeconds.second * 20, (levelSeconds.first - 1).coerceAtLeast(0))
        }
}
