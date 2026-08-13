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
    TOTEM_ULTIMATE("totem_ultimate");

    companion object {
        fun fromId(id: String): RebirthItem? = entries.firstOrNull { it.id == id }
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
