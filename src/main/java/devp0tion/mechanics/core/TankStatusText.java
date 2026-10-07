package devp0tion.mechanics.core;

/**
 * The tank state as text, shown in the tank controller's window and in the tooltip when the cursor
 * is over a tank's interior (5-4, 6-2, 6-9): {@code <fluid name> <current>/<max>}, without brackets;
 * an empty tank shows only "비어 있음" / "Empty" (N31-7). Amounts are in fluid units
 * ({@link FluidUnits}). The texts come localized from the game, as the fluid name does.
 */
public final class TankStatusText {

    private TankStatusText() {
    }

    /**
     * @param fluidName the stored fluid's display name, or {@code null} when the tank is empty
     * @param current   the stored amount
     * @param max       the tank's capacity
     * @param emptyText the text of an empty tank, "비어 있음" / "Empty"
     */
    public static String format(String fluidName, int current, int max, String emptyText) {
        if (fluidName == null) {
            // An empty tank: only "비어 있음" / "Empty", no amounts (N31-7).
            return emptyText;
        }
        return fluidName + " " + current + "/" + max;
    }

}
