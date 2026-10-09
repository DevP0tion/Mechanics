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
 *     <li>{@link #PUMP_SIDE}: on a basic pipe or a valve, toward a side of a pump that cannot be
 *     linked (N36-55, N36-61; {@link PipeGrid.Check#PUMP_SIDE_CLOSED_NEIGHBOUR}).</li>
 * </ul>
 * A tile with no part the wrench acts on shows no reason (the click there does nothing): no tooltip
 * appears there at all (N30-7), so the protected and out-of-range reasons are shown only over a part
 * the wrench acts on. On a pump's own tile, a side that cannot be linked shows no reason either, like
 * a wall (N36-45, {@link PipeGrid.Check#PUMP_SIDE_CLOSED}). Linking a valve of another fluid to a pump
 * is no longer refused (N36-26, N36-27).
 * Game independent, so it is tested by the plain test runner.
 */
public enum WrenchRefusal {

    PROTECTED("mechanicswrenchprotected"),
    OUT_OF_RANGE("mechanicswrenchoutofrange"),
    NOT_LOADED("mechanicswrenchnotloaded"),
    NOTHING_TO_LINK("mechanicswrenchnothingtolink"),
    NO_VERTICAL_LINK("mechanicswrenchnovertical"),
    PUMP_SIDE("mechanicswrenchpumpside");

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
            case PUMP_SIDE_CLOSED_NEIGHBOUR:
                return PUMP_SIDE;
            case PUMP_SIDE_CLOSED:
                // On the pump's own tile: nothing happens, like a wall, and no reason is shown (N36-45).
                return null;
            default:
                // OK, and OCCUPIED, which the wrench never gets.
                return null;
        }
    }

}
