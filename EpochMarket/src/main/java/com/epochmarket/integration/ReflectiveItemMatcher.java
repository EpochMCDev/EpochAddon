package com.epochmarket.integration;

import com.epochmarket.model.ItemSource;
import com.epochmarket.model.MarketEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Keeps optional plugin classes out of EpochMarket's class linkage. This lets vanilla
 * markets continue to work when CraftEngine or Slimefun is not installed.
 */
public final class ReflectiveItemMatcher implements ItemMatcher {
    private final Logger logger;
    private boolean craftEngineWarningLogged;
    private boolean slimefunWarningLogged;

    public ReflectiveItemMatcher(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean isAvailable(MarketEntry entry) {
        return switch (entry.source()) {
            case VANILLA -> Material.matchMaterial(entry.itemId()) != null;
            case CRAFT_ENGINE -> customItemById(entry.itemId()) != null;
            case SLIMEFUN -> slimefunItemById(entry.itemId()) != null;
        };
    }

    @Override
    public boolean matches(MarketEntry entry, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return switch (entry.source()) {
            case VANILLA -> matchesVanilla(entry.itemId(), stack);
            case CRAFT_ENGINE -> entry.itemId().equalsIgnoreCase(craftEngineItemId(stack));
            case SLIMEFUN -> entry.itemId().equalsIgnoreCase(slimefunItemId(stack));
        };
    }

    private boolean matchesVanilla(String itemId, ItemStack stack) {
        Material material = Material.matchMaterial(itemId);
        if (material == null || stack.getType() != material) {
            return false;
        }
        // A custom stack sharing a vanilla material must remain a custom item.
        return craftEngineItemId(stack) == null && slimefunItemId(stack) == null;
    }

    private Object customItemById(String id) {
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            return api.getMethod("byId", String.class).invoke(null, id);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnCraftEngine(exception);
            return null;
        }
    }

    private String craftEngineItemId(ItemStack stack) {
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Object key = api.getMethod("getCustomItemId", ItemStack.class).invoke(null, stack);
            return key == null ? null : key.toString();
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnCraftEngine(exception);
            return null;
        }
    }

    private Object slimefunItemById(String id) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            return null;
        }
        try {
            Class<?> itemClass = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
            return itemClass.getMethod("getById", String.class).invoke(null, id);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnSlimefun(exception);
            return null;
        }
    }

    private String slimefunItemId(ItemStack stack) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            return null;
        }
        try {
            Class<?> itemClass = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
            Object item = itemClass.getMethod("getByItem", ItemStack.class).invoke(null, stack);
            if (item == null) {
                return null;
            }
            Method getId = itemClass.getMethod("getId");
            return (String) getId.invoke(item);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnSlimefun(exception);
            return null;
        }
    }

    private void warnCraftEngine(Throwable exception) {
        if (!craftEngineWarningLogged) {
            craftEngineWarningLogged = true;
            logger.warning("CraftEngine integration could not be initialized: " + rootMessage(exception));
        }
    }

    private void warnSlimefun(Throwable exception) {
        if (!slimefunWarningLogged) {
            slimefunWarningLogged = true;
            logger.warning("Slimefun integration could not be initialized: " + rootMessage(exception));
        }
    }

    private static String rootMessage(Throwable exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}

