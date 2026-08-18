package com.epochmarket.gui;

import com.epochmarket.config.LanguageService;
import com.epochmarket.config.MarketConfigService;
import com.epochmarket.config.SoundService;
import com.epochmarket.integration.ItemMatcher;
import com.epochmarket.model.Market;
import com.epochmarket.model.MarketEntry;
import com.epochmarket.service.SaleService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarketGuiService {
    public static final int SELECTOR_SIZE = 54;
    public static final int CONFIRM_SIZE = 45;
    public static final int SELECTOR_PER_PAGE = 45;
    public static final int PREVIOUS_SLOT = 45;
    public static final int NEXT_SLOT = 53;

    private final MarketConfigService markets;
    private final LanguageService language;
    private final ItemMatcher matcher;
    private final SaleService sales;
    private final SoundService sounds;
    private final Plugin plugin;
    private final Clock clock;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    public MarketGuiService(Plugin plugin, MarketConfigService markets, LanguageService language, ItemMatcher matcher,
                            SaleService sales, SoundService sounds) {
        this(plugin, markets, language, matcher, sales, sounds, Clock.systemDefaultZone());
    }

    public MarketGuiService(Plugin plugin, MarketConfigService markets, LanguageService language, ItemMatcher matcher,
                            SaleService sales, SoundService sounds, Clock clock) {
        this.plugin = plugin;
        this.markets = markets;
        this.language = language;
        this.matcher = matcher;
        this.sales = sales;
        this.sounds = sounds;
        this.clock = clock;
    }

    public void openSelector(Player player) {
        openSelector(player, 0);
    }

    public void openSelector(Player player, int requestedPage) {
        List<Market> visible = visibleMarkets(player);
        int lastPage = Math.max(0, (visible.size() - 1) / SELECTOR_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, lastPage));
        Inventory inventory = Bukkit.createInventory(new SelectorHolder(page), SELECTOR_SIZE,
                language.component("gui.selector-title"));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        if (visible.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, language.component("gui.selector-empty"), List.of()));
        } else {
            int start = page * SELECTOR_PER_PAGE;
            int end = Math.min(visible.size(), start + SELECTOR_PER_PAGE);
            for (int index = start; index < end; index++) {
                Market market = visible.get(index);
                ItemStack icon = matcher.icon(market.selectorIcon());
                inventory.setItem(index - start, icon == null
                        ? unavailableItem()
                        : item(icon, language.component(market.selectorNameKey()),
                        language.components(market.selectorLoreKey(), Map.of())));
            }
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, Component.text("<"), List.of()));
        }
        if (page < lastPage) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, Component.text(">"), List.of()));
        }
        player.openInventory(inventory);
    }

    public void openMarket(Player player, String marketId) {
        Market market = markets.market(marketId);
        if (market == null) {
            message(player, "messages.unknown-market", Map.of("market", marketId));
            return;
        }
        if (!canAccess(player, market)) {
            message(player, "messages.no-permission", Map.of());
            return;
        }
        LocalDate date = today();
        String viewKey = market.viewKey(date);
        Inventory inventory = Bukkit.createInventory(new MarketHolder(market.id(), viewKey), market.rows() * 9,
                language.component(market.titleKey()));
        for (MarketEntry entry : market.entriesAt(date)) {
            if (!matcher.isAvailable(entry) || matcher.icon(entry.icon()) == null) {
                inventory.setItem(entry.slot(), unavailableItem());
                continue;
            }
            inventory.setItem(entry.slot(), loadingItem());
            loadMarketEntry(player, inventory, market, entry, viewKey);
        }
        player.openInventory(inventory);
    }

    public void openConfirm(Player player, String marketId, String entryId, int requestedAmount) {
        Market market = markets.market(marketId);
        LocalDate date = today();
        MarketEntry entry = market == null ? null : market.entry(entryId, date);
        if (market == null || entry == null || !canAccess(player, market)) {
            openSelector(player);
            return;
        }
        if (!matcher.isAvailable(entry) || matcher.icon(entry.icon()) == null) {
            message(player, "messages.unavailable", Map.of());
            openMarket(player, marketId);
            return;
        }
        String viewKey = market.viewKey(date);
        Inventory inventory = Bukkit.createInventory(
                new LoadingConfirmHolder(marketId, entryId, requestedAmount, viewKey), CONFIRM_SIZE,
                language.component("gui.confirm-title"));
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(22, loadingItem());
        player.openInventory(inventory);
        sales.availability(player, market, entry).whenComplete((availability, error) -> runOnMain(() -> {
            if (!isCurrentLoadingConfirm(player, marketId, entryId, requestedAmount, viewKey)) {
                return;
            }
            if (error != null) {
                sounds.play(player, SoundService.Trigger.FAILURE);
                message(player, "messages.storage-failed", Map.of());
                openMarket(player, marketId);
                return;
            }
            if (availability.maximum() <= 0) {
                sounds.play(player, SoundService.Trigger.FAILURE);
                message(player, availability.remaining() <= 0 ? "messages.no-quota" : "messages.no-items", Map.of());
                openMarket(player, marketId);
                return;
            }
            openConfirmView(player, market, entry, requestedAmount, availability);
        }));
    }

    public void openConfirmView(Player player, Market market, MarketEntry entry, int requestedAmount,
                                SaleService.Availability availability) {
        int amount = Math.max(1, Math.min(requestedAmount, availability.maximum()));
        Inventory inventory = Bukkit.createInventory(new ConfirmHolder(market.id(), entry.id(), amount,
                availability.inventoryCount(), availability.remaining(), market.viewKey(today())), CONFIRM_SIZE,
                language.component("gui.confirm-title"));
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(13, confirmItem(entry, amount, availability));
        inventory.setItem(20, control(Material.LIME_DYE, "gui.add-one"));
        inventory.setItem(21, control(Material.LIME_DYE, "gui.add-sixteen"));
        inventory.setItem(22, control(Material.LIME_DYE, "gui.add-sixty-four"));
        inventory.setItem(24, control(Material.RED_DYE, "gui.subtract-one"));
        inventory.setItem(25, control(Material.RED_DYE, "gui.subtract-sixteen"));
        inventory.setItem(26, control(Material.RED_DYE, "gui.subtract-sixty-four"));
        inventory.setItem(29, item(Material.CHEST, language.component("gui.sell-all"),
                language.components("gui.sell-all-lore", Map.of())));
        Map<String, String> values = amountValues(entry, amount, availability);
        inventory.setItem(31, item(Material.EMERALD_BLOCK, language.component("gui.confirm"),
                language.components("gui.confirm-lore", values)));
        inventory.setItem(33, control(Material.BARRIER, "gui.cancel"));
        player.openInventory(inventory);
    }

    public boolean canAccess(Player player, Market market) {
        return player.hasPermission("epochmarket.admin") || market.permission().isBlank() || player.hasPermission(market.permission());
    }

    public List<Market> visibleMarkets(Player player) {
        return markets.markets().stream().filter(market -> canAccess(player, market))
                .sorted(Comparator.comparing(Market::id)).toList();
    }

    public Collection<Market> allMarkets() {
        return markets.markets();
    }

    public Market market(String id) {
        return markets.market(id);
    }

    public MarketEntry entry(String marketId, String entryId) {
        Market market = markets.market(marketId);
        return market == null ? null : market.entry(entryId, today());
    }

    public Collection<MarketEntry> entries(String marketId) {
        Market market = markets.market(marketId);
        return market == null ? List.of() : market.entriesAt(today());
    }

    public String viewKey(String marketId) {
        Market market = markets.market(marketId);
        return market == null ? "" : market.viewKey(today());
    }

    private void loadMarketEntry(Player player, Inventory inventory, Market market, MarketEntry entry, String viewKey) {
        sales.remaining(player.getUniqueId(), market, entry).whenComplete((remaining, error) -> runOnMain(() -> {
            if (!isCurrentMarket(player, market.id(), viewKey, inventory)) {
                return;
            }
            ItemStack display = error == null ? entryItem(entry, remaining) : null;
            inventory.setItem(entry.slot(), display == null ? unavailableItem() : display);
        }));
    }

    private ItemStack entryItem(MarketEntry entry, int remaining) {
        Map<String, String> values = Map.of("unit_price", format(entry.unitPrice()),
                "remaining", String.valueOf(remaining), "limit", String.valueOf(entry.dailyLimit()));
        ItemStack icon = matcher.icon(entry.icon());
        return icon == null ? null
                : item(icon, language.component(entry.nameKey()), language.components("gui.entry-lore", values));
    }

    private ItemStack confirmItem(MarketEntry entry, int amount, SaleService.Availability availability) {
        ItemStack icon = matcher.icon(entry.icon());
        return icon == null ? unavailableItem()
                : item(icon, language.component(entry.nameKey()),
                language.components("gui.confirm-item-lore", amountValues(entry, amount, availability)));
    }

    private Map<String, String> amountValues(MarketEntry entry, int amount, SaleService.Availability availability) {
        BigDecimal total = entry.unitPrice().multiply(BigDecimal.valueOf(amount));
        Map<String, String> values = new HashMap<>();
        values.put("amount", String.valueOf(amount));
        values.put("inventory", String.valueOf(availability.inventoryCount()));
        values.put("remaining", String.valueOf(availability.remaining()));
        values.put("unit_price", format(entry.unitPrice()));
        values.put("total_price", format(total));
        return values;
    }

    private ItemStack unavailableItem() {
        return item(Material.BARRIER, language.component("gui.unavailable"), language.components("gui.unavailable-lore", Map.of()));
    }

    private ItemStack loadingItem() {
        return item(Material.CLOCK, language.component("gui.loading"), List.of());
    }

    private boolean isCurrentMarket(Player player, String marketId, String viewKey, Inventory inventory) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top == inventory && top.getHolder(false) instanceof MarketHolder holder
                && holder.marketId().equals(marketId) && holder.viewKey().equals(viewKey)
                && viewKey.equals(viewKey(marketId));
    }

    private boolean isCurrentLoadingConfirm(Player player, String marketId, String entryId, int requestedAmount,
                                            String viewKey) {
        return player.getOpenInventory().getTopInventory().getHolder(false) instanceof LoadingConfirmHolder holder
                && holder.marketId().equals(marketId)
                && holder.entryId().equals(entryId)
                && holder.requestedAmount() == requestedAmount
                && holder.viewKey().equals(viewKey)
                && viewKey.equals(viewKey(marketId));
    }

    private void runOnMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private ItemStack control(Material material, String key) {
        return item(material, language.component(key), List.of());
    }

    private static void fill(Inventory inventory, Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        return item(new ItemStack(material), name, lore);
    }

    private static ItemStack item(ItemStack base, Component name, List<Component> lore) {
        ItemStack stack = base.clone();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public void message(Player player, String key, Map<String, String> values) {
        player.sendMessage(language.component("prefix").append(language.component(key, values)));
    }

    public String format(BigDecimal value) {
        return moneyFormat.format(value);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
