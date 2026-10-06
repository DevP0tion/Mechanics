package devp0tion.mechanics.core;

/**
 * A tank valve as a pipe network endpoint (2-3, N7-3).
 *
 * <ul>
 *     <li>Accepts incoming fluid automatically into its tank's storage, which lives in the
 *     controller (5-9). Without a recognized tank it accepts nothing.</li>
 *     <li>Normally on: by default it accepts incoming fluid (N7-3). While it receives a wire
 *     signal it is off and accepts nothing (N11-3, {@link #applyWireSignal}).</li>
 *     <li>A pump attached directly to a valve pulls from its tank instead (11-1 ②, 11-5); that is
 *     the pump's {@link Pump#setSource source}, not a destination.</li>
 *     <li>It belongs to the tank that recognized it first (N13-3): another tank completed later
 *     around it does not take it, and is no tank while the valve is in its border (N15-3). The game
 *     passes that first tank's storage to {@link #setTank}, see
 *     {@link TankStructure#effectiveValveOwner}.</li>
 * </ul>
 *
 * <p>TODO(design): automatic output from the valve is TODO (N7-3).
 */
public final class TankValve {

    private TankStorage tank;
    private boolean enabled = true;

    /** The tank this valve belongs to, or {@code null} when it is not part of a recognized tank. */
    public TankStorage getTank() {
        return tank;
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

    /** How much of {@code type} the valve would pass into its tank now. */
    public int getSpaceFor(FluidType type) {
        return enabled && tank != null ? tank.getSpaceFor(type) : 0;
    }

    /** Passes up to {@code amount} of {@code type} into the tank; returns the amount taken. */
    int insert(FluidType type, int amount) {
        return enabled && tank != null ? tank.insert(type, amount) : 0;
    }

    @Override
    public String toString() {
        return "TankValve[" + (enabled ? "on" : "off") + ", " + tank + "]";
    }

}
