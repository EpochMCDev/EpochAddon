package com.epochaddon.skills.model

class ExperienceCurve(thresholds: Map<Int, Long>) {
    private val thresholds = thresholds
        .filter { (level, experience) -> level > 0 && experience >= 0L }
        .toSortedMap()

    fun threshold(level: Int): Long? = thresholds[level]

    fun level(experience: Long): Int {
        return thresholds.entries.lastOrNull { experience >= it.value }?.key ?: 0
    }

    fun nextThreshold(experience: Long): Long? {
        return thresholds.entries.firstOrNull { experience < it.value }?.value
    }

    fun entries(): Map<Int, Long> = thresholds.toMap()
}
