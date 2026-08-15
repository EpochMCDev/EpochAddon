package io.github.thebusybiscuit.slimefun4.epochrebirth.item

enum class RebirthItem(val id: String) {
    SOUL_BOTTLE("soul_bottle"),
    SOUL("soul"),
    MEAT("meat"),
    CORE_BASIC("core_basic"),
    CORE_ADVANCED("core_advanced"),
    CORE_ULTIMATE("core_ultimate"),
    TOTEM_BASIC("totem_basic"),
    TOTEM_ADVANCED("totem_advanced"),
    TOTEM_ULTIMATE("totem_ultimate"),
    HEALING_CORE("healing_core"),
    HEALING_ARROW_I("healing_arrow_i"),
    HEALING_ARROW_II("healing_arrow_ii");

    companion object {
        fun fromId(id: String): RebirthItem? = entries.firstOrNull { it.id == id }
    }
}

enum class HealingTier(
    val id: String,
    val item: RebirthItem,
    val intervalSeconds: Int,
    val recoveries: Int = 5
) {
    I("i", RebirthItem.HEALING_ARROW_I, 36),
    II("ii", RebirthItem.HEALING_ARROW_II, 30);

    val durationSeconds: Int
        get() = intervalSeconds * recoveries

    val intervalMillis: Long
        get() = intervalSeconds * 1000L

    companion object {
        fun fromId(id: String): HealingTier? = entries.firstOrNull { it.id == id }

        fun fromItem(item: RebirthItem?): HealingTier? = entries.firstOrNull { it.item == item }
    }
}

enum class Tier(val id: String, val item: RebirthItem) {
    BASIC("basic", RebirthItem.TOTEM_BASIC),
    ADVANCED("advanced", RebirthItem.TOTEM_ADVANCED),
    ULTIMATE("ultimate", RebirthItem.TOTEM_ULTIMATE);

    companion object {
        fun fromId(id: String): Tier? = entries.firstOrNull { it.id == id }
    }
}
