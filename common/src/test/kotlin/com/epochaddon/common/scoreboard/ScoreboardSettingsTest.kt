package com.epochaddon.common.scoreboard

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.logging.Logger

class ScoreboardSettingsTest {

    @Test
    fun resolvesProviderOverridesAndNormalizesKeys() {
        val config = YamlConfiguration()
        config.set("scoreboard.max-lines", 12)
        config.set("scoreboard.ownership-mode", "yield")
        config.set("scoreboard.provider-defaults.max-lines", 8)
        config.set("scoreboard.provider-defaults.permission", "scoreboard.default")
        config.set("scoreboard.providers.epochminerals.minerals.enabled", false)
        config.set("scoreboard.providers.epochminerals.minerals.order", 25)
        config.set("scoreboard.providers.epochminerals.minerals.max-lines", 6)
        config.set("scoreboard.providers.epochminerals.minerals.permission", "")
        config.set("scoreboard.providers.epochminerals.minerals.worlds", listOf("World", "Mining"))

        val settings = ScoreboardSettings.load(config, Logger.getAnonymousLogger())
        val provider = settings.provider(
            "EpochMinerals",
            "minerals",
            ScoreboardProviderOptions(order = 100, maxLines = 9, permission = "code.permission"),
        )

        assertEquals(ScoreboardOwnershipMode.YIELD, settings.ownershipMode)
        assertEquals("epochminerals.minerals", provider.key)
        assertFalse(provider.enabled)
        assertEquals(25, provider.order)
        assertEquals(6, provider.maxLines)
        assertNull(provider.permission)
        assertEquals(setOf("world", "mining"), provider.worlds)
    }

    @Test
    fun appliesProviderDefaultsWhenNoOverrideExists() {
        val config = YamlConfiguration()
        config.set("scoreboard.default-enabled", false)
        config.set("scoreboard.persist-player-preferences", true)
        config.set("scoreboard.provider-defaults.max-lines", 7)
        config.set("scoreboard.provider-defaults.separator-before", false)

        val settings = ScoreboardSettings.load(config, Logger.getAnonymousLogger())
        val provider = settings.provider(
            "Example Plugin",
            "stats",
            ScoreboardProviderOptions(order = 300, maxLines = 10),
        )

        assertFalse(settings.defaultEnabled)
        assertTrue(settings.persistPlayerPreferences)
        assertEquals("example_plugin.stats", provider.key)
        assertEquals(300, provider.order)
        assertEquals(7, provider.maxLines)
        assertFalse(provider.separatorBefore)
        assertTrue(provider.enabled)
    }
}
