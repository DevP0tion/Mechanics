package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.FluidType;
import necesse.engine.localization.Localization;
import necesse.engine.registries.TileRegistry;
import necesse.level.gameTile.GameTile;

/**
 * Display names of the fluids (12-5, N32-4), in the game's language:
 *
 * <ul>
 *     <li>seawater and freshwater: 해수 / 담수, from the {@code [fluid]} section of the locale files
 *     (12-5);</li>
 *     <li>lava, slime, ooze, spirit water and quicksand: the names of their vanilla liquid tiles,
 *     read from the game's own tile localization when shown, so they follow the game language
 *     (N32-4);</li>
 *     <li>except ooze in a language whose locale file of the mod names it ({@code [fluid] ooze}): the
 *     vanilla Korean localization lacks ooze (the game shows "Ooze"), so the mod's Korean file names
 *     it 우즈 (N32-6); the other languages keep the vanilla name;</li>
 *     <li>crude oil, which has no vanilla tile: 원유 / Crude Oil, from the {@code [fluid]} section
 *     (N32-4).</li>
 * </ul>
 * An empty tank (or pipe) shows {@link #emptyText()}, "비어 있음" / "Empty" (N31-7).
 */
public final class FluidNames {

    /** The {@code [fluid]} key of ooze's name, in the Korean locale file only (N32-6). */
    static final String OOZE_KEY = "ooze";

    private FluidNames() {
    }

    /** The display name, or {@code null} for {@code null} (an empty tank). */
    public static String displayName(FluidType fluid) {
        if (fluid == null) {
            return null;
        }
        switch (fluid) {
            case SEAWATER:
                return Localization.translate("fluid", "seawater"); // 해수 (12-5)
            case FRESHWATER:
                return Localization.translate("fluid", "freshwater"); // 담수 (12-5)
            case CRUDE_OIL:
                return Localization.translate("fluid", "crudeoil"); // 원유 (N32-4)
            case OOZE:
                // 우즈 in Korean (N32-6), the vanilla tile's name in the languages the mod does not name it in.
                return Localization.getCurrentLang().translationExists("fluid", OOZE_KEY)
                        ? Localization.translate("fluid", OOZE_KEY) : vanillaTileName(fluid);
            default:
                return vanillaTileName(fluid);
        }
    }

    /** The name of the fluid's vanilla liquid tile in the game's language (N32-4). */
    private static String vanillaTileName(FluidType fluid) {
        GameTile tile = fluid.hasLiquidTile() ? TileRegistry.getTile(fluid.getLiquidTileStringID()) : null;
        // The raw constant name only if the game had no such tile.
        return tile == null ? fluid.name() : tile.getLocalization().translate();
    }

    /** The text of an empty tank: "비어 있음" / "Empty" (N31-7). */
    public static String emptyText() {
        return Localization.translate("fluid", "empty");
    }

}
