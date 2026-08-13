package com.epochmarket.model;

import org.bukkit.Material;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Market {
    private final String id;
    private final String titleKey;
    private final int rows;
    private final String permission;
    private final Material selectorIcon;
    private final String selectorNameKey;
    private final String selectorLoreKey;
    private final Map<String, MarketEntry> entries;

    public Market(String id, String titleKey, int rows, String permission, Material selectorIcon,
                  String selectorNameKey, String selectorLoreKey, Collection<MarketEntry> entries) {
        this.id = Objects.requireNonNull(id, "id");
        this.titleKey = Objects.requireNonNull(titleKey, "titleKey");
        this.rows = rows;
        this.permission = permission == null ? "" : permission;
        this.selectorIcon = Objects.requireNonNull(selectorIcon, "selectorIcon");
        this.selectorNameKey = Objects.requireNonNull(selectorNameKey, "selectorNameKey");
        this.selectorLoreKey = Objects.requireNonNull(selectorLoreKey, "selectorLoreKey");
        this.entries = new LinkedHashMap<>();
        for (MarketEntry entry : entries) {
            if (this.entries.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalArgumentException("Duplicate entry ID: " + entry.id());
            }
        }
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public int rows() {
        return rows;
    }

    public String permission() {
        return permission;
    }

    public Material selectorIcon() {
        return selectorIcon;
    }

    public String selectorNameKey() {
        return selectorNameKey;
    }

    public String selectorLoreKey() {
        return selectorLoreKey;
    }

    public Collection<MarketEntry> entries() {
        return entries.values();
    }

    public MarketEntry entry(String entryId) {
        return entries.get(entryId);
    }
}

