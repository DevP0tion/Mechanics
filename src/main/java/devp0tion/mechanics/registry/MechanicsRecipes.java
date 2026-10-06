package devp0tion.mechanics.registry;

import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;

/**
 * Recipe registration (called from postInit, after all items exist).
 */
public final class MechanicsRecipes {

    private MechanicsRecipes() {
    }

    public static void load() {
        // Engineering workbench: crafted at the tier-1 workstation (workstationduo) (13-2)
        // from logs 10, stone 10, copper bars 10, iron bars 10 (N1-1).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.ENGINEERING_WORKBENCH,
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("anylog", 10),
                        new Ingredient("stone", 10),
                        new Ingredient("copperbar", 10),
                        new Ingredient("ironbar", 10)
                }
        ));
    }

}
