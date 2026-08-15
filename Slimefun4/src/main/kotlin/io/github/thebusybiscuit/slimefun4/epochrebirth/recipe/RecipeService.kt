package io.github.thebusybiscuit.slimefun4.epochrebirth.recipe

import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItem
import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItems
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** 合成表常量（原 config.yml recipes 内容） */
class RecipeService {

    private val recipes: List<RebirthRecipe> = listOf(
        recipe("soul-bottle", listOf("TTT", "TBT", "TTT"), mapOf('T' to IngredientSpec.Material(Material.TORCH), 'B' to IngredientSpec.Material(Material.GLASS_BOTTLE)), RebirthItem.SOUL_BOTTLE, 1),
        recipe("meat-from-beef", listOf("M"), mapOf('M' to IngredientSpec.Material(Material.BEEF)), RebirthItem.MEAT, 1),
        recipe("meat-from-pork", listOf("M"), mapOf('M' to IngredientSpec.Material(Material.PORKCHOP)), RebirthItem.MEAT, 1),
        recipe("meat-from-chicken", listOf("M"), mapOf('M' to IngredientSpec.Material(Material.CHICKEN)), RebirthItem.MEAT, 1),
        recipe("meat-from-mutton", listOf("M"), mapOf('M' to IngredientSpec.Material(Material.MUTTON)), RebirthItem.MEAT, 1),
        recipe("basic-core", listOf("MBM", "BSB", "MBM"), mapOf(
            'M' to IngredientSpec.Custom(RebirthItem.MEAT),
            'B' to IngredientSpec.Material(Material.BONE),
            'S' to IngredientSpec.Custom(RebirthItem.SOUL)
        ), RebirthItem.CORE_BASIC, 1),
        recipe("advanced-core", listOf("GBA", "MCM", "ABG"), mapOf(
            'G' to IngredientSpec.Material(Material.GLISTERING_MELON_SLICE),
            'B' to IngredientSpec.Material(Material.BONE_BLOCK),
            'A' to IngredientSpec.Material(Material.GHAST_TEAR),
            'M' to IngredientSpec.Custom(RebirthItem.MEAT),
            'C' to IngredientSpec.Custom(RebirthItem.CORE_BASIC)
        ), RebirthItem.CORE_ADVANCED, 1),
        recipe("ultimate-core", listOf("GBA", "BCB", "ABG"), mapOf(
            'G' to IngredientSpec.Material(Material.GLISTERING_MELON_SLICE),
            'B' to IngredientSpec.Material(Material.BONE_BLOCK),
            'A' to IngredientSpec.Material(Material.GOLDEN_APPLE),
            'C' to IngredientSpec.Custom(RebirthItem.CORE_ADVANCED)
        ), RebirthItem.CORE_ULTIMATE, 1),
        recipe("basic-totem", listOf("III", "ICI", "IBI"), mapOf(
            'I' to IngredientSpec.Material(Material.COPPER_INGOT),
            'B' to IngredientSpec.Material(Material.COPPER_BLOCK),
            'C' to IngredientSpec.Custom(RebirthItem.CORE_BASIC)
        ), RebirthItem.TOTEM_BASIC, 1),
        recipe("advanced-totem", listOf("III", "ICI", "IBI"), mapOf(
            'I' to IngredientSpec.Material(Material.IRON_INGOT),
            'B' to IngredientSpec.Material(Material.IRON_BLOCK),
            'C' to IngredientSpec.Custom(RebirthItem.CORE_ADVANCED)
        ), RebirthItem.TOTEM_ADVANCED, 1),
        recipe("ultimate-totem", listOf("III", "ICI", "IBI"), mapOf(
            'I' to IngredientSpec.Material(Material.GOLD_INGOT),
            'B' to IngredientSpec.Material(Material.GOLD_BLOCK),
            'C' to IngredientSpec.Custom(RebirthItem.CORE_ULTIMATE)
        ), RebirthItem.TOTEM_ULTIMATE, 1),
        recipe("healing-core", listOf("MGM", "GAG", "MGM"), mapOf(
            'M' to IngredientSpec.Custom(RebirthItem.MEAT),
            'G' to IngredientSpec.Material(Material.GLISTERING_MELON_SLICE),
            'A' to IngredientSpec.Material(Material.ARROW)
        ), RebirthItem.HEALING_CORE, 1),
        recipe("healing-arrow-i", listOf("MMG", "MCM", "BMM"), mapOf(
            'M' to IngredientSpec.Custom(RebirthItem.MEAT),
            'G' to IngredientSpec.Material(Material.GOLD_BLOCK),
            'B' to IngredientSpec.Material(Material.MELON),
            'C' to IngredientSpec.Custom(RebirthItem.HEALING_CORE)
        ), RebirthItem.HEALING_ARROW_I, 1),
        recipe("healing-arrow-ii", listOf("GTG", "SCS", "MTM"), mapOf(
            'G' to IngredientSpec.Material(Material.GOLD_BLOCK),
            'T' to IngredientSpec.Material(Material.GHAST_TEAR),
            'S' to IngredientSpec.Material(Material.GLISTERING_MELON_SLICE),
            'M' to IngredientSpec.Material(Material.MELON),
            'C' to IngredientSpec.Custom(RebirthItem.HEALING_ARROW_I)
        ), RebirthItem.HEALING_ARROW_II, 1)
    )

