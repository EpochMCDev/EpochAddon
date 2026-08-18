package com.epochmarket.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RotatingStockTest {

    @Test
    void selectionIsStableWithinACycleAndUsesEachCandidateAtMostOnce() {
        RotatingStock stock = new RotatingStock(3, List.of(0, 1, 2), List.of(
                candidate("a", Material.WHEAT), candidate("b", Material.CARROT),
                candidate("c", Material.POTATO), candidate("d", Material.BEETROOT)
        ));

        List<MarketEntry> first = stock.entries("plants", LocalDate.of(2026, 8, 18));
        List<MarketEntry> sameCycle = stock.entries("plants", LocalDate.of(2026, 8, 19));

        assertEquals(first, sameCycle);
        assertEquals(3, first.stream().map(MarketEntry::itemId).collect(Collectors.toSet()).size());
        assertEquals(Set.of(0, 1, 2), first.stream().map(MarketEntry::slot).collect(Collectors.toSet()));
    }

    @Test
    void entryIdentityChangesWhenTheCycleChanges() {
        RotatingStock stock = new RotatingStock(3, List.of(0), List.of(
                candidate("a", Material.WHEAT), candidate("b", Material.CARROT)
        ));

        MarketEntry before = stock.entries("plants", LocalDate.of(2026, 8, 18)).getFirst();
        MarketEntry after = stock.entries("plants", LocalDate.of(2026, 8, 21)).getFirst();

        assertNotEquals(before.id(), after.id());
    }

    private static RotatingCandidate candidate(String id, Material icon) {
        return new RotatingCandidate(id, ItemSource.VANILLA, icon.name(), icon,
                BigDecimal.ONE, 10, "test." + id);
    }
}
