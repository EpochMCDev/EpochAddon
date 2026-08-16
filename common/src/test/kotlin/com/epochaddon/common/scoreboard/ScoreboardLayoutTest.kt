package com.epochaddon.common.scoreboard

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScoreboardLayoutTest {

    @Test
    fun ordersSectionsAndReservesFooterSpace() {
        val result = ScoreboardLayout.compose(
            maxLines = 7,
            header = listOf(text("header")),
            footer = listOf(text("footer")),
            separatorEnabled = true,
            separator = text("separator"),
            sections = listOf(
                section("second", 200, "b1", "b2"),
                section("first", 100, "a1", "a2"),
            ),
        )

        assertEquals(
            listOf("header", "a1", "a2", "separator", "b1", "b2", "footer").map(::text),
            result.lines,
        )
        assertEquals(emptyMap<String, Int>(), result.truncatedSections)
    }

    @Test
    fun reportsProviderAndGlobalTruncation() {
        val result = ScoreboardLayout.compose(
            maxLines = 5,
            header = emptyList(),
            footer = listOf(text("footer")),
            separatorEnabled = true,
            separator = Component.empty(),
            sections = listOf(
                section("first", 100, "a1", "a2", "a3", maxLines = 2),
                section("second", 200, "b1", "b2"),
            ),
        )

        assertEquals(listOf(text("a1"), text("a2"), Component.empty(), text("b1"), text("footer")), result.lines)
        assertEquals(mapOf("first" to 1, "second" to 1), result.truncatedSections)
    }

    @Test
    fun capsHeaderAndFooterAtMinecraftLimit() {
        val result = ScoreboardLayout.compose(
            maxLines = 15,
            header = (1..14).map { text("h$it") },
            footer = listOf(text("f1"), text("f2")),
            separatorEnabled = false,
            separator = Component.empty(),
            sections = emptyList(),
        )

        assertEquals(15, result.lines.size)
        assertEquals(1, result.footerTruncated)
    }

    private fun section(
        key: String,
        order: Int,
        vararg lines: String,
        maxLines: Int = 15,
    ) = ScoreboardSectionContent(
        key = key,
        order = order,
        maxLines = maxLines,
        separatorBefore = true,
        lines = lines.map(::text),
    )

    private fun text(value: String): Component = Component.text(value)
}
