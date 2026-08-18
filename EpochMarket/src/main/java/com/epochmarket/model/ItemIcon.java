package com.epochmarket.model;

import org.bukkit.Material;

import java.util.Objects;

/** A GUI icon backed by either a vanilla material or a CraftEngine item. */
public record ItemIcon(ItemSource source, String itemId) {
    public ItemIcon {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(itemId, "itemId");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("icon item ID cannot be blank");
        }
        if (source != ItemSource.VANILLA && source != ItemSource.CRAFT_ENGINE) {
            throw new IllegalArgumentException("unsupported icon source: " + source);
        }
        if (source == ItemSource.VANILLA) {
            Material material = Material.matchMaterial(itemId);
            if (material == null || isAir(material)) {
                throw new IllegalArgumentException("invalid item material '" + itemId + "'");
            }
        }
    }

    public static ItemIcon vanilla(Material material) {
        Objects.requireNonNull(material, "material");
        if (isAir(material)) {
            throw new IllegalArgumentException("icon material must be an item");
        }
        return new ItemIcon(ItemSource.VANILLA, material.name());
    }

    public static ItemIcon craftEngine(String itemId) {
        return new ItemIcon(ItemSource.CRAFT_ENGINE, itemId);
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}
