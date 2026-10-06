package devp0tion.mechanics.registry;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.GlobalIngredientRegistry;

/**
 * The mod's own ingredient groups (global ingredients, like the vanilla {@code anylog} and
 * {@code anystone}): an item belongs to a group when it is tagged with the group's stringID
 * ({@code addGlobalIngredient}). Registered in {@code init} before any object, because the
 * registry closes after {@code init} and recipes are built later, in {@code postInit}.
 * The display names are {@code [item] <stringID>} in the locale files.
 */
public final class MechanicsIngredients {

    /**
     * Any mineral wall (아무 광물 벽): every one of the 11 mineral walls, for the tank controller
     * (N10-2) and tank valve (N10-3) recipes.
     */
    public static final String ANY_MINERAL_WALL = "anymineralwall";

    /**
     * Any pipe (아무 파이프): basic and underground pipes of every bar (N10-3), for the tank valve
     * recipe. TODO(game): pipes do not exist yet (4th implementation round, N11-5), so no item is in
     * this group and the tank valve cannot be crafted until they are added.
     */
    public static final String ANY_PIPE = "anypipe";

    private MechanicsIngredients() {
    }

    public static void load() {
        GlobalIngredientRegistry.registerGlobalIngredient(ANY_MINERAL_WALL, new LocalMessage("item", ANY_MINERAL_WALL), null);
        GlobalIngredientRegistry.registerGlobalIngredient(ANY_PIPE, new LocalMessage("item", ANY_PIPE), null);
    }

}
