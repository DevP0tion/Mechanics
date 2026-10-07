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
 *     rebuilt with fewer cells or lower-tier walls or valves), the excess is lost at once (N11-2,
 *     N13-4), even when that smaller tank only exists for a moment while the tank is being rebuilt or
 *     extended.</li>
 *     <li>When the controller goes away the storage is released ({@link #release}): from that moment
 *     it takes and gives nothing and the valves that still point at it have no tank, so no push or
 *     pull reaches it before they look their tank up again (5-10, N19-2). A controller that only
 *     unloads goes away the same way: while it is unloaded its tank is dormant, its valves neither
 *     destinations nor sources (N21-2).</li>
 *     <li>The active state and the capacity are the controller's judgment: saved with the controller
 *     and restored when it loads ({@link #restoreJudgment}, N20-7, N22-7).</li>
 * </ul>
 */
public final class TankStorage extends LiquidStorage {

    private boolean active;
    private boolean released;

    /** A new controller's storage: empty and inactive until a structure is applied. */
    public TankStorage() {
        super(0);
    }

    /**
     * Applies the result of the controller's tank search: a valid tank activates the storage with
     * the tank's capacity and discards whatever no longer fits (N11-2); anything else (including
     * {@code null}) deactivates it and keeps the fluid and the last capacity (5-9, N13-4).
     *
     * @return the amount lost because the new capacity is smaller than the stored amount
     */
    public int applyStructure(TankValidation validation) {
        if (released) {
            return 0;
        }
        if (validation != null && validation.isValid()) {
            setCapacity(validation.getCapacity());
            active = true;
            // The only place where a tank loses fluid to a smaller capacity (N11-2). A capacity drop
            // that is only temporary while the tank is being rebuilt loses the excess too (N13-4).
            return discardExcess();
        }
        active = false;
        return 0;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Restores the judgment the controller saved (N20-7, N22-7): whether the tank is active and its
     * capacity, as they were when it was saved. Nothing is discarded: only a new judgment of the
     * structure loses fluid ({@link #applyStructure}, N11-2). A released storage stays inactive.
     */
    public void restoreJudgment(boolean active, int capacity) {
        if (released) {
            return;
        }
        setCapacity(capacity);
        this.active = active;
    }

    /**
     * The controller holding this storage is gone (5-10, N19-2): the storage is inactive for good, a
     * structure applied later does not activate it again, and valves treat it as no tank
     * ({@link TankValve#getTank}). The contents stay as they are (a controller whose region only
     * unloads was saved before).
     */
    public void release() {
        released = true;
        active = false;
    }

    /** Whether the controller holding this storage is gone ({@link #release}). */
    public boolean isReleased() {
        return released;
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
