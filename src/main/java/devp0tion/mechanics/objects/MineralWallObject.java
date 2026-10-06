package devp0tion.mechanics.objects;

import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.registry.MechanicsIngredients;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.WallObject;

import java.awt.Color;
import java.util.Locale;

/**
 * Mineral wall (광물 벽): a wall made of one of the 11 vanilla bars (5-12, 8-2). Mineral walls form
 * the border of multiblock tanks; the lowest tier sets the tank's capacity multiplier (N4-2).
 *
 * <p>Only the wall itself is registered: unlike {@link WallObject#registerWallObjects}, no door or
 * window variants (they are not part of the design).
 *
 * <ul>
 *     <li>Mined with a pickaxe of at least the tier of that mineral's pickaxe (N5-1, N5-2).</li>
 *     <li>Name: the vanilla bar item's name + "wall" ({@code [object] mineralwall} in the locale
 *     files), so it follows the game's own bar names in every language.</li>
 *     <li>Texture: {@code objects/<stringID>.png} in the vanilla wall sheet layout, item icon
 *     {@code items/<stringID>.png} (drawn by {@code tools/textures/draw_mineral_walls.py}).</li>
 *     <li>Belongs to the "any mineral wall" ingredient group
 *     ({@link MechanicsIngredients#ANY_MINERAL_WALL}).</li>
 * </ul>
 */
public class MineralWallObject extends WallObject {

    private final MineralTier tier;

    public MineralWallObject(MineralTier tier, String stringID, Color mapColor) {
        // "walloutlines" is the vanilla outline overlay that the plain registerWallObjects overloads use.
        super(stringID, "walloutlines", mapColor, tier.getWallToolTier(), ToolType.PICKAXE);
        this.tier = tier;
        // Every mineral wall counts as "any mineral wall" in recipes (N10-2, N10-3).
        addGlobalIngredient(MechanicsIngredients.ANY_MINERAL_WALL);
    }

    /** The wall's stringID (also its item's and texture's name): {@code <mineral>wall}, e.g. {@code copperwall}. */
    public static String stringIDOf(MineralTier tier) {
        return tier.name().toLowerCase(Locale.ROOT) + "wall";
    }

    /**
     * Registers the wall object (and its item) for a tier.
     *
     * @return the object ID
     */
    public static int register(MineralTier tier, Color mapColor) {
        String stringID = stringIDOf(tier);
        // A negative broker value makes the game derive it from the recipe (1x the bar's value),
        // as the vanilla walls do.
        return ObjectRegistry.registerObject(stringID, new MineralWallObject(tier, stringID, mapColor), -1f, true);
    }

    public MineralTier getMineralTier() {
        return tier;
    }

    @Override
    public GameMessage getNewLocalization() {
        return new LocalMessage("object", "mineralwall", "bar", new LocalMessage("item", tier.getBarStringID()));
    }

}
