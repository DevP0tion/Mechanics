package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.core.TankValveRole;
import devp0tion.mechanics.core.TileBuckets;
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
 *     <li>Both sides: what a valve is for the tanks around it ({@link #valveRole}, N33-1).</li>
 * </ul>
 * Safe to read from the client's draw threads. An index by region bucket ({@link TileBuckets})
 * keeps the frequent server lookups from scanning every controller: the controllers near a changed
 * tile, and near every valve, every tick.
 */
public final class TankRegistry {

    private static final Map<Level, LevelControllers> LEVELS =
            Collections.synchronizedMap(new WeakHashMap<Level, LevelControllers>());

    /** The controllers of one level and their indexes. */
    private static final class LevelControllers {
        final Set<TankControllerObjectEntity> all = new CopyOnWriteArraySet<>();
        /** By region bucket of the controller tile; guarded by itself. */
        final TileBuckets<TankControllerObjectEntity> nearby = new TileBuckets<>();

        List<TankControllerObjectEntity> near(int x0, int y0, int x1, int y1) {
            synchronized (nearby) {
                return nearby.near(x0, y0, x1, y1);
            }
        }
    }

    private TankRegistry() {
    }

    private static LevelControllers of(Level level) {
        return level == null ? null : LEVELS.get(level);
    }

    static void add(TankControllerObjectEntity controller) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        LevelControllers controllers;
        synchronized (LEVELS) {
            controllers = LEVELS.get(level);
            if (controllers == null) {
                controllers = new LevelControllers();
                LEVELS.put(level, controllers);
            }
        }
        controllers.all.add(controller);
        synchronized (controllers.nearby) {
            controllers.nearby.add(controller.tileX, controller.tileY, controller);
        }
    }

    static void remove(TankControllerObjectEntity controller) {
        LevelControllers controllers = of(controller.getLevel());
        if (controllers == null) {
            return;
        }
        controllers.all.remove(controller);
        synchronized (controllers.nearby) {
            controllers.nearby.remove(controller.tileX, controller.tileY, controller);
        }
    }

    private static boolean isGone(TankControllerObjectEntity controller) {
        return controller.removed() || controller.isDisposed();
    }

    /** The live controllers of a level. */
    public static List<TankControllerObjectEntity> getControllers(Level level) {
        LevelControllers controllers = of(level);
        if (controllers == null || controllers.all.isEmpty()) {
            return Collections.emptyList();
        }
        List<TankControllerObjectEntity> result = new ArrayList<>(controllers.all.size());
        for (TankControllerObjectEntity controller : controllers.all) {
            if (isGone(controller)) {
                remove(controller);
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
        LevelControllers controllers = of(level);
        if (controllers == null) {
            return;
        }
        int reach = TankStructure.REACH;
        for (TankControllerObjectEntity controller : controllers.near(tileX - reach, tileY - reach,
                tileX + reach, tileY + reach)) {
            if (Math.abs(controller.tileX - tileX) <= reach && Math.abs(controller.tileY - tileY) <= reach) {
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
     * The bounds of the recognized tank whose interior holds the tile, or {@code null}. The same
     * match as {@link #findTankWithInterior}, returning the bounds it read: a client's view can
     * change on the network thread while a draw thread asks, so the caller keeps one consistent
     * snapshot (the glass block's ceiling drawing, N34-1).
     */
    public static TankBounds findInteriorBounds(Level level, int tileX, int tileY) {
        for (TankControllerObjectEntity controller : getControllers(level)) {
            TankBounds bounds = controller.getTankBounds();
            if (bounds != null && bounds.isInterior(tileX, tileY)) {
                return bounds;
            }
        }
        return null;
    }

    /**
     * What the valve at ({@code tileX}, {@code tileY}) is for the tanks around it (N33-1,
     * {@link TankValveRole}): the controller of the tank it works for, or a plain wall in a wall
     * shared by two recognized tanks. The candidates are the live controllers whose kept tank
     * (N13-3) has the tile in its border; a valve is in the border of a tank only within
     * {@link TankStructure#REACH} of its controller. Both sides: clients read the synced kept tanks
     * and recognition, so placement checks that need the tank agree with the server.
     *
     * <p>N33-13: a tank whose controller is not loaded (dormant, N21-2, N22-7) is not in the
     * registry, so it is not seen here: it does not make the wall shared, and the valve in a wall it
     * shares with a loaded recognized tank is no plain wall and works for that tank.
     */
    public static TankValveRole<TankControllerObjectEntity> valveRole(Level level, int tileX, int tileY) {
        LevelControllers controllers = of(level);
        List<TankControllerObjectEntity> candidates = Collections.emptyList();
        if (controllers != null) {
            int reach = TankStructure.REACH;
            candidates = new ArrayList<>();
            for (TankControllerObjectEntity controller : controllers.near(tileX - reach, tileY - reach,
                    tileX + reach, tileY + reach)) {
                if (isGone(controller)) {
                    remove(controller);
                } else {
                    candidates.add(controller);
                }
            }
        }
        return TankValveRole.of(tileX, tileY, candidates, TankControllerObjectEntity::getKeptTank,
                TankControllerObjectEntity::isRecognized);
    }

    /**
     * A controller stopped keeping {@code tank} (it took another tank, or it is gone): the controller
     * cells of that tank may now belong to other tanks, so every controller close enough to have such
     * a cell in its border searches its tank again on its next tick (N13-3). Server only.
     */
    static void onTankReleased(Level level, TankBounds tank) {
        if (level == null || !level.isServer() || tank == null) {
            return;
        }
        LevelControllers controllers = of(level);
        if (controllers == null) {
            return;
        }
        int reach = TankStructure.REACH;
        for (TankControllerObjectEntity controller : controllers.near(tank.x - reach, tank.y - reach,
                tank.getMaxX() + reach, tank.getMaxY() + reach)) {
            if (controller.tileX >= tank.x - reach && controller.tileX <= tank.getMaxX() + reach
                    && controller.tileY >= tank.y - reach && controller.tileY <= tank.getMaxY() + reach) {
                controller.markStructureChanged();
            }
        }
    }

}
