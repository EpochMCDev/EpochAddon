package com.epochmarket.model;

import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.Objects;

public record MarketEntry(
        String id,
        ItemSource source,
        String itemId,
        Material icon,
        int slot,
        BigDecimal unitPrice,
        int dailyLimit,
        String nameKey
) {
    public MarketEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(nameKey, "nameKey");
        if (slot < 0) {
            throw new IllegalArgumentException("slot cannot be negative");
        }
        if (dailyLimit < 0) {
            throw new IllegalArgumentException("daily limit cannot be negative");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unit price cannot be negative");
        }
    }
}

