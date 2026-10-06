package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankRegions;
import devp0tion.mechanics.objects.TankControllerObject;
import necesse.engine.registries.LevelDataRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.regionSystem.Region;
import necesse.level.maps.regionSystem.RegionManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the regions each recognized tank spans loaded together (N15-6, D5): while any region of a
 * tank is kept loaded by something else (a player nearby, a settlement), all its regions stay
 * loaded; tanks sharing a region are kept as one group ({@link TankRegions}). This is the vanilla
 * settlement mechanism ({@code ServerSettlementData.ensureRegionsLoaded}): every tick, each region of
 * a kept group gets {@code unloadRegionBuffer.keepLoaded(1)}, unloaded ones are loaded (never
 * generated), and the group saves its region files together ({@code syncSaveFileBuffer}).
 *
 * <p>Level data of server levels, saved with the level, so the tanks are known while their
 * controllers' regions are unloaded: walking up to any region of a tank loads the rest. Controllers
 * register their recognized tank ({@link #setTank}); an entry whose controller tile is loaded but
 * holds no controller any more is dropped.
 */
public class TankRegionsLevelData extends LevelData {

    /** Registry stringID and level data key; must stay the same for saved worlds. */
    public static final String KEY = "mechanicstankregions";

    /** Seconds a region counts as kept by others after its last keep (the settlement value). */
    private static final int KEEP_SECONDS = 1;

    /** Controller tile -> the bounds of its recognized tank. */
    private final Map<GridPos, TankBounds> tanks = new LinkedHashMap<>();

    public TankRegionsLevelData() {
    }

    /** Registers the level data type (mod init). */
    public static void register() {
        LevelDataRegistry.registerLevelData(KEY, TankRegionsLevelData.class);
    }

    /** The level's data, created when {@code create} is set; {@code null} on clients. */
    public static TankRegionsLevelData get(Level level, boolean create) {
        if (level == null || !level.isServer()) {
            return null;
        }
        LevelData data = level.getLevelData(KEY);
        if (data instanceof TankRegionsLevelData) {
            return (TankRegionsLevelData) data;
        }
        if (!create) {
            return null;
        }
        TankRegionsLevelData created = new TankRegionsLevelData();
        level.addLevelData(KEY, created);
        return created;
    }

    /** The recognized tank of the controller at the tile, or {@code null} when it has none. */
    void setTank(int controllerX, int controllerY, TankBounds bounds) {
        GridPos controller = new GridPos(controllerX, controllerY);
        if (bounds == null) {
            tanks.remove(controller);
        } else {
            tanks.put(controller, bounds);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isServer() || tanks.isEmpty()) {
            return;
        }
        final RegionManager regions = level.regionManager;
        List<Collection<GridPos>> tankRegions = new ArrayList<>();
        Iterator<Map.Entry<GridPos, TankBounds>> iterator = tanks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GridPos, TankBounds> entry = iterator.next();
            GridPos controller = entry.getKey();
            if (level.isTileWithinBounds(controller.x, controller.y) && regions.isTileLoaded(controller.x, controller.y)
                    && !(level.getObject(controller.x, controller.y) instanceof TankControllerObject)) {
                iterator.remove();
                continue;
            }
            tankRegions.add(TankRegions.regionsOf(entry.getValue(), RegionManager.REGION_SIZE));
        }
        // Kept by others: loaded, and kept loaded within the last second by something else. Regions
        // only kept by this rule count as unkept one tick after their last keep, so a group unloads
        // once nothing else keeps it.
        List<Set<GridPos>> groups = TankRegions.groupsToKeepLoaded(tankRegions, position -> {
            Region region = regions.isRegionWithinBounds(position.x, position.y)
                    ? regions.getRegion(position.x, position.y, false) : null;
            return region != null && !region.unloadRegionBuffer.shouldUnload(KEEP_SECONDS);
        });
        for (Set<GridPos> group : groups) {
            Region first = null;
            for (GridPos position : group) {
                if (!regions.isRegionWithinBounds(position.x, position.y)) {
                    continue;
                }
                // Loads the region if needed; never generates one (a tank's regions exist already).
                regions.ensureRegionIsLoadedButDontGenerate(position.x, position.y);
                Region region = regions.getRegion(position.x, position.y, false);
                if (region == null) {
                    continue;
                }
                region.unloadRegionBuffer.keepLoaded(KEEP_SECONDS);
                if (first == null) {
                    first = region;
                } else {
                    region.syncSaveFileBuffer(first);
                }
            }
        }
    }

    @Override
    public boolean shouldSave() {
        return !tanks.isEmpty();
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        for (Map.Entry<GridPos, TankBounds> entry : tanks.entrySet()) {
            SaveData tank = new SaveData("TANK");
            tank.addInt("controllerX", entry.getKey().x);
            tank.addInt("controllerY", entry.getKey().y);
            tank.addInt("x", entry.getValue().x);
            tank.addInt("y", entry.getValue().y);
            tank.addInt("width", entry.getValue().outerWidth);
            tank.addInt("height", entry.getValue().outerHeight);
            save.addSaveData(tank);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        tanks.clear();
        for (LoadData tank : save.getLoadDataByName("TANK")) {
            tanks.put(new GridPos(tank.getInt("controllerX", 0, false), tank.getInt("controllerY", 0, false)),
                    new TankBounds(tank.getInt("x", 0, false), tank.getInt("y", 0, false),
                            tank.getInt("width", 0, false), tank.getInt("height", 0, false)));
        }
    }

}
