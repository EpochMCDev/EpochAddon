package com.epochmarket.command;

import com.epochmarket.config.LanguageService;
import com.epochmarket.gui.MarketGuiService;
import com.epochmarket.model.Market;
import com.epochmarket.model.MarketEntry;
import com.epochmarket.service.SaleService;
import com.epochmarket.storage.QuotaRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class EpochMarketCommand implements CommandExecutor, TabCompleter {
    private final MarketGuiService gui;
    private final QuotaRepository quotas;
    private final SaleService sales;
    private final LanguageService language;
    private final Clock clock;
    private final BooleanSupplier reload;
    private final Plugin plugin;

    public EpochMarketCommand(Plugin plugin, MarketGuiService gui, QuotaRepository quotas, SaleService sales,
                              LanguageService language, Clock clock, BooleanSupplier reload) {
        this.plugin = plugin;
        this.gui = gui;
        this.quotas = quotas;
        this.sales = sales;
        this.language = language;
        this.clock = clock;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            return openSelector(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> open(sender, args);
            case "reload" -> reload(sender);
            case "quota" -> quota(sender, args);
            case "reset" -> reset(sender, args);
            default -> false;
        };
    }

    private boolean openSelector(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(language.component("prefix").append(language.component("messages.player-only")));
            return true;
        }
        if (!player.hasPermission("epochmarket.use") && !player.hasPermission("epochmarket.admin")) {
            player.sendMessage(language.component("prefix").append(language.component("messages.no-permission")));
            return true;
        }
        gui.openSelector(player);
        return true;
    }

    private boolean open(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(language.component("prefix").append(language.component("messages.player-only")));
            return true;
        }
        if (!player.hasPermission("epochmarket.use") && !player.hasPermission("epochmarket.admin")) {
            player.sendMessage(language.component("prefix").append(language.component("messages.no-permission")));
            return true;
        }
        gui.openMarket(player, args[1]);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!admin(sender)) {
            return true;
        }
        sender.sendMessage(language.component("prefix").append(language.component(
                reload.getAsBoolean() ? "messages.reloaded" : "messages.reload-failed")));
        return true;
    }

    private boolean quota(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 4) {
            return false;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Market market = gui.market(args[2]);
        MarketEntry entry = gui.entry(args[2], args[3]);
        if (market == null) {
            sender.sendMessage(language.component("prefix").append(language.component("messages.unknown-market", Map.of("market", args[2]))));
            return true;
        }
        if (entry == null) {
            sender.sendMessage(language.component("prefix").append(language.component("messages.unknown-entry",
                    Map.of("market", args[2], "entry", args[3]))));
            return true;
        }
        sales.sold(target.getUniqueId(), market, entry).whenComplete((sold, error) -> runOnMain(() -> {
            if (error != null) {
                sender.sendMessage(language.component("prefix").append(language.component("messages.storage-failed")));
                return;
            }
            int remaining = Math.max(0, entry.dailyLimit() - sold);
            sender.sendMessage(language.component("prefix").append(language.component("messages.quota", Map.of(
                    "player", playerName(target, args[1]), "market", market.id(), "entry", entry.id(),
                    "sold", String.valueOf(sold), "remaining", String.valueOf(remaining)
            ))));
        }));
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 4) {
            return false;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Market market = gui.market(args[2]);
        MarketEntry entry = gui.entry(args[2], args[3]);
        if (market == null || entry == null) {
            sender.sendMessage(language.component("prefix").append(language.component(market == null
                    ? "messages.unknown-market" : "messages.unknown-entry", market == null
                    ? Map.of("market", args[2]) : Map.of("market", args[2], "entry", args[3]))));
            return true;
        }
        quotas.reset(target.getUniqueId(), market.id(), entry.id(), LocalDate.now(clock)).whenComplete((unused, error) -> runOnMain(() -> {
            if (error != null) {
                sender.sendMessage(language.component("prefix").append(language.component("messages.storage-failed")));
                return;
            }
            sender.sendMessage(language.component("prefix").append(language.component("messages.quota-reset", Map.of(
                    "player", playerName(target, args[1]), "market", market.id(), "entry", entry.id()
            ))));
        }));
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("epochmarket.admin")) {
            return true;
        }
        sender.sendMessage(language.component("prefix").append(language.component("messages.no-permission")));
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return match(args[0], sender.hasPermission("epochmarket.admin")
                    ? List.of("open", "reload", "quota", "reset") : List.of("open"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            List<String> marketIds = sender instanceof Player player
                    ? gui.visibleMarkets(player).stream().map(Market::id).toList()
                    : gui.allMarkets().stream().map(Market::id).toList();
            return match(args[1], marketIds);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("quota") || args[0].equalsIgnoreCase("reset"))) {
            return match(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("quota") || args[0].equalsIgnoreCase("reset"))) {
            return match(args[2], gui.allMarkets().stream().map(Market::id).toList());
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("quota") || args[0].equalsIgnoreCase("reset"))) {
            Market market = gui.market(args[2]);
            return market == null ? List.of() : match(args[3], gui.entries(market.id()).stream().map(MarketEntry::id).toList());
        }
        return List.of();
    }

    private static List<String> match(String prefix, Collection<String> candidates) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static String playerName(OfflinePlayer player, String fallback) {
        return player.getName() == null ? fallback : player.getName();
    }

    private void runOnMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
