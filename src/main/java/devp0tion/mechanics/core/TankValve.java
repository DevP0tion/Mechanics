package devp0tion.mechanics.core;

/**
 * A tank valve as a pipe network endpoint (2-3, N7-3).
 *
 * <ul>
 *     <li>Accepts incoming fluid automatically into its tank's storage, which lives in the
 *     controller (5-9). Without a recognized tank it accepts nothing.</li>
 *     <li>Can be switched on and off by wire ({@link #setEnabled}). A new valve is on: by default
 *     it accepts incoming fluid (N7-3).</li>
 *     <li>A pump attached directly to a valve pulls from its tank instead (11-1 ②, 11-5); that is
 *     the pump's {@link Pump#setSource source}, not a destination.</li>
 * </ul>
 *
 * <p>TODO(design): automatic output from the valve is TODO (N7-3).
 * <p>TODO(design): which wire signal state switches the valve off is undecided (N7-3 only says
 * the wire switches it on and off); the game maps the signal to {@link #setEnabled}.
 * <p>TODO(design): tanks may share walls (N8-1), so a valve can sit in the border of two tanks;
 * which tank it serves is undecided. The game decides which storage to pass to {@link #setTank}.
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
