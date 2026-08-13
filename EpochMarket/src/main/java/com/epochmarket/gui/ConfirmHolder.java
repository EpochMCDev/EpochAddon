package com.epochmarket.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public record ConfirmHolder(String marketId, String entryId, int amount, int inventoryCount, int remaining) implements InventoryHolder {
    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("Confirmation holders do not own an inventory");
    }
}
