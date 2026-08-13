package io.github.thebusybiscuit.slimefun4.epochrebirth.recipe

import io.github.thebusybiscuit.slimefun4.epochrebirth.config.LanguageService
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItem
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockInteractEvent
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.OutputChest
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Dispenser
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.logging.Logger

/**
 * 粘液科技扩展：把物品注册进指南书，并监听工作台交互完成合成
 * （支持 any: 多材料匹配）。产物优先进入相邻物品输出箱，否则保留在发射器里。
 */
class SlimefunBridge(
    private val plugin: Slimefun,
    private val logger: Logger,
    private val items: RebirthItems,
    private val language: LanguageService,
    private val recipes: List<RebirthRecipe>
) : Listener {

    /** 注册进 Slimefun 的物品栈（带 Slimefun 标识），合成产物直接使用它们 */
    private val slimefunStacks = HashMap<RebirthItem, SlimefunItemStack>()

    /** 匹配用配方，占用格子多的优先（避免 1 格肉配方抢在核心配方之前） */
    private val matchRecipes = recipes.sortedByDescending { it.size }

    fun registerAll(): Boolean {
        return try {
            // 顶层分类直接装物品，指南书里点一次图标即可看到物品列表。
            // 图标用不死图腾，tier=0 使其排在武器分类（tier=1）之前，位于指南书第一位。
            val group = ItemGroup(
                NamespacedKey(plugin, "epoch_rebirth"),
                ItemStack(Material.TOTEM_OF_UNDYING),
                0
            )

            // 注册顺序即指南书展示顺序：缚魂瓶第一、瓶装魂第二、其余按配置顺序
            for (recipe in recipes.distinctBy { it.result }) {
                registerCraftable(group, recipe)
                if (recipe.result == RebirthItem.SOUL_BOTTLE) {
                    registerSoulHint(group)
                }
            }
            logger.info("粘液科技注册完成：${slimefunStacks.size} 个物品")
            true
        } catch (exception: Exception) {
            logger.warning("Slimefun 物品注册失败: ${exception.message}")
            exception.printStackTrace()
            false
        }
    }

    /** 注册可合成物品：同一产物的多个配方（如四种肉压制肉块）只注册第一个作为指南书展示 */
    private fun registerCraftable(group: ItemGroup, recipe: RebirthRecipe) {
        val stack = SlimefunItemStack(sfId(recipe.result), items.create(recipe.result))
        slimefunStacks[recipe.result] = stack
        val recipeArray = recipe.toSlimefunRecipe(items, slimefunStacks)
        SlimefunItem(group, stack, RecipeType.ENHANCED_CRAFTING_TABLE, recipeArray).register(Slimefun.instance()!!)
    }

    /** 注册瓶装魂：配方页展示一张提示纸（并非真实合成表），说明获取方式 */
    private fun registerSoulHint(group: ItemGroup) {
        val soulStack = SlimefunItemStack(sfId(RebirthItem.SOUL), items.create(RebirthItem.SOUL))
        slimefunStacks[RebirthItem.SOUL] = soulStack
        val paper = ItemStack(Material.PAPER)
        val meta = paper.itemMeta
        meta.displayName(language.component("items.soul.hint-name"))
        meta.lore(language.components("items.soul.hint-lore").map { it.decoration(TextDecoration.ITALIC, false) })
        paper.itemMeta = meta
        SlimefunItem(group, soulStack, RecipeType.ENHANCED_CRAFTING_TABLE, arrayOf(paper)).register(Slimefun.instance()!!)
    }

    @EventHandler
    fun onMultiBlockInteract(event: MultiBlockInteractEvent) {
        if (event.isCancelled) return
        val player = event.player
        val dispenser = findDispenser(event.clickedBlock) ?: return
        val inventory = (dispenser.state as? Dispenser)?.inventory ?: return
        val grid = (0 until 9).map { inventory.getItem(it) }
        val recipe = matchRecipes.firstOrNull { it.matches(grid, items) }
        if (recipe == null) {
            // 指南书展示用的"纸 → 瓶装魂"不是真实合成表，阻止工作台用纸合成
            val nonAir = grid.filterNotNull().filterNot { it.type.isAir }
            if (nonAir.size == 1 && nonAir[0].type == Material.PAPER) {
                event.isCancelled = true
                player.sendMessage(language.component("messages.soul-capture-hint"))
            }
            return
        }

        event.isCancelled = true

        // 复刻正统粘液科技流程（EnhancedCraftingTable.craft）：
        // 产物优先推进发射器旁相邻的「物品输出箱」(OutputChest)，没有则留在发射器；
        // 两者都放不下时取消合成并提示机器已满（不消耗材料）
        val output = slimefunStacks[recipe.result]?.clone()?.apply { amount = recipe.resultAmount }
            ?: items.create(recipe.result, recipe.resultAmount)

        val outputChest = OutputChest.findOutputChestFor(dispenser, output)
        val target: Inventory = if (outputChest.isPresent) {
            outputChest.get()
        } else if (fitsAfterCraft(inventory, grid, output)) {
            inventory
        } else {
            Slimefun.getLocalization().sendMessage(player, "machines.full-inventory", true)
            return
        }

        for (index in 0 until 9) {
            val stack = inventory.getItem(index) ?: continue
            if (stack.amount > 1) stack.amount -= 1 else inventory.setItem(index, null)
        }
        val leftovers = target.addItem(output)
        leftovers.values.forEach {
            player.world.dropItemNaturally(dispenser.getRelative(BlockFace.UP).location, it)
        }
        SoundEffect.ENHANCED_CRAFTING_TABLE_CRAFT_SOUND.playAt(dispenser)
    }

    /** 模拟消耗原料后的发射器库存是否还放得下产物（复刻正统流程的虚拟库存检查） */
    private fun fitsAfterCraft(inventory: Inventory, grid: List<ItemStack?>, output: ItemStack): Boolean {
        val virtual = Bukkit.createInventory(null, InventoryType.DISPENSER)
        grid.forEachIndexed { index, stack -> if (stack != null) virtual.setItem(index, stack.clone()) }
        for (index in 0 until 9) {
            val stack = virtual.getItem(index) ?: continue
            if (stack.amount > 1) stack.amount -= 1 else virtual.setItem(index, null)
        }
        return virtual.addItem(output.clone()).isEmpty()
    }

    private fun findDispenser(clicked: Block): Block? {
        val candidates = listOf(
            clicked,
            clicked.getRelative(BlockFace.DOWN),
            clicked.getRelative(BlockFace.UP),
            clicked.getRelative(BlockFace.NORTH),
            clicked.getRelative(BlockFace.SOUTH),
            clicked.getRelative(BlockFace.EAST),
            clicked.getRelative(BlockFace.WEST)
        )
        return candidates.firstOrNull { it.type == Material.DISPENSER }
    }

    private fun sfId(item: RebirthItem): String = "EPOCH_REBIRTH_" + item.id.uppercase()
}