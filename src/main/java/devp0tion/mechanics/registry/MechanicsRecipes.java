package devp0tion.mechanics.registry;

import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.objects.MineralWallObject;
import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;

/**
 * Recipe registration (called from postInit, after all items exist).
 */
public final class MechanicsRecipes {

    /**
     * Any stone: the recipes' "stone" (돌) means any stone, the vanilla {@code anystone} ingredient
     * (N8-3). Used by the engineering workbench (N1-1) and, once the pump exists, the manual pump
     * (N3-1: anylog 20 + anystone 20).
     */
    public static final String ANY_STONE = "anystone";

    private MechanicsRecipes() {
    }

    public static void load() {
        // Engineering workbench: crafted at the tier-1 workstation (workstationduo) (13-2)
        // from logs 10, any stone 10, copper bars 10, iron bars 10 (N1-1, N8-3).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.ENGINEERING_WORKBENCH,
                1,
                RecipeTechRegistry.WORKSTATION,
                new Ingredient[]{
                        new Ingredient("anylog", 10),
                        new Ingredient(ANY_STONE, 10),
                        new Ingredient("copperbar", 10),
                        new Ingredient("ironbar", 10)
                }
        ));

        // Mineral walls: 1 bar -> 1 wall (8-2), at the engineering workbench like every mod element (13-1).
        for (MineralTier tier : MineralTier.values()) {
            Recipes.registerModRecipe(new Recipe(
                    MineralWallObject.stringIDOf(tier),
                    1,
                    MechanicsTech.ENGINEERING,
                    new Ingredient[]{
                            new Ingredient(tier.getBarStringID(), 1)
                    }
            ));
        }
    }

}
