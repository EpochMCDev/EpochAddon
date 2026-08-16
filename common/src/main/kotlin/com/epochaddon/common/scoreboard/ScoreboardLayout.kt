package com.epochaddon.common.scoreboard

import net.kyori.adventure.text.Component

internal data class ScoreboardSectionContent(
    val key: String,
    val order: Int,
    val maxLines: Int,
    val separatorBefore: Boolean,
    val lines: List<Component>,
)

internal data class ScoreboardLayoutResult(
    val lines: List<Component>,
    val truncatedSections: Map<String, Int>,
    val headerTruncated: Int,
    val footerTruncated: Int,
)

internal object ScoreboardLayout {

    fun compose(
        maxLines: Int,
        header: List<Component>,
        footer: List<Component>,
        separatorEnabled: Boolean,
        separator: Component,
        sections: List<ScoreboardSectionContent>,
    ): ScoreboardLayoutResult {
        val limit = maxLines.coerceIn(1, 15)
        val renderedHeader = header.take(limit)
        val remainingAfterHeader = limit - renderedHeader.size
        val renderedFooter = footer.take(remainingAfterHeader)
        var sectionBudget = remainingAfterHeader - renderedFooter.size
        val renderedSections = mutableListOf<Component>()
        val truncations = linkedMapOf<String, Int>()
        var hasRenderedSection = false

        val sortedSections = sections.sortedWith(compareBy<ScoreboardSectionContent> { it.order }.thenBy { it.key })
        for ((sectionIndex, section) in sortedSections.withIndex()) {
            if (section.lines.isEmpty()) {
                continue
            }

            val providerLimited = section.lines.take(section.maxLines.coerceAtLeast(1))
            val needsSeparator = separatorEnabled && hasRenderedSection && section.separatorBefore
            val separatorCost = if (needsSeparator) 1 else 0
            val availableLines = (sectionBudget - separatorCost).coerceAtLeast(0)
            val rendered = providerLimited.take(availableLines)

            if (rendered.isNotEmpty()) {
                if (needsSeparator) {
                    renderedSections += separator
                    sectionBudget--
                }
                renderedSections += rendered
                sectionBudget -= rendered.size
                hasRenderedSection = true
            }

            val dropped = section.lines.size - rendered.size
            if (dropped > 0) {
                truncations[section.key] = dropped
            }
            if (sectionBudget <= 0) {
                for (remaining in sortedSections.drop(sectionIndex + 1)) {
                    if (remaining.lines.isNotEmpty()) {
                        truncations[remaining.key] = remaining.lines.size
                    }
                }
                break
            }
        }

        return ScoreboardLayoutResult(
            lines = renderedHeader + renderedSections + renderedFooter,
            truncatedSections = truncations,
            headerTruncated = header.size - renderedHeader.size,
            footerTruncated = footer.size - renderedFooter.size,
        )
    }
}
