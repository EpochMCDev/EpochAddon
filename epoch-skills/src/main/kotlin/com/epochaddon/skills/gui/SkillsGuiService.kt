package com.epochaddon.skills.gui

import com.epochaddon.skills.config.SkillSettings
import com.epochaddon.skills.model.SkillNodeDefinition
import com.epochaddon.skills.model.SkillRequirement
import com.epochaddon.skills.model.SkillTreeDefinition
import com.epochaddon.skills.service.DefaultEpochSkillsService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class SkillsGuiService(
    private val plugin: JavaPlugin,
    private val skills: DefaultEpochSkillsService,
    private val settings: () -> SkillSettings,
) {
    private val miniMessage = MiniMessage.miniMessage()

    fun openRoot(player: Player) {
        open(player, SkillsScreen.Root, ROOT_SIZE, "<dark_gray>EpochSkills - 技能目录")
    }

    fun openCategory(player: Player, categoryId: String) {
        val title = when (categoryId) {
            CATEGORY_COMBAT -> "<dark_red>战斗技能"
            CATEGORY_GATHERING -> "<dark_green>采集技能"
            CATEGORY_TECHNOLOGY -> "<dark_aqua>科技技能"
            CATEGORY_LOGISTICS -> "<gold>后勤技能"
            else -> "<dark_gray>技能分类"
        }
        open(player, SkillsScreen.Category(categoryId), CATEGORY_SIZE, title)
    }

    fun openTree(player: Player, treeId: String) {
        val tree = skills.tree(treeId) ?: run {
            sendUnavailable(player)
            return
        }
        open(player, SkillsScreen.Tree(treeId), TREE_SIZE, tree.title)
    }

    fun refresh(player: Player) {
        val inventory = player.openInventory.topInventory
        val holder = inventory.getHolder(false) as? SkillsInventoryHolder ?: return
        render(player, inventory, holder.screen)
    }

    fun handleClick(player: Player, screen: SkillsScreen, slot: Int) {
        when (screen) {
            SkillsScreen.Root -> when (slot) {
                ROOT_COMBAT_SLOT -> openCategory(player, CATEGORY_COMBAT)
                ROOT_GATHERING_SLOT -> openCategory(player, CATEGORY_GATHERING)
                ROOT_TECHNOLOGY_SLOT -> openCategory(player, CATEGORY_TECHNOLOGY)
                ROOT_LOGISTICS_SLOT -> openCategory(player, CATEGORY_LOGISTICS)
            }

            is SkillsScreen.Category -> handleCategoryClick(player, screen.id, slot)
            is SkillsScreen.Tree -> {
                val tree = skills.tree(screen.id)
                val node = tree?.nodes?.firstOrNull { it.slot == slot }
                if (node != null) {
                    when (skills.unlockNode(player, screen.id, node.id)) {
                        DefaultEpochSkillsService.NodeUnlockResult.UNLOCKED -> Unit
                        DefaultEpochSkillsService.NodeUnlockResult.AUTO_UNLOCK ->
                            player.sendMessage(text(settings().messages.nodeAutoUnlock))
                        DefaultEpochSkillsService.NodeUnlockResult.ALREADY_UNLOCKED -> Unit
                        DefaultEpochSkillsService.NodeUnlockResult.NOT_FOUND,
                        DefaultEpochSkillsService.NodeUnlockResult.PREREQUISITES_MISSING,
                        DefaultEpochSkillsService.NodeUnlockResult.REQUIREMENT_NOT_MET,
                        -> player.sendMessage(text(settings().messages.nodeNotReady))
                    }
                    return
                }
                when (slot) {
                    TREE_BACK_SLOT -> openCategory(player, CATEGORY_GATHERING)
                    TREE_PREVIOUS_SLOT, TREE_NEXT_SLOT -> Unit
                }
            }
        }
    }

    private fun open(player: Player, screen: SkillsScreen, size: Int, title: String) {
        val holder = SkillsInventoryHolder(screen)
        val inventory = Bukkit.createInventory(holder, size, text(title))
        holder.bind(inventory)
        render(player, inventory, screen)
        player.openInventory(inventory)
    }

    private fun render(player: Player, inventory: Inventory, screen: SkillsScreen) {
        inventory.clear()
        fill(inventory)
        when (screen) {
            SkillsScreen.Root -> renderRoot(inventory)
            is SkillsScreen.Category -> renderCategory(inventory, screen.id)
            is SkillsScreen.Tree -> skills.tree(screen.id)?.let { renderTree(player, inventory, it) }
        }
    }

    private fun renderRoot(inventory: Inventory) {
        inventory.setItem(
            ROOT_COMBAT_SLOT,
            item(Material.IRON_SWORD, "<red><bold>战斗</bold></red>", listOf("<gray>剑艺、斧技与弓术</gray>")),
        )
        inventory.setItem(
            ROOT_GATHERING_SLOT,
            item(Material.IRON_PICKAXE, "<green><bold>采集</bold></green>", listOf("<gray>采矿、砍伐、挖掘、草药与钓鱼</gray>")),
        )
        inventory.setItem(
            ROOT_TECHNOLOGY_SLOT,
            item(Material.REDSTONE, "<aqua><bold>科技</bold></aqua>", listOf("<gray>科技技能树</gray>")),
        )
        inventory.setItem(
            ROOT_LOGISTICS_SLOT,
            item(Material.CHEST, "<gold><bold>后勤</bold></gold>", listOf("<gray>后勤技能树</gray>")),
        )
    }

    private fun renderCategory(inventory: Inventory, categoryId: String) {
        when (categoryId) {
            CATEGORY_COMBAT -> {
                inventory.setItem(0, unavailableItem(Material.IRON_SWORD, "剑艺"))
                inventory.setItem(1, unavailableItem(Material.IRON_AXE, "斧技"))
                inventory.setItem(2, unavailableItem(Material.BOW, "弓术"))
            }

            CATEGORY_GATHERING -> {
                inventory.setItem(
                    0,
                    item(Material.IRON_PICKAXE, "<green><bold>采矿</bold></green>", listOf("<yellow>点击查看技能树</yellow>")),
                )
                inventory.setItem(1, unavailableItem(Material.IRON_AXE, "砍伐"))
                inventory.setItem(2, unavailableItem(Material.IRON_SHOVEL, "挖掘"))
                inventory.setItem(3, unavailableItem(Material.FERN, "草药"))
                inventory.setItem(4, unavailableItem(Material.FISHING_ROD, "钓鱼"))
            }

            CATEGORY_TECHNOLOGY -> inventory.setItem(13, unavailableItem(Material.CRAFTING_TABLE, "科技"))
            CATEGORY_LOGISTICS -> inventory.setItem(2, unavailableItem(Material.BOW, "弓术"))
        }
        inventory.setItem(CATEGORY_BACK_SLOT, item(Material.ARROW, "<yellow>返回技能目录</yellow>"))
    }

    private fun renderTree(player: Player, inventory: Inventory, tree: SkillTreeDefinition) {
        val profile = skills.profile(player)
        val progress = profile.profession(tree.professionId)
        val nodes = tree.nodes.sortedBy { it.level }

        for ((index, slot) in tree.connectionSlots.withIndex()) {
            val previousNode = nodes.getOrNull(index / CONNECTIONS_PER_SEGMENT)
            val connected = previousNode != null && previousNode.id in progress.unlockedNodes
            inventory.setItem(
                slot,
                item(
                    if (connected) Material.LIME_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE,
                    " ",
                ),
            )
        }

        for (node in nodes) {
            inventory.setItem(node.slot, nodeItem(player, tree, node))
        }

        inventory.setItem(TREE_PREVIOUS_SLOT, unavailableItem(Material.ARROW, "上一页"))
        inventory.setItem(TREE_BACK_SLOT, item(Material.OAK_DOOR, "<yellow>返回采集目录</yellow>"))
        inventory.setItem(
            TREE_PROFESSION_SLOT,
            item(
                Material.NAME_TAG,
                "<gold>当前职业</gold>",
                listOf("<white>${skills.currentNode(player, tree)?.name ?: "未入门"}</white>"),
            ),
        )
        inventory.setItem(
            TREE_EXPERIENCE_SLOT,
            item(
                Material.EXPERIENCE_BOTTLE,
                "<aqua>经验值</aqua>",
                listOf("<white>${progress.experience}</white>"),
            ),
        )
        inventory.setItem(TREE_NEXT_LEVEL_SLOT, nextLevelItem(player, tree))
        inventory.setItem(TREE_NEXT_SLOT, unavailableItem(Material.ARROW, "下一页"))
    }

    private fun nodeItem(player: Player, tree: SkillTreeDefinition, node: SkillNodeDefinition): ItemStack {
        val progress = skills.profile(player).profession(tree.professionId)
        val unlocked = node.id in progress.unlockedNodes
        val prerequisitesMet = progress.unlockedNodes.containsAll(node.prerequisites)
        val material = when {
            unlocked -> node.icon
            prerequisitesMet -> Material.YELLOW_DYE
            else -> Material.GRAY_DYE
        }
        val status = when {
            unlocked -> "<green>已解锁</green>"
            node.autoUnlock -> "<gray>满足条件后自动解锁</gray>"
            prerequisitesMet && skills.isNodeAvailable(player, tree, node) -> "<yellow>可解锁，点击确认</yellow>"
            prerequisitesMet -> "<yellow>进行中</yellow>"
            else -> "<red>前置技能未解锁</red>"
        }
        val (current, required) = skills.requirementProgress(player, tree, node)
        val requirement = when (node.requirement) {
            is SkillRequirement.Experience ->
                "<gray>解锁消耗：<white>$required</white>（当前经验：<white>$current</white>）</gray>"
            is SkillRequirement.Counter -> "<gray>进度：<white>$current/$required</white></gray>"
        }
        val lore = buildList {
            add("<gray>等级 ${node.level}</gray>")
            addAll(node.description)
            add("")
            add(requirement)
            add(status)
        }
        return item(material, "<white><bold>${node.level};${node.name}</bold></white>", lore)
    }

    private fun nextLevelItem(player: Player, tree: SkillTreeDefinition): ItemStack {
        val next = skills.nextNode(player, tree)
            ?: return item(Material.NETHER_STAR, "<green>已完成当前技能树</green>")
        val (current, required) = skills.requirementProgress(player, tree, next)
        return item(
            Material.CLOCK,
            "<yellow>下一级：${next.name}</yellow>",
            listOf("<gray>解锁消耗：<white>$required</white>（当前经验：<white>$current</white>）</gray>"),
        )
    }

    private fun handleCategoryClick(player: Player, categoryId: String, slot: Int) {
        if (slot == CATEGORY_BACK_SLOT) {
            openRoot(player)
            return
        }
        if (categoryId == CATEGORY_GATHERING && slot == 0) {
            openTree(player, DIGGING_TREE_ID)
            return
        }
        if (slot in 0 until CATEGORY_SIZE) {
            sendUnavailable(player)
        }
    }

    private fun sendUnavailable(player: Player) {
        player.sendMessage(text(settings().messages.unavailable))
    }

    private fun fill(inventory: Inventory) {
        val filler = item(Material.BLACK_STAINED_GLASS_PANE, " ")
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, filler.clone())
        }
    }

    private fun unavailableItem(material: Material, name: String): ItemStack {
        return item(
            material,
            "<gray>$name</gray>",
            listOf("<dark_gray>待实装</dark_gray>"),
        )
    }

    private fun item(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta.displayName(if (name.isBlank()) Component.empty() else text(name))
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { line ->
                if (line.isBlank()) Component.empty() else text(line)
            })
        }
        stack.itemMeta = meta
        return stack
    }

    private fun text(raw: String): Component = miniMessage.deserialize("<italic:false>$raw")

    companion object {
        private const val ROOT_SIZE = 54
        private const val CATEGORY_SIZE = 27
        private const val TREE_SIZE = 54
        private const val ROOT_COMBAT_SLOT = 4
        private const val ROOT_GATHERING_SLOT = 13
        private const val ROOT_TECHNOLOGY_SLOT = 22
        private const val ROOT_LOGISTICS_SLOT = 31
        private const val CATEGORY_BACK_SLOT = 26
        private const val TREE_PREVIOUS_SLOT = 46
        private const val TREE_PROFESSION_SLOT = 48
        private const val TREE_EXPERIENCE_SLOT = 49
        private const val TREE_NEXT_LEVEL_SLOT = 50
        private const val TREE_NEXT_SLOT = 52
        private const val TREE_BACK_SLOT = 45
        private const val CONNECTIONS_PER_SEGMENT = 2
        private const val CATEGORY_COMBAT = "combat"
        private const val CATEGORY_GATHERING = "gathering"
        private const val CATEGORY_TECHNOLOGY = "technology"
        private const val CATEGORY_LOGISTICS = "logistics"
        private const val DIGGING_TREE_ID = "gathering_digging_skt_01"
    }
}
