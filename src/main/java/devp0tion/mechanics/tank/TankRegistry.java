package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankStorage;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.core.TileBuckets;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
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
 * Safe to read from the client's draw threads. Two indexes keep the frequent server lookups from
 * scanning every controller: by tile (the owner lookup of every valve, every tick) and by region
 * bucket ({@link TileBuckets}: the controllers near a changed tile).
 */
public final class TankRegistry {

    private static final Map<Level, LevelControllers> LEVELS =
            Collections.synchronizedMap(new WeakHashMap<Level, LevelControllers>());

    /** The controllers of one level and their indexes. */
    private static final class LevelControllers {
        final Set<TankControllerObjectEntity> all = new CopyOnWriteArraySet<>();
        /** By controller tile. */
        final Map<GridPos, TankControllerObjectEntity> byTile = new ConcurrentHashMap<>();
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
        controllers.byTile.put(new GridPos(controller.tileX, controller.tileY), controller);
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
        controllers.byTile.remove(new GridPos(controller.tileX, controller.tileY), controller);
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

    /** The live controller at the tile, or {@code null}. */
    private static TankControllerObjectEntity controllerAt(Level level, GridPos tile) {
        LevelControllers controllers = of(level);
        TankControllerObjectEntity controller = controllers == null ? null : controllers.byTile.get(tile);
        if (controller != null && isGone(controller)) {
            remove(controller);
            return null;
        }
        return controller;
    }

    /**
     * An object or the floor tile at ({@code tileX}, {@code tileY}) changed (5-1). On the server,
     * every controller within {@link TankStructure#REACH} searches its tank again on its next tick.
     * A controller whose tank another controller's contests (N29-8,
     * {@link TankControllerObjectEntity#isContested}) also does for a change within twice the reach,
     * where the other tank's cells are: the tank still valid is recognized once the other one is not.
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
        int contestReach = reach * 2;
        for (TankControllerObjectEntity controller : controllers.near(tileX - contestReach, tileY - contestReach,
                tileX + contestReach, tileY + contestReach)) {
            int dx = Math.abs(controller.tileX - tileX);
            int dy = Math.abs(controller.tileY - tileY);
            if (dx <= reach && dy <= reach || controller.isContested() && dx <= contestReach && dy <= contestReach) {
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
        TankControllerObjectEntity controller = findValveController(level, tileX, tileY, owner);
        return controller == null ? null : controller.getStorage();
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
        TankControllerObjectEntity controller = controllerAt(level, owner);
        return controller != null && TankStructure.ownsValve(controller.getKeptTank(), tileX, tileY) ? controller : null;
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
