package devp0tion.mechanics.registry;

import devp0tion.mechanics.items.MechanicsWrenchItem;
import necesse.engine.registries.ItemRegistry;

/**
 * Item registration (items that are not object items).
 */
public final class MechanicsItems {

    private MechanicsItems() {
    }

    public static void load() {
        // The wrench (12-8). A negative broker value makes the game derive it from the recipe.
        ItemRegistry.registerItem(MechanicsWrenchItem.STRING_ID, new MechanicsWrenchItem(), -1f, true);
    }

}
