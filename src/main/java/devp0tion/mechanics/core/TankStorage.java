package devp0tion.mechanics.core;

/**
 * The fluid of a multiblock tank. It lives in the tank controller (5-9): the controller's game
 * object owns one instance and the tank's valves point at it ({@link TankValve#setTank}).
 * Destroying the controller loses the fluid (5-10), which is the game object dropping this storage.
 *
 * <ul>
 *     <li>One fluid at a time (12-7, {@link LiquidStorage}).</li>
 *     <li>The capacity comes from the recognized structure (N4-4, {@link TankStructure}).</li>
 *     <li>While the structure is not a valid tank (for example a wall was broken), the tank is
 *     inactive: it keeps its fluid and its last capacity (5-9) but takes and gives nothing until it
 *     is valid again.</li>
 *     <li>When a valid structure is applied whose capacity is below the stored amount (the tank was
 *     rebuilt with fewer cells or lower-tier walls), the excess is lost (N11-2).</li>
 * </ul>
 */
public final class TankStorage extends LiquidStorage {

    private boolean active;

    /** A new controller's storage: empty and inactive until a structure is applied. */
    public TankStorage() {
        super(0);
    }

    /**
     * Applies the result of the controller's tank search: a valid tank activates the storage with
     * the tank's capacity and discards whatever no longer fits (N11-2); anything else (including
     * {@code null}) deactivates it and keeps the fluid and the last capacity (5-9).
     *
     * @return the amount lost because the new capacity is smaller than the stored amount
     */
    public int applyStructure(TankValidation validation) {
        if (validation != null && validation.isValid()) {
            setCapacity(validation.getCapacity());
            active = true;
            // The only place where a tank loses fluid to a smaller capacity (N11-2).
            // TODO(design): a capacity drop that is only temporary while the tank is being rebuilt
            // (for example a lower-tier wall placed before the higher-tier one it replaces) also
            // loses the excess; whether that should be spared is undecided.
            return discardExcess();
        }
        active = false;
        return 0;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    protected boolean acceptsInput() {
        return active;
    }

    @Override
    protected boolean providesOutput() {
        return active;
    }

}
