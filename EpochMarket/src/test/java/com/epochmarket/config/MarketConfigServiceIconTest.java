package com.epochmarket.config;

import com.epochmarket.model.ItemIcon;
import com.epochmarket.model.Market;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketConfigServiceIconTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsVanillaIconsAndParsesCraftEngineShorthand() throws Exception {
        writeConfig("""
                title-key: test.title
                rows: 1
                selector:
                  icon: WHEAT
                  name-key: test.name
                  lore-key: test.lore
                entries:
                  vanilla:
                    source: VANILLA
                    item-id: WHEAT
                    icon: WHEAT
                    slot: 0
                    unit-price: 1
                    daily-limit: 10
                    name-key: test.vanilla
                  custom:
                    source: CRAFT_ENGINE
                    item-id: epoch:seasonal_crop
                    icon: epoch:seasonal_crop
                    slot: 1
                    unit-price: 1
                    daily-limit: 10
                    name-key: test.custom
                """);

        Market market = load().market("test");

        assertEquals(ItemIcon.vanilla(org.bukkit.Material.WHEAT), market.selectorIcon());
        assertEquals(ItemIcon.vanilla(org.bukkit.Material.WHEAT), market.entry("vanilla").icon());
        assertEquals(ItemIcon.craftEngine("epoch:seasonal_crop"), market.entry("custom").icon());
    }

    @Test
    void parsesExplicitCraftEngineIconDefinition() throws Exception {
        writeConfig("""
                title-key: test.title
                rows: 1
                selector:
                  icon:
                    source: CRAFT_ENGINE
                    item-id: epoch:market_icon
                  name-key: test.name
                  lore-key: test.lore
                entries:
                  custom:
                    source: CRAFT_ENGINE
                    item-id: epoch:seasonal_crop
                    icon:
                      source: CRAFT_ENGINE
                      item-id: epoch:market_icon
                    slot: 0
                    unit-price: 1
                    daily-limit: 10
                    name-key: test.custom
                """);

        Market market = load().market("test");

        assertEquals(ItemIcon.craftEngine("epoch:market_icon"), market.selectorIcon());
        assertEquals(ItemIcon.craftEngine("epoch:market_icon"), market.entry("custom").icon());
    }

    private void writeConfig(String content) throws Exception {
        Files.writeString(temporaryDirectory.resolve("test.yml"), content);
    }

    private MarketConfigService load() throws Exception {
        MarketConfigService service = new MarketConfigService(temporaryDirectory.toFile(),
                Logger.getLogger("market-config-test"));
        service.reload();
        return service;
    }
}
