package io.github.thebusybiscuit.slimefun4.epochrebirth.config

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 文案服务：内置默认文案（defaults），可被外部文件
 * plugins/Slimefun/epoch-rebirth-lang.yml 覆盖，/erb reload 生效。
 */
class LanguageService(private val plugin: Slimefun) {

    private val miniMessage = MiniMessage.miniMessage()
    private val overrides = mutableMapOf<String, String>()

    private val defaults = mapOf(
        "hud.title" to "<dark_red>☠ <red>EpochMC",
        "hud.basic-line" to "<gold>橙色❤ <gray>× <white><count>",
        "hud.advanced-line" to "<#ff9f9f>浅红❤ <gray>× <white><count>",
        "hud.ultimate-line" to "<red>红❤ <gray>× <white><count>",
        "menu.title" to "复活图腾优先级",
        "menu.count-lore" to "<gray>当前复活次数：<white><count><gray>/<max>",
        "menu.order-lore" to "<green>当前使用顺序：<white>第 <order> 优先",
        "menu.click-hint" to "<gray><i>点击设为第一优先",
        "menu.tier-name-none" to "<red>无图腾</red>",
        "menu.tier-name-basic" to "<gold>初级图腾</gold>",
        "menu.tier-name-advanced" to "<#ff9f9f>高级图腾</#ff9f9f>",
        "menu.tier-name-ultimate" to "<red>终极图腾</red>",
        "items.soul_bottle.name" to "<aqua><i:false>缚魂瓶",
        "items.soul_bottle.lore" to "<gray>对着动物灵魂右键，可收集瓶装魂",
        "items.soul.name" to "<dark_aqua><i:false>瓶装魂",
        "items.soul.lore" to "<gray>合成重生核心的材料",
        "items.soul.lore2" to "<light_purple><i>你听见他们的呐喊了吗？",
        "items.soul.hint-name" to "<yellow><i:false>获取提示",
        "items.soul.hint-lore" to "<gray>杀死对应生物会生成魂魄",
        "items.soul.hint-lore2" to "<gray>用缚魂瓶装取可获得瓶装魂",
        "items.meat.name" to "<red><i:false>肉块",
        "items.meat.lore" to "<gray>由任意生肉在粘液科技工作台压制而成",
        "items.meat.lore2" to "<gray>合成重生核心的材料",
        "items.core_basic.name" to "<yellow><i:false>初级重生核心",
        "items.core_basic.lore" to "<gray>合成初级复活图腾的材料",
        "items.core_advanced.name" to "<gold><i:false>高级重生核心",
        "items.core_advanced.lore" to "<gray>合成高级复活图腾的材料",
        "items.core_ultimate.name" to "<red><i:false>终极重生核心",
        "items.core_ultimate.lore" to "<gray>合成终极复活图腾的材料",
        "items.totem_basic.name" to "<gold><i:false>初级复活图腾",
        "items.totem_basic.lore" to "<gray>右键使用：初级复活次数 +1",
        "items.totem_advanced.name" to "<#ff9f9f><i:false>高级复活图腾",
        "items.totem_advanced.lore" to "<gray>右键使用：高级复活次数 +1",
        "items.totem_ultimate.name" to "<red><i:false>终极复活图腾",
        "items.totem_ultimate.lore" to "<gray>右键使用：终极复活次数 +1",
        "messages.totem-added" to "<green>复活次数 +1（<tier>：<count>/<max>）",
        "messages.totem-full" to "<red>该等级的复活次数已达上限（<max>）",
        "messages.soul-captured" to "<aqua>灵魂已被收入缚魂瓶",
        "messages.soul-gone" to "<red>灵魂已经消散了",
        "messages.soul-capture-hint" to "<aqua>瓶装魂无法合成：杀死动物生成魂魄，用缚魂瓶装取",
        "messages.death-consumed" to "<yellow>你消耗了一次<tier>复活，金币 <money>",
        "messages.death-no-totem" to "<red>你没有任何复活图腾，受到最严重的死亡惩罚！金币 <money>",
        "messages.penalty-money" to "<red>金币 -<money>（余额可为负数）",
        "messages.priority-set" to "<green>已将<tier>设为第一优先",
        "messages.menu-no-permission" to "<red>你没有权限打开复活菜单",
        "messages.reload-done" to "<green>EpochRebirth 已重载",
        "messages.no-permission" to "<red>你没有权限执行此操作",
        "messages.player-not-found" to "<red>找不到该玩家",
        "messages.get-info" to "<gray><player> 的复活次数：<gold>初级 <basic> <gray>/ <#ff9f9f>高级 <advanced> <gray>/ <red>终极 <ultimate>",
        "messages.set-done" to "<green>已将 <player> 的 <tier> 复活次数设为 <count>",
        "messages.health-reset" to "<green>已重置 <player> 的生命上限惩罚",
        "messages.economy-failed" to "<red>金币扣除失败（经济插件返回失败），请检查经济配置",
        "messages.cannot-place" to "<red>这个物品不能放置",
        "messages.invalid-tier" to "<red>无效的图腾等级：<tier>（可选 basic/advanced/ultimate）",
        "messages.invalid-count" to "<red>数量必须为 0~<max> 的整数",
        "messages.invalid-item" to "<red>无效的物品 id：<item>",
        "messages.give-done" to "<green>已给予 <player> 物品 <item> × <amount>",
        "messages.usage" to "<gray>用法：<white>/epochrebirth [menu|get|set|give|reload]"
    )

    /** 加载外部文案文件；不存在时从 jar 生成默认文件 */
    fun reload() {
        overrides.clear()
        val file = File(plugin.dataFolder, "epoch-rebirth-lang.yml")
        if (!file.isFile) {
            plugin.saveResource("epoch-rebirth-lang.yml", false)
        }
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getKeys(false).forEach { key ->
            yaml.getString(key)?.let { overrides[key] = it }
        }
    }

    private fun text(key: String): String = overrides[key] ?: defaults[key] ?: "<red>Missing language key: $key"

    fun component(key: String, values: Map<String, String> = emptyMap(), rawValues: Set<String> = emptySet()): Component =
        miniMessage.deserialize(replace(text(key), values, rawValues))

    fun components(key: String, values: Map<String, String> = emptyMap()): List<Component> {
        val list = mutableListOf<Component>()
        list.add(miniMessage.deserialize(replace(text(key), values)))
        // 支持 items.*.lore / items.*.lore2 多行后缀
        var index = 2
        while (true) {
            val extra = text("$key$index").takeIf { it != "<red>Missing language key: $key$index" } ?: break
            list.add(miniMessage.deserialize(replace(extra, values)))
            index++
        }
        return list
    }

    fun plain(key: String, values: Map<String, String> = emptyMap()): String =
        replace(overrides[key] ?: defaults[key] ?: key, values)

    private fun replace(template: String, values: Map<String, String>, rawValues: Set<String> = emptySet()): String =
        values.entries.fold(template) { acc, (key, value) ->
            acc.replace("<$key>", if (key in rawValues) value else miniMessage.escapeTags(value))
        }
}
