package devp0tion.mechanics.core;

/**
 * A tank valve as a pipe network endpoint (2-3, N7-3).
 *
 * <ul>
 *     <li>Accepts incoming fluid automatically into its tank's storage, which lives in the
 *     controller (5-9). Without a recognized tank it accepts nothing.</li>
 *     <li>Normally on: by default it accepts incoming fluid (N7-3). While it receives a wire
 *     signal it is off and accepts nothing (N11-3, {@link #applyWireSignal}).</li>
 *     <li>A pump linked directly to a valve pulls from its tank (11-1 ②, 11-5, N16-3); that is one
 *     of the pump's sources, not a destination.</li>
 *     <li>Link flags ({@link LinkFlags}): one per side (basic pipes and pumps next to it) and the
 *     vertical one (the underground pipe on its tile, 9-9, 13-5). Toggled by the wrench and kept
 *     when the other side is removed (N16-4).</li>
 *     <li>It belongs to the tank that recognized it first (N13-3): another tank completed later
 *     around it does not take it, and is no tank while the valve is in its border (N15-3). The game
 *     passes that first tank's storage to {@link #setTank}, see
 *     {@link TankStructure#effectiveValveOwner}.</li>
 * </ul>
 *
 * <p>TODO(design): automatic output from the valve is TODO (N7-3).
 * <p>TODO(design): whether the wire signal also stops a linked pump from pulling through the valve
 * is not decided; the signal only switches the valve's automatic input (N7-3, N11-3), and a pump
 * pulls from the tank regardless.
 */
public final class TankValve {

    private TankStorage tank;
    private boolean enabled = true;
    private int links = LinkFlags.ALL_OPEN;

    /**
     * The tank this valve belongs to, or {@code null} when it is not part of a recognized tank. A
     * released tank (its controller is gone, {@link TankStorage#release}) is none, also before the game
     * looks the valve's tank up again.
     */
    public TankStorage getTank() {
        return tank == null || tank.isReleased() ? null : tank;
    }

    public void setTank(TankStorage tank) {
        this.tank = tank;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Wire control (N7-3). A disabled valve accepts nothing. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Applies the wire signal on the valve's tile: a signal switches the valve off, no signal
     * leaves it on (N11-3).
     */
    public void applyWireSignal(boolean signal) {
        setEnabled(!signal);
    }

    /** The link flags ({@link LinkFlags}). */
    public int getLinks() {
        return links;
    }

    /** Restores saved link flags (before the valve is added to a grid). */
    public void setLinks(int links) {
        this.links = LinkFlags.sanitize(links);
    }

    public boolean isSideOpen(Direction direction) {
        return LinkFlags.isSideOpen(links, direction);
    }

    public boolean isVerticalOpen() {
        return LinkFlags.isVerticalOpen(links);
    }

    void setSideOpen(Direction direction, boolean open) {
        links = LinkFlags.withSide(links, direction, open);
    }

    void setVerticalOpen(boolean open) {
        links = LinkFlags.withVertical(links, open);
    }

    /** The fluid stored in its tank (also while the tank is inactive), or {@code null}. */
    public FluidType getStoredFluid() {
        TankStorage current = getTank();
        return current == null ? null : current.getFluid();
    }

    /** How much of {@code type} the valve would pass into its tank now. */
    public int getSpaceFor(FluidType type) {
        TankStorage current = getTank();
        return enabled && current != null ? current.getSpaceFor(type) : 0;
    }

    /** Passes up to {@code amount} of {@code type} into the tank; returns the amount taken. */
    int insert(FluidType type, int amount) {
        TankStorage current = getTank();
        return enabled && current != null ? current.insert(type, amount) : 0;
    }

    @Override
    public String toString() {
        return "TankValve[" + (enabled ? "on" : "off") + ", " + getTank() + "]";
    }

}
