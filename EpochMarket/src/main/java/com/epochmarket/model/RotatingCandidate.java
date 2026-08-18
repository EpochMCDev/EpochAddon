package com.epochmarket.model;

import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.Objects;

/** A configured item that may be selected for a rotating market slot. */
public record RotatingCandidate(
        String id,
        ItemSource source,
        String itemId,
        ItemIcon icon,
        BigDecimal unitPrice,
        int dailyLimit,
        String nameKey
) {
    public RotatingCandidate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(nameKey, "nameKey");
        if (id.isBlank()) {
            throw new IllegalArgumentException("candidate ID cannot be blank");
        }
        if (dailyLimit < 0) {
            throw new IllegalArgumentException("daily limit cannot be negative");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unit price cannot be negative");
        }
    }

    public MarketEntry toEntry(int slot, String cycleKey) {
        return new MarketEntry("rotation." + slot + "." + cycleKey + "." + id,
                source, itemId, icon, slot, unitPrice, dailyLimit, nameKey);
    }

    public RotatingCandidate(String id, ItemSource source, String itemId, Material icon,
                             BigDecimal unitPrice, int dailyLimit, String nameKey) {
        this(id, source, itemId, ItemIcon.vanilla(icon), unitPrice, dailyLimit, nameKey);
    }
}
