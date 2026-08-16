package com.epochaddon.common.scoreboard

data class ScoreboardProviderOptions(
    val order: Int,
    val enabledByDefault: Boolean = true,
    val maxLines: Int = 15,
    val separatorBefore: Boolean = true,
    val permission: String? = null,
) {
    init {
        require(maxLines > 0) { "Scoreboard provider maxLines must be positive" }
    }
}
