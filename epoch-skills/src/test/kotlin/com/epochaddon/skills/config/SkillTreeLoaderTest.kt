package com.epochaddon.skills.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class SkillTreeLoaderTest {
    @Test
    fun `contains the documented digging tree resource`() {
        val resource = SkillTreeLoaderTest::class.java.getResource("/trees/digging_skt_01.yml")
            ?: error("digging tree resource is missing")
        val config = YamlConfiguration.loadConfiguration(File(resource.toURI()))

        assertEquals("gathering_digging_skt_01", config.getString("id"))
        assertEquals("gathering.digging", config.getString("profession-id"))
        assertEquals(
            setOf("digger", "prospector", "practice_makes_perfect"),
            config.getConfigurationSection("nodes")?.getKeys(false),
        )
        assertEquals(true, config.getBoolean("nodes.digger.auto-unlock"))
        assertEquals(false, config.getBoolean("nodes.prospector.auto-unlock"))
    }
}
