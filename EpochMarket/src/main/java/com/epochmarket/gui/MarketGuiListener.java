package com.epochmarket.gui;

import com.epochmarket.config.SoundService;
import com.epochmarket.model.Market;
import com.epochmarket.model.MarketEntry;
import com.epochmarket.service.SaleResult;
import com.epochmarket.service.SaleService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketGuiListener implements Listener {
    private final MarketGuiService gui;
    private final SaleService sales;
    private final SoundService sounds;
    private final Set<UUID> selling = ConcurrentHashMap.newKeySet();

    public MarketGuiListener(MarketGuiService gui, SaleService sales, SoundService sounds) {
        this.gui = gui;
        this.sales = sales;
        this.sounds = sounds;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof SelectorHolder || holder instanceof MarketHolder || holder instanceof ConfirmHolder
                || holder instanceof LoadingConfirmHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }
        if (holder instanceof SelectorHolder selector) {
            handleSelector(player, selector, event.getRawSlot());
        } else if (holder instanceof MarketHolder market) {
            handleMarket(player, market, event.getRawSlot());
        } else if (holder instanceof ConfirmHolder confirm) {
            handleConfirm(player, confirm, event.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof SelectorHolder || holder instanceof MarketHolder || holder instanceof ConfirmHolder
                || holder instanceof LoadingConfirmHolder) {
            event.setCancelled(true);
        }
    }

    private void handleSelector(Player player, SelectorHolder holder, int slot) {
        if (slot == MarketGuiService.PREVIOUS_SLOT) {
            gui.openSelector(player, holder.page() - 1);
            return;
        }
        if (slot == MarketGuiService.NEXT_SLOT) {
            gui.openSelector(player, holder.page() + 1);
            return;
        }
        if (slot >= MarketGuiService.SELECTOR_PER_PAGE) {
            return;
        }
        var visible = gui.visibleMarkets(player);
        int index = holder.page() * MarketGuiService.SELECTOR_PER_PAGE + slot;
        if (index < visible.size()) {
            gui.openMarket(player, visible.get(index).id());
        }
    }

    private void handleMarket(Player player, MarketHolder holder, int slot) {
        Market market = gui.market(holder.marketId());
        if (market == null || !gui.canAccess(player, market)) {
            gui.openSelector(player);
            return;
        }
        for (MarketEntry entry : market.entries()) {
            if (entry.slot() == slot) {
                gui.openConfirm(player, market.id(), entry.id(), 1);
                return;
            }
        }
    }

    private void handleConfirm(Player player, ConfirmHolder holder, int slot) {
        Market market = gui.market(holder.marketId());
        MarketEntry entry = gui.entry(holder.marketId(), holder.entryId());
        if (market == null || entry == null || !gui.canAccess(player, market)) {
            gui.openSelector(player);
            return;
        }
        int amount = holder.amount();
        switch (slot) {
            case 20 -> {
                sounds.play(player, SoundService.Trigger.ADD);
                gui.openConfirmView(player, market, entry, amount + 1, availability(holder));
            }
            case 21 -> {
                sounds.play(player, SoundService.Trigger.ADD);
                gui.openConfirmView(player, market, entry, amount + 16, availability(holder));
            }
            case 22 -> {
                sounds.play(player, SoundService.Trigger.ADD);
                gui.openConfirmView(player, market, entry, amount + 64, availability(holder));
            }
            case 24 -> {
                sounds.play(player, SoundService.Trigger.SUBTRACT);
                gui.openConfirmView(player, market, entry, amount - 1, availability(holder));
            }
            case 25 -> {
                sounds.play(player, SoundService.Trigger.SUBTRACT);
                gui.openConfirmView(player, market, entry, amount - 16, availability(holder));
            }
            case 26 -> {
                sounds.play(player, SoundService.Trigger.SUBTRACT);
                gui.openConfirmView(player, market, entry, amount - 64, availability(holder));
            }
            case 29 -> gui.openConfirmView(player, market, entry, availability(holder).maximum(), availability(holder));
            case 31 -> sell(player, holder, market, entry);
            case 33 -> gui.openMarket(player, holder.marketId());
            default -> {
            }
        }
    }

    private void sell(Player player, ConfirmHolder holder, Market market, MarketEntry entry) {
        if (!selling.add(player.getUniqueId())) {
            return;
        }
        sales.sell(player, market, entry, holder.amount(), result -> {
            switch (result.status()) {
                case SUCCESS -> {
                    sounds.play(player, SoundService.Trigger.SUCCESS);
                    gui.message(player, "messages.sold", Map.of(
                            "amount", String.valueOf(result.amount()),
                            "item", entry.itemId(),
                            "money", gui.format(result.money())
                    ));
                    gui.openMarket(player, holder.marketId());
                }
                case UNAVAILABLE, NO_ITEMS, NO_QUOTA, STORAGE_FAILURE, PAYOUT_FAILURE, INVALID_AMOUNT, CHANGED -> {
                    sounds.play(player, SoundService.Trigger.FAILURE);
                    String key = switch (result.status()) {
                        case UNAVAILABLE -> "messages.unavailable";
                        case NO_ITEMS -> "messages.no-items";
                        case NO_QUOTA -> "messages.no-quota";
                        case STORAGE_FAILURE -> "messages.storage-failed";
                        case PAYOUT_FAILURE -> "messages.payout-failed";
                        default -> "messages.changed";
                    };
                    gui.message(player, key, Map.of());
                }
            }
            selling.remove(player.getUniqueId());
        });
    }

    private static SaleService.Availability availability(ConfirmHolder holder) {
        return new SaleService.Availability(holder.inventoryCount(), holder.remaining());
    }
}
