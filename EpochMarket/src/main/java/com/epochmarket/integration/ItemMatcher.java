package com.epochmarket.integration;

import com.epochmarket.model.MarketEntry;
import org.bukkit.inventory.ItemStack;

public interface ItemMatcher {
    /** Returns whether the configured source and item ID can currently be resolved. */
    boolean isAvailable(MarketEntry entry);

    /** Returns whether the supplied stack exactly represents the configured entry. */
    boolean matches(MarketEntry entry, ItemStack stack);
}

