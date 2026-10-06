package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankStorage;
import devp0tion.mechanics.core.TankStructure;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The tank controllers of each loaded level (server and client levels separately). Controller
 * object entities add themselves when they are created and remove themselves when they go away.
 *
 * <ul>
 *     <li>Server: {@link #onTileChanged} is called for every object or floor tile change
 *     ({@link TankChangePatches}); the controllers close enough to be affected search their tank
 *     again on their next tick (5-1).</li>
 *     <li>Both sides: finds the tank whose interior holds a tile (hover tooltip, fluid rendering,
 *     the interior placement rule N16).</li>
 *     <li>Server: the tank a valve belongs to ({@link #findValveTank}, N13-3).</li>
 * </ul>
 * Safe to read from the client's draw threads.
 */
public final class TankRegistry {

    private static final Map<Level, Set<TankControllerObjectEntity>> CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<Level, Set<TankControllerObjectEntity>>());

    private TankRegistry() {
    }

    static void add(TankControllerObjectEntity controller) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        Set<TankControllerObjectEntity> set;
        synchronized (CONTROLLERS) {
            set = CONTROLLERS.get(level);
            if (set == null) {
                set = new CopyOnWriteArraySet<>();
                CONTROLLERS.put(level, set);
            }
        }
        set.add(controller);
    }

    static void remove(TankControllerObjectEntity controller) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        Set<TankControllerObjectEntity> set = CONTROLLERS.get(level);
        if (set != null) {
            set.remove(controller);
        }
    }

    /** The live controllers of a level. */
    public static List<TankControllerObjectEntity> getControllers(Level level) {
        Set<TankControllerObjectEntity> set = level == null ? null : CONTROLLERS.get(level);
        if (set == null || set.isEmpty()) {
            return Collections.emptyList();
        }
        List<TankControllerObjectEntity> result = new ArrayList<>(set.size());
        for (TankControllerObjectEntity controller : set) {
            if (controller.removed() || controller.isDisposed()) {
                set.remove(controller);
            } else {
                result.add(controller);
            }
        }
        return result;
    }

    /**
     * An object or the floor tile at ({@code tileX}, {@code tileY}) changed (5-1). On the server,
     * every controller within {@link TankStructure#REACH} searches its tank again on its next tick.
     */
    public static void onTileChanged(Level level, int tileX, int tileY) {
        if (level == null || !level.isServer()) {
            return;
        }
        Set<TankControllerObjectEntity> set = CONTROLLERS.get(level);
        if (set == null) {
            return;
        }
        for (TankControllerObjectEntity controller : set) {
            if (Math.abs(controller.tileX - tileX) <= TankStructure.REACH
                    && Math.abs(controller.tileY - tileY) <= TankStructure.REACH) {
                controller.markStructureChanged();
            }
        }
    }

    /** The recognized tank whose interior holds the tile, or {@code null}. */
    public static TankControllerObjectEntity findTankWithInterior(Level level, int tileX, int tileY) {
        for (TankControllerObjectEntity controller : getControllers(level)) {
            TankBounds bounds = controller.getTankBounds();
            if (bounds != null && bounds.isInterior(tileX, tileY)) {
                return controller;
            }
        }
        return null;
    }

    /**
     * The storage of the tank the valve at ({@code tileX}, {@code tileY}) belongs to, or
     * {@code null} when there is none. Server only (clients hold no fluid).
     *
     * <p>First come, first served (N13-3): the valve belongs to the controller that recognized it
     * first ({@code owner}, set by {@link TankControllerObjectEntity}), as long as that controller
     * keeps a tank with the valve in its border; while that tank is inactive its storage takes and
     * gives nothing. Another tank completed around the valve later never gets it, and is no tank while
     * the valve is in its border (N15-3).
     */
    public static TankStorage findValveTank(Level level, int tileX, int tileY, GridPos owner) {
        if (owner == null) {
            return null;
        }
        for (TankControllerObjectEntity controller : getControllers(level)) {
            if (controller.tileX == owner.x && controller.tileY == owner.y) {
                return TankStructure.ownsValve(controller.getKeptTank(), tileX, tileY) ? controller.getStorage() : null;
            }
        }
        return null;
    }

    /**
     * The controller of the tank the valve at ({@code tileX}, {@code tileY}) belongs to, or
     * {@code null} (N13-3). Both sides: clients read the synced owner and kept tanks, so placement
     * checks that need the tank's fluid (N17-1) agree with the server.
     */
    public static TankControllerObjectEntity findValveController(Level level, int tileX, int tileY, GridPos owner) {
        if (owner == null) {
            return null;
        }
        for (TankControllerObjectEntity controller : getControllers(level)) {
            if (controller.tileX == owner.x && controller.tileY == owner.y) {
                return TankStructure.ownsValve(controller.getKeptTank(), tileX, tileY) ? controller : null;
            }
        }
        return null;
    }

    /**
     * A controller stopped keeping {@code tank} (it took another tank, or it is gone): the valves
     * and the controller cells of that tank may now belong to other tanks, so every controller close
     * enough to have such a cell in its border searches its tank again on its next tick (N13-3).
     * Server only.
     */
    static void onTankReleased(Level level, TankBounds tank) {
        if (level == null || !level.isServer() || tank == null) {
            return;
        }
        Set<TankControllerObjectEntity> set = CONTROLLERS.get(level);
        if (set == null) {
            return;
        }
        int reach = TankStructure.REACH;
        for (TankControllerObjectEntity controller : set) {
            if (controller.tileX >= tank.x - reach && controller.tileX <= tank.getMaxX() + reach
                    && controller.tileY >= tank.y - reach && controller.tileY <= tank.getMaxY() + reach) {
                controller.markStructureChanged();
            }
        }
    }

}
