package devp0tion.mechanics;

import devp0tion.mechanics.registry.MechanicsContainers;
import devp0tion.mechanics.registry.MechanicsIngredients;
import devp0tion.mechanics.registry.MechanicsObjects;
import devp0tion.mechanics.registry.MechanicsRecipes;
import devp0tion.mechanics.registry.MechanicsTech;
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
    }

    public void initResources() {
        // Object textures are loaded by each object's loadTextures(); nothing else to load yet.
    }

    public void postInit() {
        MechanicsRecipes.load();
    }

}
