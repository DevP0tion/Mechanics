package devp0tion.mechanics.core;

/**
 * The tank state as text, shown in the tank controller's window and in the tooltip when the cursor
 * is over a tank's interior (5-4, 6-2, 6-9): {@code <fluid name> <current>/<max>}, without brackets.
 * Amounts are in fluid units ({@link FluidUnits}).
 */
public final class TankStatusText {

    private TankStatusText() {
    }

    /**
     * @param fluidName the stored fluid's display name, or {@code null} when the tank is empty
     * @param current   the stored amount
     * @param max       the tank's capacity
     */
    public static String format(String fluidName, int current, int max) {
        if (fluidName == null) {
            // TODO(design): the text for an empty tank (no fluid, so no fluid name) is undecided;
            // only the amounts are shown.
            return current + "/" + max;
        }
        return fluidName + " " + current + "/" + max;
    }

}
