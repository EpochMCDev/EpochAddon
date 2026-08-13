package com.epochmarket.service;

import com.epochmarket.integration.ItemMatcher;
import com.epochmarket.model.MarketEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/** Only slots 0-35 are considered: storage and hotbar, never offhand or armor. */
public final class InventoryService {
    private static final int MARKET_INVENTORY_SIZE = 36;
    private final ItemMatcher matcher;

    public InventoryService(ItemMatcher matcher) {
        this.matcher = matcher;
    }

    public int count(Player player, MarketEntry entry) {
        int found = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < MARKET_INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matcher.matches(entry, stack)) {
                found = Math.addExact(found, stack.getAmount());
            }
        }
        return found;
    }

    /**
     * Removes an exact amount from slots 0-35 and returns a snapshot needed to restore it.
     * It returns an empty list when the requested amount cannot be fulfilled.
     */
    public List<RemovedStack> remove(Player player, MarketEntry entry, int amount) {
        if (amount <= 0 || count(player, entry) < amount) {
            return List.of();
        }
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        List<RemovedStack> removed = new ArrayList<>();
        for (int slot = 0; slot < MARKET_INVENTORY_SIZE && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matcher.matches(entry, stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            removed.add(new RemovedStack(slot, stack.clone(), take));
            if (take == stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                inventory.setItem(slot, stack);
            }
            remaining -= take;
        }
        player.updateInventory();
        return remaining == 0 ? List.copyOf(removed) : List.of();
    }

    public void restore(Player player, List<RemovedStack> removed) {
        PlayerInventory inventory = player.getInventory();
        for (RemovedStack entry : removed) {
            ItemStack current = inventory.getItem(entry.slot());
            if (current == null || current.getType().isAir()) {
                ItemStack restored = entry.original().clone();
                restored.setAmount(entry.amount());
                inventory.setItem(entry.slot(), restored);
            } else if (current.isSimilar(entry.original()) && current.getAmount() + entry.amount() <= current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + entry.amount());
                inventory.setItem(entry.slot(), current);
            } else {
                // The action runs synchronously after removal, so this is only a defensive fallback.
                ItemStack restored = entry.original().clone();
                restored.setAmount(entry.amount());
                player.getWorld().dropItemNaturally(player.getLocation(), restored);
            }
        }
        player.updateInventory();
    }

    public record RemovedStack(int slot, ItemStack original, int amount) {
    }
}

