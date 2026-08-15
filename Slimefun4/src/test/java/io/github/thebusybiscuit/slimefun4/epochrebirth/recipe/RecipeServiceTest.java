package io.github.thebusybiscuit.slimefun4.epochrebirth.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.thebusybiscuit.slimefun4.epochrebirth.item.RebirthItem;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RecipeServiceTest {

    private final RecipeService service = new RecipeService();

    @Test
    void keepsCustomMeatAsCoreIngredient() {
        var basicCore = recipe("basic-core");
        assertCustom(basicCore, 0, 0, RebirthItem.MEAT);
        assertMaterial(basicCore, 0, 1, Material.BONE);
        assertCustom(basicCore, 1, 1, RebirthItem.SOUL);

        var advancedCore = recipe("advanced-core");
        assertCustom(advancedCore, 1, 0, RebirthItem.MEAT);
        assertCustom(advancedCore, 1, 1, RebirthItem.CORE_BASIC);
        assertMaterial(advancedCore, 0, 2, Material.GHAST_TEAR);
    }

    @Test
    void matchesHealingRecipesFromPlanningImages() {
        var core = recipe("healing-core");
        assertCustom(core, 0, 0, RebirthItem.MEAT);
        assertMaterial(core, 0, 1, Material.GLISTERING_MELON_SLICE);
        assertMaterial(core, 1, 1, Material.ARROW);

        var levelOne = recipe("healing-arrow-i");
        assertCustom(levelOne, 0, 0, RebirthItem.MEAT);
        assertMaterial(levelOne, 0, 2, Material.GOLD_BLOCK);
        assertCustom(levelOne, 1, 1, RebirthItem.HEALING_CORE);
        assertMaterial(levelOne, 2, 0, Material.MELON);

        var levelTwo = recipe("healing-arrow-ii");
        assertMaterial(levelTwo, 0, 1, Material.GHAST_TEAR);
        assertMaterial(levelTwo, 1, 0, Material.GLISTERING_MELON_SLICE);
        assertCustom(levelTwo, 1, 1, RebirthItem.HEALING_ARROW_I);
        assertMaterial(levelTwo, 2, 0, Material.MELON);
    }

    @Test
    void usesMetalProgressionForResurrectionTotems() {
        var basic = recipe("basic-totem");
        assertMaterial(basic, 0, 0, Material.COPPER_INGOT);
        assertCustom(basic, 1, 1, RebirthItem.CORE_BASIC);
        assertMaterial(basic, 2, 1, Material.COPPER_BLOCK);

        var advanced = recipe("advanced-totem");
        assertMaterial(advanced, 0, 0, Material.IRON_INGOT);
        assertCustom(advanced, 1, 1, RebirthItem.CORE_ADVANCED);
        assertMaterial(advanced, 2, 1, Material.IRON_BLOCK);

        var ultimate = recipe("ultimate-totem");
        assertMaterial(ultimate, 0, 0, Material.GOLD_INGOT);
        assertCustom(ultimate, 1, 1, RebirthItem.CORE_ULTIMATE);
        assertMaterial(ultimate, 2, 1, Material.GOLD_BLOCK);
    }

    @Test
    void containsAllCraftingVariants() {
        assertEquals(14, service.all().size());
    }

    private RebirthRecipe recipe(String key) {
        return service.all().stream()
                .filter(recipe -> recipe.getKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private void assertMaterial(RebirthRecipe recipe, int row, int column, Material expected) {
        var ingredient = assertInstanceOf(IngredientSpec.Material.class, recipe.ingredientAt(row, column));
        assertEquals(expected, ingredient.getMaterial());
    }

    private void assertCustom(RebirthRecipe recipe, int row, int column, RebirthItem expected) {
        var ingredient = assertInstanceOf(IngredientSpec.Custom.class, recipe.ingredientAt(row, column));
        assertEquals(expected, ingredient.getItem());
    }
}
