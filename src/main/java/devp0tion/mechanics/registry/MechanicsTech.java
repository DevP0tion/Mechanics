package devp0tion.mechanics.registry;

import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.Tech;

/**
 * Recipe techs of the mod.
 */
public final class MechanicsTech {

    /**
     * Crafted at the engineering workbench. Every mod element is crafted here (13-1).
     * The display name is the [tech] entry in the locale files.
     */
    public static Tech ENGINEERING;

    private MechanicsTech() {
    }

    public static void load() {
        ENGINEERING = RecipeTechRegistry.registerTech("engineering", MechanicsObjects.ENGINEERING_WORKBENCH);
    }

}
