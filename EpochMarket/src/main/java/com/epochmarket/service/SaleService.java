package com.epochmarket.service;

import com.epochmarket.integration.ItemMatcher;
import com.epochmarket.model.Market;
import com.epochmarket.model.MarketEntry;
import com.epochmarket.storage.QuotaRepository;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Inventory and Vault operations always run on Paper's primary thread. Quota access
 * is dispatched to QuotaRepository's dedicated database thread and resumed here.
 */
public final class SaleService {
    private final Plugin plugin;
    private final QuotaRepository quotas;
    private final ItemMatcher matcher;
    private final InventoryService inventories;
    private final Economy economy;
    private final Clock clock;

    public SaleService(Plugin plugin, QuotaRepository quotas, ItemMatcher matcher, InventoryService inventories,
                       Economy economy, Clock clock) {
        this.plugin = plugin;
        this.quotas = quotas;
        this.matcher = matcher;
        this.inventories = inventories;
        this.economy = economy;
        this.clock = clock;
    }

    public CompletableFuture<Integer> sold(UUID playerId, Market market, MarketEntry entry) {
        return quotas.sold(playerId, market.id(), entry.id(), today());
    }

    public CompletableFuture<Integer> remaining(UUID playerId, Market market, MarketEntry entry) {
        return sold(playerId, market, entry).thenApply(sold -> Math.max(0, entry.dailyLimit() - sold));
    }

    /** The inventory count is read on the caller's main-thread context before issuing one async quota query. */
    public CompletableFuture<Integer> availableToSell(Player player, Market market, MarketEntry entry) {
        int inventory = inventories.count(player, entry);
        return remaining(player.getUniqueId(), market, entry).thenApply(remaining -> Math.min(inventory, remaining));
    }

    public CompletableFuture<Availability> availability(Player player, Market market, MarketEntry entry) {
        int inventory = inventories.count(player, entry);
        return remaining(player.getUniqueId(), market, entry).thenApply(remaining -> new Availability(inventory, remaining));
    }

    /**
     * Completes on Paper's main thread. The callback is never invoked from the SQLite executor.
     */
    public void sell(Player player, Market market, MarketEntry entry, int amount, Consumer<SaleResult> callback) {
        if (!matcher.isAvailable(entry)) {
            callback.accept(SaleResult.failure(SaleResult.Status.UNAVAILABLE));
            return;
        }
        if (amount <= 0) {
            callback.accept(SaleResult.failure(SaleResult.Status.INVALID_AMOUNT));
            return;
        }
        int inventoryCount = inventories.count(player, entry);
        if (inventoryCount == 0) {
            callback.accept(SaleResult.failure(SaleResult.Status.NO_ITEMS));
            return;
        }
        if (amount > inventoryCount) {
            callback.accept(SaleResult.failure(SaleResult.Status.CHANGED));
            return;
        }

        LocalDate date = today();
        quotas.reserve(player.getUniqueId(), market.id(), entry.id(), date, amount, entry.dailyLimit())
                .whenComplete((reserved, error) -> runOnPrimaryThread(() -> {
                    if (error != null) {
                        callback.accept(SaleResult.failure(SaleResult.Status.STORAGE_FAILURE));
                        return;
                    }
                    if (!Boolean.TRUE.equals(reserved)) {
                        callback.accept(SaleResult.failure(SaleResult.Status.NO_QUOTA));
                        return;
                    }
                    completeReservedSale(player, market, entry, amount, date, callback);
                }));
    }

    private void completeReservedSale(Player player, Market market, MarketEntry entry, int amount, LocalDate date,
                                      Consumer<SaleResult> callback) {
        if (!player.isOnline() || !matcher.isAvailable(entry) || inventories.count(player, entry) < amount) {
            releaseQuietly(player.getUniqueId(), market, entry, date, amount);
            callback.accept(SaleResult.failure(SaleResult.Status.CHANGED));
            return;
        }

        List<InventoryService.RemovedStack> removed = inventories.remove(player, entry, amount);
        if (removed.isEmpty()) {
            releaseQuietly(player.getUniqueId(), market, entry, date, amount);
            callback.accept(SaleResult.failure(SaleResult.Status.CHANGED));
            return;
        }

        BigDecimal total = entry.unitPrice().multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        EconomyResponse response;
        try {
            response = economy.depositPlayer(player, total.doubleValue());
        } catch (RuntimeException exception) {
            inventories.restore(player, removed);
            releaseQuietly(player.getUniqueId(), market, entry, date, amount);
            callback.accept(SaleResult.failure(SaleResult.Status.PAYOUT_FAILURE));
            return;
        }
        if (!response.transactionSuccess()) {
            inventories.restore(player, removed);
            releaseQuietly(player.getUniqueId(), market, entry, date, amount);
            callback.accept(SaleResult.failure(SaleResult.Status.PAYOUT_FAILURE));
            return;
        }
        callback.accept(new SaleResult(SaleResult.Status.SUCCESS, amount, total));
    }

    private void releaseQuietly(UUID playerId, Market market, MarketEntry entry, LocalDate date, int amount) {
        quotas.release(playerId, market.id(), entry.id(), date, amount);
    }

    private void runOnPrimaryThread(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    public record Availability(int inventoryCount, int remaining) {
        public int maximum() {
            return Math.min(inventoryCount, remaining);
        }
    }
}
