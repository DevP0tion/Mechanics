package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.FluidType;
import necesse.engine.localization.Localization;

/**
 * Display names of the fluids, from the {@code [fluid]} section of the locale files.
 */
public final class FluidNames {

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
            default:
                // TODO(design): the display names of the other fluids are undecided (numbers.md
                // table 4). The raw FluidType name (e.g. LAVA) is shown as a placeholder.
                return fluid.name();
        }
    }

}
