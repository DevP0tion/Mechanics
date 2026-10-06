package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.TankValve;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

/**
 * A tank valve's state (2-1, 2-3): the pipe network endpoint ({@link TankValve}) that passes
 * incoming fluid automatically into its tank (N7-3).
 *
 * <ul>
 *     <li>Normally on; off while any wire on its tile carries a signal (N11-3). Updated from
 *     {@code onWireUpdate} and whenever the valve is used.</li>
 *     <li>Its tank is the recognized tank whose border holds the valve
 *     ({@link TankRegistry#findValveTank}).</li>
 * </ul>
 * TODO(game): pipes do not exist yet (4th implementation round, N11-5), so nothing delivers fluid
 * to the valve yet; the pipe network will use {@link #getValve()} as its endpoint.
 * TODO(design): automatic output from the valve is TODO (N7-3).
 */
public class TankValveObjectEntity extends ObjectEntity {

    /** Object entity type; must stay the same for saved worlds. */
    public static final String TYPE = "tankvalve";

    private final TankValve valve = new TankValve();

    public TankValveObjectEntity(Level level, int tileX, int tileY) {
        super(level, TYPE, tileX, tileY);
        // Nothing to save: the on/off state comes from the wires, the tank from the structure.
        shouldSave = false;
    }

    @Override
    public void init() {
        super.init();
        updateWireSignal();
    }

    /** Reads the wire signal on the valve's tile (N11-3). */
    public void updateWireSignal() {
        valve.applyWireSignal(getLevel().wireManager.isWireActiveAny(tileX, tileY));
    }

    /** Whether the valve is on (no wire signal, N11-3). */
    public boolean isOn() {
        return valve.isEnabled();
    }

    /** The core valve, linked to the tank it belongs to now. Server only (clients hold no fluid). */
    public TankValve getValve() {
        updateWireSignal();
        valve.setTank(TankRegistry.findValveTank(getLevel(), tileX, tileY));
        return valve;
    }

}
