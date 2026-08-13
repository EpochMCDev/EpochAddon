package io.github.thebusybiscuit.slimefun4.epochrebirth

import com.epochaddon.common.util.VersionUtil
import io.github.thebusybiscuit.slimefun4.epochrebirth.command.RebirthCommand
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.config.RebirthConfig
import io.github.thebusybiscuit.slimefun4.epochrebirth.death.DeathHandler
import io.github.thebusybiscuit.slimefun4.epochrebirth.economy.EconomyService
import io.github.thebusybiscuit.slimefun4.epochrebirth.gui.PriorityMenu
import io.github.thebusybiscuit.slimefun4.epochrebirth.hud.RebirthHud
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.ItemGuardListener
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.PdcKeys
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import io.github.thebusybiscuit.slimefun4.epochrebirth.recipe.RecipeService
import io.github.thebusybiscuit.slimefun4.epochrebirth.recipe.SlimefunBridge
import io.github.thebusybiscuit.slimefun4.epochrebirth.soul.SoulListener
import io.github.thebusybiscuit.slimefun4.epochrebirth.storage.ResurrectionStore
import io.github.thebusybiscuit.slimefun4.epochrebirth.totem.TotemListener
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import java.util.logging.Level

/** EpochRebirth 合并版初始化入口，由 SlimefunItemSetup 调用 */
object RebirthSetup {

    @JvmStatic
    fun setup(plugin: Slimefun) {
        try {
            val keys = PdcKeys()
            val language = LanguageService(plugin)
            language.reload()
            val config = RebirthConfig()
            val economy = EconomyService()
            if (!economy.setup()) {
                plugin.logger.warning("Vault 经济提供者不可用，死亡金币惩罚将失效")
            }
            val items = RebirthItems(keys, language)
            val store = ResurrectionStore(keys) { config.maxCount }
            val hud = RebirthHud(store, config, language)
            val recipes = RecipeService(items)
            val menu = PriorityMenu(store, config, items, language)

            val manager = plugin.server.pluginManager
            val bridge = SlimefunBridge(plugin, plugin.logger, items, language, recipes.all())
            bridge.registerAll()
            manager.registerEvents(bridge, plugin)
            val deathHandler = DeathHandler(plugin, config, store, economy, hud, language)
            manager.registerEvents(deathHandler, plugin)
            manager.registerEvents(TotemListener(config, store, items, hud, language), plugin)
            manager.registerEvents(SoulListener(config, items, keys, language), plugin)
            manager.registerEvents(ItemGuardListener(items, language), plugin)
            manager.registerEvents(hud, plugin)
            manager.registerEvents(menu, plugin)

            val command = plugin.getCommand("erb")
                ?: throw IllegalStateException("erb 命令缺失（Slimefun plugin.yml）")
            val executor = RebirthCommand(menu, store, config, hud, items, language, deathHandler) {
                language.reload()
                config.reload()
            }
            command.setExecutor(executor)
            command.tabCompleter = executor

            hud.refreshAll()
            plugin.logger.info(
                "EpochRebirth 已集成，服务端版本：${VersionUtil.serverVersion(plugin.server)}，合成表 ${recipes.all().size} 个"
            )
        } catch (exception: Exception) {
            plugin.logger.log(Level.SEVERE, "EpochRebirth 集成初始化失败", exception)
        }
    }
}
