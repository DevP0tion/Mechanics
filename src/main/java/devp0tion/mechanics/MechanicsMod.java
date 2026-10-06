package devp0tion.mechanics;

import devp0tion.mechanics.pipe.PipeSystem;
import devp0tion.mechanics.pipe.UndergroundPipeLayer;
import devp0tion.mechanics.registry.MechanicsContainers;
import devp0tion.mechanics.registry.MechanicsIngredients;
import devp0tion.mechanics.registry.MechanicsItems;
import devp0tion.mechanics.registry.MechanicsObjects;
import devp0tion.mechanics.registry.MechanicsPackets;
import devp0tion.mechanics.registry.MechanicsRecipes;
import devp0tion.mechanics.registry.MechanicsTech;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import devp0tion.mechanics.tank.TankRegionsLevelData;
import necesse.engine.modLoader.annotations.ModEntry;

/**
 * Mod entry point. Registration order matters: anything that other content uses (recipe techs,
 * the underground pipe layer) is registered before the content that uses it.
 */
@ModEntry
public class MechanicsMod {

    public void init() {
        // Ingredient groups first: object items tag themselves with them when they are created.
        MechanicsIngredients.load();
        MechanicsTech.load();
        // The underground pipes' object layer, before the underground pipe objects (9-1).
        UndergroundPipeLayer.register();
        MechanicsObjects.load();
        MechanicsItems.load();
        MechanicsContainers.load();
        MechanicsPackets.load();
        // Keeps the regions of each recognized tank loaded together (N15-6).
        TankRegionsLevelData.register();
        // The level's pipe grid and the underground pipes' state (N7-1, N14-4).
        PipeSystem.register();
        // Placement rejection inside recognized tanks (N16-2); objects also go through
        // TankInteriorPatches.
        TankInteriorPlacement.register();
    }

    public void initResources() {
        // Object and item textures are loaded by each object's and item's own load methods.
    }

    public void postInit() {
        MechanicsRecipes.load();
    }

}
