package com.epochmarket.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundServiceTest {
    private final SoundService service = new SoundService(java.util.logging.Logger.getLogger("test"));

    @Test
    void parsesEnabledSoundAndVolume() {
        service.reload(section("""
                sounds:
                  add:
                    enabled: true
                    sound: block_note_block_pling
                    volume: 0.5
                """).getConfigurationSection("sounds"));
        SoundService.Setting add = service.setting(SoundService.Trigger.ADD);
        assertTrue(add.enabled());
        assertEquals("BLOCK_NOTE_BLOCK_PLING", add.soundName());
        assertEquals(0.5f, add.volume());
    }

    @Test
    void lowercaseSoundNameIsNormalizedToUppercase() {
        service.reload(section("""
                sounds:
                  add:
                    sound: block_note_block_pling
                """).getConfigurationSection("sounds"));
        assertEquals("BLOCK_NOTE_BLOCK_PLING", service.setting(SoundService.Trigger.ADD).soundName());
    }

    @Test
    void missingSoundsSectionFallsBackToDefaults() {
        service.reload(null);
        assertEquals("UI_BUTTON_CLICK", service.setting(SoundService.Trigger.ADD).soundName());
        assertTrue(service.setting(SoundService.Trigger.SUCCESS).enabled());
        assertEquals(1.0f, service.setting(SoundService.Trigger.FAILURE).volume());
    }

    @Test
    void missingTriggerSectionFallsBackToDefaults() {
        service.reload(section("""
                sounds:
                  add:
                    enabled: false
                """).getConfigurationSection("sounds"));
        assertFalse(service.setting(SoundService.Trigger.ADD).enabled());
        assertTrue(service.setting(SoundService.Trigger.SUBTRACT).enabled());
    }

    @Test
    void disabledTriggerKeepsItsConfiguredSound() {
        service.reload(section("""
                sounds:
                  add:
                    enabled: false
                    sound: BLOCK_NOTE_BLOCK_PLING
                    volume: 0.5
                """).getConfigurationSection("sounds"));
        SoundService.Setting add = service.setting(SoundService.Trigger.ADD);
        assertFalse(add.enabled());
        assertEquals("BLOCK_NOTE_BLOCK_PLING", add.soundName());
        assertEquals(0.5f, add.volume());
    }

    @Test
    void blankSoundDisablesTheTrigger() {
        service.reload(section("""
                sounds:
                  success:
                    sound: ""
                """).getConfigurationSection("sounds"));
        assertNull(service.setting(SoundService.Trigger.SUCCESS).soundName());
    }

    @Test
    void missingVolumeDefaultsToOne() {
        service.reload(section("""
                sounds:
                  add:
                    enabled: true
                    sound: BLOCK_NOTE_BLOCK_PLING
                """).getConfigurationSection("sounds"));
        assertEquals(1.0f, service.setting(SoundService.Trigger.ADD).volume());
    }

    private static ConfigurationSection section(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }
}
