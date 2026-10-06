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

        // Tank controller: any mineral wall 1, tank valve 2, iron bar 10, copper bar 10,
        // vanilla glass 10 (N10-2).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.TANK_CONTROLLER,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient(MechanicsIngredients.ANY_MINERAL_WALL, 1),
                        new Ingredient(MechanicsObjects.TANK_VALVE, 2),
                        new Ingredient("ironbar", 10),
                        new Ingredient("copperbar", 10),
                        new Ingredient("glass", 10)
                }
        ));

        // Tank valve: any mineral wall 1, any pipe 2 (N10-3).
        // TODO(game): no pipe exists yet, so the "any pipe" group is empty and this recipe cannot be
        // crafted until the pipes are added (4th implementation round).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.TANK_VALVE,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient(MechanicsIngredients.ANY_MINERAL_WALL, 1),
                        new Ingredient(MechanicsIngredients.ANY_PIPE, 2)
                }
        ));

        // Glass block: vanilla glass 5 -> 1 (N10-4).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.GLASS_BLOCK,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient("glass", 5)
                }
        ));
    }

}
