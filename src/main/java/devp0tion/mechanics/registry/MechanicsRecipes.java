package devp0tion.mechanics.registry;

import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.PumpForm;
import devp0tion.mechanics.items.MechanicsWrenchItem;
import devp0tion.mechanics.objects.BasicPipeObject;
import devp0tion.mechanics.objects.MineralWallObject;
import devp0tion.mechanics.objects.PumpObjectItem;
import devp0tion.mechanics.objects.TankValveObjectItem;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import necesse.engine.network.gameNetworkData.GNDItemMap;
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
     * (N8-3). Used by the engineering workbench (N1-1) and the manual pump (N3-1).
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

        // Tank valve: any mineral wall 1, any pipe 2 (N10-3). The crafted valve gets the tier of the
        // mineral wall used (N13-5 ②): the recipe's crafted event lists the items used, and the
        // listener stores the wall's tier in the result item. The display item says where the tier
        // comes from instead of showing one.
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.TANK_VALVE,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient(MechanicsIngredients.ANY_MINERAL_WALL, 1),
                        new Ingredient(MechanicsIngredients.ANY_PIPE, 2)
                },
                false,
                new GNDItemMap().setBoolean(TankValveObjectItem.TIER_FROM_WALL_KEY, true)
        ).onCrafted(TankValveObjectItem::onCrafted));

        // Glass block: vanilla glass 5 -> 1 (N10-4).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.GLASS_BLOCK,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient("glass", 5)
                }
        ));

        for (MineralTier tier : MineralTier.values()) {
            // Basic pipe: 1 bar -> 1 pipe (N9-1).
            Recipes.registerModRecipe(new Recipe(
                    BasicPipeObject.stringIDOf(tier),
                    1,
                    MechanicsTech.ENGINEERING,
                    new Ingredient[]{
                            new Ingredient(tier.getBarStringID(), 1)
                    }
            ));
            // Underground pipe: the basic pipe + 1 of the same bar -> 1 (N9-2, N10-1).
            Recipes.registerModRecipe(new Recipe(
                    UndergroundPipeObject.stringIDOf(tier),
                    1,
                    MechanicsTech.ENGINEERING,
                    new Ingredient[]{
                            new Ingredient(BasicPipeObject.stringIDOf(tier), 1),
                            new Ingredient(tier.getBarStringID(), 1)
                    }
            ));
        }

        // Pumps: crafted as valve pumps (N36-14). The recipe's item data goes to the crafted item and
        // to the crafting window's display item alike (vanilla Recipe), so the window shows the valve form.

        // Manual pump: any log 20, any stone 20 (N2-2, N3-1, N8-3).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.MANUAL_PUMP,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient("anylog", 20),
                        new Ingredient(ANY_STONE, 20)
                },
                false,
                PumpObjectItem.formData(PumpForm.VALVE)
        ));

        // Fire pump: copper bar 10, iron bar 10 (N9-3).
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.FIRE_PUMP,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient("copperbar", 10),
                        new Ingredient("ironbar", 10)
                },
                false,
                PumpObjectItem.formData(PumpForm.VALVE)
        ));

        // Advanced fire pump: fire pump 1, demonic bar 10, iron bar 10 (N9-3). The fire pump of
        // either form (N36-66): vanilla ingredients match the item only.
        Recipes.registerModRecipe(new Recipe(
                MechanicsObjects.ADVANCED_FIRE_PUMP,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient(MechanicsObjects.FIRE_PUMP, 1),
                        new Ingredient("demonicbar", 10),
                        new Ingredient("ironbar", 10)
                },
                false,
                PumpObjectItem.formData(PumpForm.VALVE)
        ));

        // Wrench: iron bar 10 -> 1 (N9-4).
        Recipes.registerModRecipe(new Recipe(
                MechanicsWrenchItem.STRING_ID,
                1,
                MechanicsTech.ENGINEERING,
                new Ingredient[]{
                        new Ingredient("ironbar", 10)
                }
        ));
    }

}
