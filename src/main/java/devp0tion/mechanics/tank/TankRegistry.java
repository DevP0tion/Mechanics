package devp0tion.mechanics.tank;

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
 *     <li>Both sides: finds the tank whose interior holds a tile (hover tooltip, fluid rendering)
 *     and the tank a valve belongs to.</li>
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
     * The storage of the recognized tank whose border holds the valve at ({@code tileX},
     * {@code tileY}), or {@code null} when there is none. Server only (clients hold no fluid).
     * TODO(design): when the valve is in the border of two recognized tanks (possible when a change
     * other than placing the valve completes the second tank; placing it there is rejected, N11-1),
     * which tank it serves is undecided: it serves none.
     */
    public static TankStorage findValveTank(Level level, int tileX, int tileY) {
        TankStorage found = null;
        for (TankControllerObjectEntity controller : getControllers(level)) {
            TankBounds bounds = controller.getTankBounds();
            if (bounds == null || !bounds.isOnBorder(tileX, tileY) || bounds.isCorner(tileX, tileY)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = controller.getStorage();
        }
        return found;
    }

}
