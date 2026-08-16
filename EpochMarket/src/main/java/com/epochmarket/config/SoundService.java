package com.epochmarket.config;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class SoundService {
    private static final Map<Trigger, Setting> DEFAULTS = Map.of(
            Trigger.ADD, new Setting(true, "UI_BUTTON_CLICK", 1.0f),
            Trigger.SUBTRACT, new Setting(true, "UI_BUTTON_CLICK", 1.0f),
            Trigger.SUCCESS, new Setting(true, "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f),
            Trigger.FAILURE, new Setting(true, "ENTITY_VILLAGER_NO", 1.0f)
    );

    private final Logger logger;
    private final Set<Trigger> invalidWarned = new HashSet<>();
    private Map<Trigger, Setting> settings = new EnumMap<>(DEFAULTS);

    public SoundService(Logger logger) {
        this.logger = logger;
    }

    public void reload(ConfigurationSection soundsSection) {
        Map<Trigger, Setting> parsed = new EnumMap<>(DEFAULTS);
        invalidWarned.clear();
        for (Trigger trigger : Trigger.values()) {
            ConfigurationSection section = soundsSection == null
                    ? null : soundsSection.getConfigurationSection(trigger.name().toLowerCase(Locale.ROOT));
            if (section == null) {
                continue;
            }
            boolean enabled = section.getBoolean("enabled", DEFAULTS.get(trigger).enabled());
            String soundName = section.getString("sound", "");
            soundName = soundName.isBlank() ? null : soundName.trim().toUpperCase(Locale.ROOT);
            float volume = (float) section.getDouble("volume", DEFAULTS.get(trigger).volume());
            parsed.put(trigger, new Setting(enabled, soundName, volume));
        }
        settings = parsed;
    }

    public void play(Player player, Trigger trigger) {
        Setting setting = settings.getOrDefault(trigger, DEFAULTS.get(trigger));
        if (!setting.enabled() || setting.soundName() == null) {
            return;
        }
        Sound sound;
        try {
            sound = Sound.valueOf(setting.soundName());
        } catch (IllegalArgumentException | LinkageError exception) {
            warnInvalid(trigger, setting.soundName());
            return;
        }
        player.playSound(player.getLocation(), sound, setting.volume(), 1.0f);
    }

    private void warnInvalid(Trigger trigger, String soundName) {
        if (invalidWarned.add(trigger)) {
            logger.warning("Invalid sound '" + soundName + "' for trigger " + trigger + "; sound will not play.");
        }
    }

    // Package-private for tests.
    Setting setting(Trigger trigger) {
        return settings.getOrDefault(trigger, DEFAULTS.get(trigger));
    }

    public enum Trigger {
        ADD, SUBTRACT, SUCCESS, FAILURE
    }

    record Setting(boolean enabled, String soundName, float volume) {
    }
}
