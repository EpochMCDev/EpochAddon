package com.epochmarket.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies a confirmation page awaiting its async quota lookup. */
public record LoadingConfirmHolder(String marketId, String entryId, int requestedAmount, String viewKey)
        implements InventoryHolder {
    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("Loading confirmation holders do not own an inventory");
    }
}
