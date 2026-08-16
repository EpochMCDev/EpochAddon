package com.epochmarket;

import com.epochmarket.command.EpochMarketCommand;
import com.epochmarket.config.LanguageService;
import com.epochmarket.config.MarketConfigService;
import com.epochmarket.config.SoundService;
import com.epochmarket.gui.MarketGuiListener;
import com.epochmarket.gui.MarketGuiService;
import com.epochmarket.integration.ReflectiveItemMatcher;
import com.epochmarket.service.InventoryService;
import com.epochmarket.service.SaleService;
import com.epochmarket.storage.QuotaRepository;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public final class EpochMarketPlugin extends JavaPlugin {
    private QuotaRepository quotas;
    private LanguageService language;
    private MarketConfigService markets;
    private SoundService sounds;
    private ZoneId resetZone;

    @Override
    public void onEnable() {
        saveDefaultResources();
        saveDefaultConfig();
        try {
            resetZone = ZoneId.of(getConfig().getString("reset-timezone", "Asia/Shanghai"));
        } catch (Exception exception) {
            getLogger().severe("Invalid reset-timezone. Use a Java timezone ID such as Asia/Shanghai.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Economy economy = economy();
        if (economy == null) {
            getLogger().severe("Vault has no registered Economy provider. EpochMarket is disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            quotas = new QuotaRepository(new File(getDataFolder(), getConfig().getString("database.file", "market.db")));
            quotas.initialize().get();
            int retention = getConfig().getInt("database.retention-days", 30);
            if (retention > 0) {
                quotas.deleteBefore(LocalDate.now(resetZone).minusDays(retention)).get();
            }
            language = new LanguageService(new File(getDataFolder(), "lang"), getLogger());
            markets = new MarketConfigService(new File(getDataFolder(), "markets"), getLogger());
            sounds = new SoundService(getLogger());
            if (!reloadServices()) {
                throw new IllegalStateException("Initial configuration load failed");
            }
        } catch (Exception exception) {
            getLogger().severe("Failed to initialize EpochMarket: " + exception.getMessage());
            exception.printStackTrace();
            safeCloseDatabase();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        ReflectiveItemMatcher matcher = new ReflectiveItemMatcher(getLogger());
        Clock clock = Clock.system(resetZone);
        InventoryService inventory = new InventoryService(matcher);
        SaleService sales = new SaleService(this, quotas, matcher, inventory, economy, clock);
        MarketGuiService gui = new MarketGuiService(this, markets, language, matcher, sales, sounds);
        Bukkit.getPluginManager().registerEvents(new MarketGuiListener(gui, sales, sounds), this);

        EpochMarketCommand executor = new EpochMarketCommand(this, gui, quotas, sales, language, clock, this::reloadServices);
        PluginCommand command = getCommand("epochmarket");
        if (command == null) {
            throw new IllegalStateException("epochmarket command missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getLogger().info("EpochMarket enabled with " + markets.markets().size() + " market(s).");
    }

    @Override
    public void onDisable() {
        safeCloseDatabase();
    }

    private boolean reloadServices() {
        try {
            reloadConfig();
            language.reload(getConfig().getString("default-language", "zh_CN"));
            markets.reload();
            sounds.reload(getConfig().getConfigurationSection("sounds"));
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof com.epochmarket.gui.SelectorHolder
                        || player.getOpenInventory().getTopInventory().getHolder(false) instanceof com.epochmarket.gui.MarketHolder
                        || player.getOpenInventory().getTopInventory().getHolder(false) instanceof com.epochmarket.gui.ConfirmHolder
                        || player.getOpenInventory().getTopInventory().getHolder(false) instanceof com.epochmarket.gui.LoadingConfirmHolder) {
                    player.closeInventory();
                }
            });
            return true;
        } catch (Exception exception) {
            getLogger().severe("EpochMarket reload failed: " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private void saveDefaultResources() {
        saveResource("lang/zh_CN.yml", false);
        saveResource("lang/en_US.yml", false);
        saveResource("markets/minerals.yml", false);
        saveResource("markets/plants.yml", false);
    }

    private void safeCloseDatabase() {
        if (quotas == null) {
            return;
        }
        quotas.close();
        quotas = null;
    }
}
