package com.epochmarket.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class LanguageService {
    private final File languageDirectory;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration language = new YamlConfiguration();

    public LanguageService(File languageDirectory, Logger logger) {
        this.languageDirectory = languageDirectory;
        this.logger = logger;
    }

    public void reload(String languageId) {
        File file = new File(languageDirectory, languageId + ".yml");
        if (!file.isFile()) {
            logger.warning("Language file '" + languageId + ".yml' was not found; falling back to zh_CN.yml.");
            file = new File(languageDirectory, "zh_CN.yml");
        }
        language = YamlConfiguration.loadConfiguration(file);
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, Map<String, String> values) {
        String template = language.getString(key);
        if (template == null) {
            logger.warning("Missing language key: " + key);
            template = "<red>Missing language key: " + key;
        }
        return miniMessage.deserialize(replace(template, values));
    }

    public List<Component> components(String key, Map<String, String> values) {
        List<String> templates = language.getStringList(key);
        List<Component> result = new ArrayList<>(templates.size());
        for (String template : templates) {
            result.add(miniMessage.deserialize(replace(template, values)));
        }
        return result;
    }

    public String plain(String key, Map<String, String> values) {
        String template = language.getString(key, key);
        return replace(template, values);
    }

    private String replace(String template, Map<String, String> values) {
        String value = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            value = value.replace("<" + entry.getKey() + ">", miniMessage.escapeTags(entry.getValue()));
        }
        return value;
    }
}

