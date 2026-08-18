package com.epochmarket.config;

import com.epochmarket.model.ItemSource;
import com.epochmarket.model.ItemIcon;
import com.epochmarket.model.Market;
import com.epochmarket.model.MarketEntry;
import com.epochmarket.model.RotatingCandidate;
import com.epochmarket.model.RotatingStock;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class MarketConfigService {
    private final File marketsDirectory;
    private final Logger logger;
    private Map<String, Market> markets = Map.of();

    public MarketConfigService(File marketsDirectory, Logger logger) {
        this.marketsDirectory = marketsDirectory;
        this.logger = logger;
    }

    public void reload() throws IOException {
        Files.createDirectories(marketsDirectory.toPath());
        Map<String, Market> loaded = new LinkedHashMap<>();
        try (var files = Files.list(marketsDirectory.toPath())) {
            List<Path> configs = files
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path path : configs) {
                try {
                    Market market = loadMarket(path.toFile());
                    if (loaded.putIfAbsent(market.id(), market) != null) {
                        throw new IllegalArgumentException("Duplicate market ID: " + market.id());
                    }
                } catch (Exception exception) {
                    logger.severe("Skipped invalid market config '" + path.getFileName() + "': " + exception.getMessage());
                }
            }
        }
        markets = Map.copyOf(loaded);
        logger.info("Loaded " + markets.size() + " EpochMarket market(s).");
    }

    public Market market(String id) {
        return markets.get(id);
    }

    public Collection<Market> markets() {
        return markets.values();
    }

    private Market loadMarket(File file) {
        String fileName = file.getName();
        String id = fileName.substring(0, fileName.length() - ".yml".length());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String titleKey = required(config, "title-key", fileName);
        int rows = config.getInt("rows", 3);
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6");
        }

        ConfigurationSection selector = config.getConfigurationSection("selector");
        if (selector == null) {
            throw new IllegalArgumentException("missing selector section");
        }
        ItemIcon selectorIcon = icon(selector, "icon", null, fileName);
        String selectorNameKey = required(selector, "name-key", fileName);
        String selectorLoreKey = required(selector, "lore-key", fileName);
        ConfigurationSection entries = config.getConfigurationSection("entries");
        if (entries == null) {
            throw new IllegalArgumentException("missing entries section");
        }

        List<MarketEntry> parsedEntries = new ArrayList<>();
        boolean[] occupiedSlots = new boolean[rows * 9];
        for (String entryId : entries.getKeys(false)) {
            ConfigurationSection entry = entries.getConfigurationSection(entryId);
            if (entry == null) {
                logger.warning("Skipped non-section entry '" + entryId + "' in " + fileName);
                continue;
            }
            try {
                int slot = entry.getInt("slot", -1);
                if (slot < 0 || slot >= rows * 9) {
                    throw new IllegalArgumentException("slot must be inside the market inventory");
                }
                if (occupiedSlots[slot]) {
                    throw new IllegalArgumentException("slot " + slot + " is already occupied");
                }
                BigDecimal price = new BigDecimal(required(entry, "unit-price", fileName));
                int limit = entry.getInt("daily-limit", -1);
                ItemSource source = ItemSource.parse(required(entry, "source", fileName));
                String itemId = required(entry, "item-id", fileName);
                MarketEntry parsed = new MarketEntry(
                        entryId,
                        source,
                        itemId,
                        icon(entry, "icon", source, fileName),
                        slot,
                        price,
                        limit,
                        required(entry, "name-key", fileName)
                );
                occupiedSlots[slot] = true;
                parsedEntries.add(parsed);
            } catch (Exception exception) {
                logger.severe("Skipped invalid entry '" + entryId + "' in " + fileName + ": " + exception.getMessage());
            }
        }
        RotatingStock rotatingStock = parseRotatingStock(config.getConfigurationSection("rotation"),
                occupiedSlots, rows, fileName);
        return new Market(id, titleKey, rows, config.getString("permission", ""), selectorIcon,
                selectorNameKey, selectorLoreKey, parsedEntries, rotatingStock);
    }

    private RotatingStock parseRotatingStock(ConfigurationSection rotation, boolean[] occupiedSlots, int rows,
                                             String fileName) {
        if (rotation == null) {
            return null;
        }
        int cycleDays = rotation.getInt("cycle-days", -1);
        if (cycleDays < 1) {
            throw new IllegalArgumentException("rotation.cycle-days must be positive");
        }

        List<?> configuredSlots = rotation.getList("slots");
        if (configuredSlots == null || configuredSlots.isEmpty()) {
            throw new IllegalArgumentException("rotation.slots must contain at least one slot");
        }
        List<Integer> slots = new ArrayList<>();
        for (Object value : configuredSlots) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("rotation.slots must contain only numbers");
            }
            int slot = number.intValue();
            if (slot < 0 || slot >= rows * 9) {
                throw new IllegalArgumentException("rotation slot must be inside the market inventory");
            }
            if (occupiedSlots[slot]) {
                throw new IllegalArgumentException("slot " + slot + " is already occupied");
            }
            occupiedSlots[slot] = true;
            if (slots.contains(slot)) {
                throw new IllegalArgumentException("rotation slot " + slot + " is duplicated");
            }
            slots.add(slot);
        }

        ConfigurationSection candidatesSection = rotation.getConfigurationSection("candidates");
        if (candidatesSection == null) {
            throw new IllegalArgumentException("missing rotation.candidates section");
        }
        List<RotatingCandidate> candidates = new ArrayList<>();
        for (String candidateId : candidatesSection.getKeys(false)) {
            ConfigurationSection candidate = candidatesSection.getConfigurationSection(candidateId);
            if (candidate == null) {
                logger.warning("Skipped non-section rotation candidate '" + candidateId + "' in " + fileName);
                continue;
            }
            try {
                ItemSource source = ItemSource.parse(required(candidate, "source", fileName));
                String itemId = required(candidate, "item-id", fileName);
                candidates.add(new RotatingCandidate(
                        candidateId,
                        source,
                        itemId,
                        icon(candidate, "icon", source, fileName),
                        new BigDecimal(required(candidate, "unit-price", fileName)),
                        candidate.getInt("daily-limit", -1),
                        required(candidate, "name-key", fileName)
                ));
            } catch (Exception exception) {
                logger.severe("Skipped invalid rotation candidate '" + candidateId + "' in " + fileName
                        + ": " + exception.getMessage());
            }
        }
        return new RotatingStock(cycleDays, slots, candidates);
    }

    private static String required(ConfigurationSection section, String path, String source) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing '" + path + "' in " + source);
        }
        return value;
    }

    private static Material material(String input) {
        Material material = Material.matchMaterial(input);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("invalid item material '" + input + "'");
        }
        return material;
    }

    private static ItemIcon icon(ConfigurationSection section, String path, ItemSource defaultSource, String fileName) {
        ConfigurationSection definition = section.getConfigurationSection(path);
        if (definition != null) {
            ItemSource source = ItemSource.parse(required(definition, "source", fileName));
            String itemId = required(definition, "item-id", fileName);
            return switch (source) {
                case VANILLA -> ItemIcon.vanilla(material(itemId));
                case CRAFT_ENGINE -> ItemIcon.craftEngine(itemId);
                case SLIMEFUN -> throw new IllegalArgumentException("icon source SLIMEFUN is not supported");
            };
        }

        Object raw = section.get(path);
        if (!(raw instanceof String input) || input.isBlank()) {
            throw new IllegalArgumentException("missing '" + path + "' in " + fileName);
        }
        Material material = Material.matchMaterial(input);
        if (material != null && material.isItem()) {
            return ItemIcon.vanilla(material);
        }
        if (defaultSource == ItemSource.CRAFT_ENGINE) {
            return ItemIcon.craftEngine(input);
        }
        throw new IllegalArgumentException("invalid item material '" + input + "'");
    }
}