    fun all(): List<RebirthRecipe> = recipes

    private fun recipe(
        key: String, shape: List<String>, specs: Map<Char, IngredientSpec>, result: RebirthItem, amount: Int
    ): RebirthRecipe = RebirthRecipe(key, shape, specs, result, amount)
}

sealed class IngredientSpec {
    data class Material(val material: org.bukkit.Material) : IngredientSpec()
    data class Custom(val item: RebirthItem) : IngredientSpec()
    data class AnyOf(val materials: List<org.bukkit.Material>) : IngredientSpec()

    fun matches(stack: ItemStack?, items: RebirthItems): Boolean {
        if (stack == null || stack.type.isAir) return false
        return when (this) {
            is Material -> stack.type == material && items.identityOf(stack) == null
            is Custom -> items.identityOf(stack) == item
            is AnyOf -> stack.type in materials && items.identityOf(stack) == null
        }
    }
}

class RebirthRecipe(
    val key: String,
    shape: List<String>,
    private val specs: Map<Char, IngredientSpec>,
    val result: RebirthItem,
    val resultAmount: Int
) {
    private val normalized: List<String>

    /** 配方占用的格子数，匹配时大的配方优先 */
    val size: Int

    init {
        val width = shape.maxOfOrNull { it.length } ?: 0
        normalized = shape.map { it.padEnd(width, ' ') }
        size = normalized.sumOf { row -> row.count { it != ' ' } }
    }

    /** 把形状网格转换成 3x3（行优先）的 Slimefun 配方数组。 */
    fun toSlimefunRecipe(items: RebirthItems, slimefunStacks: Map<RebirthItem, SlimefunItemStack>): Array<ItemStack?> {
        val padded = normalized.map { it.padEnd(3, ' ') }.take(3) + List((3 - normalized.size).coerceAtLeast(0)) { "   " }
        return Array(9) { index ->
            val row = index / 3
            val col = index % 3
            val char = padded[row][col]
            if (char == ' ') null else specs[char]?.let { spec ->
                when (spec) {
                    is IngredientSpec.Material -> ItemStack(spec.material)
                    is IngredientSpec.Custom -> slimefunStacks[spec.item]
                    is IngredientSpec.AnyOf -> ItemStack(spec.materials.first())
                }
            }
        }
    }

    fun matches(grid: List<ItemStack?>, items: RebirthItems): Boolean {
        if (grid.size != 9) return false
        val dimension = 3
        val rows = List(dimension) { row -> List(dimension) { col -> grid[row * dimension + col] } }
        val usedRows = rows.indices.filter { row -> rows[row].any { isFilled(it) } }
        if (usedRows.isEmpty()) return false
        val usedCols = rows[0].indices.filter { col -> rows.any { row -> isFilled(row[col]) } }
        if (usedRows.size != normalized.size) return false
        if (usedCols.size != normalized[0].length) return false
        usedRows.forEachIndexed { rowIndex, row ->
            usedCols.forEachIndexed { colIndex, col ->
                val expected = normalized[rowIndex][colIndex]
                val stack = rows[row][col]
                if (expected == ' ') {
                    if (isFilled(stack)) return false
                } else {
                    val spec = specs[expected] ?: return false
                    if (!spec.matches(stack, items)) return false
                }
            }
        }
        return true
    }

    fun ingredientAt(row: Int, column: Int): IngredientSpec? {
        val char = normalized.getOrNull(row)?.getOrNull(column) ?: return null
        return if (char == ' ') null else specs[char]
    }

    private fun isFilled(stack: ItemStack?): Boolean = stack != null && !stack.type.isAir
}
