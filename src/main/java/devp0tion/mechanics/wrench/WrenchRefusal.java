package devp0tion.mechanics.wrench;

import devp0tion.mechanics.core.PipeGrid;

/**
 * Why a right click of the engineering wrench would be refused (N30-1). The refusal itself shows no
 * message; the tooltip previews the reason while the player points at the tile. Every refusal path
 * of the right click (side and middle):
 *
 * <ul>
 *     <li>{@link #PROTECTED}: the tile is protected (the item's interact check).</li>
 *     <li>{@link #OUT_OF_RANGE}: the tile is beyond the place range (the item's interact check).</li>
 *     <li>{@link #NOT_LOADED}: the tile toward the side (or the tile itself) is outside the world or
 *     in a region that was never generated, so it cannot be loaded ({@link PipeGrid.Check#NOT_LOADED}).</li>
 *     <li>{@link #NOTHING_TO_LINK}: the part the click acts on is not in the pipe engine
 *     ({@link PipeGrid.Check#NOTHING_THERE} toward a side).</li>
 *     <li>{@link #NO_VERTICAL_LINK}: a middle click on a tile with no basic pipe, valve or
 *     underground pipe, i.e. a lone pump, which has no vertical link (9-9;
 *     {@link PipeGrid.Check#NOTHING_THERE} in the middle).</li>
 *     <li>{@link #DIFFERENT_SOURCE_FLUID}: linking a valve to a pump while the valve's tank holds
 *     another fluid than the pump's other sources (N16-3).</li>
 * </ul>
 * A tile with no part the wrench acts on shows no reason (the click there does nothing).
 * Game independent, so it is tested by the plain test runner.
 */
public enum WrenchRefusal {

    PROTECTED("mechanicswrenchprotected"),
    OUT_OF_RANGE("mechanicswrenchoutofrange"),
    NOT_LOADED("mechanicswrenchnotloaded"),
    NOTHING_TO_LINK("mechanicswrenchnothingtolink"),
    NO_VERTICAL_LINK("mechanicswrenchnovertical"),
    DIFFERENT_SOURCE_FLUID("mechanicswrenchsourcefluid");

    /** The text's key in the {@code [ui]} section of the locale files. */
    public final String localeKey;

    WrenchRefusal(String localeKey) {
        this.localeKey = localeKey;
    }

    /**
     * The reason of an engine check result: {@code null} when the click would go through.
     *
     * @param middle whether the click is on the tile's middle (vertical link) rather than a side
     */
    public static WrenchRefusal of(PipeGrid.Check check, boolean middle) {
        if (check == null) {
            return null;
        }
        switch (check) {
            case NOT_LOADED:
                return NOT_LOADED;
            case NOTHING_THERE:
                return middle ? NO_VERTICAL_LINK : NOTHING_TO_LINK;
            case DIFFERENT_SOURCE_FLUID:
                return DIFFERENT_SOURCE_FLUID;
            default:
                // OK, and OCCUPIED, which the wrench never gets.
                return null;
        }
    }

}
