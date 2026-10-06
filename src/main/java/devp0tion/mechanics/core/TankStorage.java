package devp0tion.mechanics.core;

/**
 * The fluid of a multiblock tank. It lives in the tank controller (5-9): the controller's game
 * object owns one instance and the tank's valves point at it ({@link TankValve#setTank}).
 * Destroying the controller loses the fluid (5-10), which is the game object dropping this storage.
 *
 * <p>The capacity comes from the recognized structure (N4-4, {@link TankStructure}). While the
 * structure is not a valid tank (for example a wall was broken), the tank is inactive: it keeps its
 * fluid (5-9) but takes and gives nothing until it is valid again.
 *
 * <p>TODO(design): when a tank is rebuilt with a smaller capacity than its stored amount (fewer
 * cells or lower-tier walls), the fluid is kept and the tank takes nothing until it is under
 * capacity; nothing decides whether the excess should be lost instead.
 */
public final class TankStorage extends LiquidStorage {

    private boolean active;

    /** A new controller's storage: empty and inactive until a structure is applied. */
    public TankStorage() {
        super(0);
    }

    /**
     * Applies the result of the controller's tank search: a valid tank activates the storage with
     * the tank's capacity; anything else (including {@code null}) deactivates it and keeps the
     * fluid and the last capacity.
     */
    public void applyStructure(TankValidation validation) {
        if (validation != null && validation.isValid()) {
            setCapacity(validation.getCapacity());
            active = true;
        } else {
            active = false;
        }
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
