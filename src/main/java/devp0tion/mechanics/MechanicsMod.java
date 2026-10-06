package devp0tion.mechanics;

import devp0tion.mechanics.registry.MechanicsContainers;
import devp0tion.mechanics.registry.MechanicsIngredients;
import devp0tion.mechanics.registry.MechanicsObjects;
import devp0tion.mechanics.registry.MechanicsRecipes;
import devp0tion.mechanics.registry.MechanicsTech;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import devp0tion.mechanics.tank.TankRegionsLevelData;
import necesse.engine.modLoader.annotations.ModEntry;

/**
 * Mod entry point. Registration order matters: anything that other content uses (recipe techs)
 * is registered before the content that uses it.
 */
@ModEntry
public class MechanicsMod {

    public void init() {
        // Ingredient groups first: object items tag themselves with them when they are created.
        MechanicsIngredients.load();
        MechanicsTech.load();
        MechanicsObjects.load();
        MechanicsContainers.load();
        // Keeps the regions of each recognized tank loaded together (N15-6).
        TankRegionsLevelData.register();
        // Placement rejection inside recognized tanks (N16-2); objects also go through
        // TankInteriorPatches.
        TankInteriorPlacement.register();
    }

    public void initResources() {
        // Object textures are loaded by each object's loadTextures(); nothing else to load yet.
    }

    public void postInit() {
        MechanicsRecipes.load();
    }

}
