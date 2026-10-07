package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.CellKind;
import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankCell;
import devp0tion.mechanics.core.TankCellLookup;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.objects.GlassBlockObject;
import devp0tion.mechanics.objects.MineralWallObject;
import devp0tion.mechanics.objects.TankControllerObject;
import devp0tion.mechanics.objects.TankValveObject;
import devp0tion.mechanics.objects.TankValveObjectItem;
import devp0tion.mechanics.pipe.UndergroundPipeLayer;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;

/**
 * The game adapter of {@link TankCellLookup}: reads what is on a tile of a level for the tank rules
 * in {@link TankStructure}.
 *
 * <ul>
 *     <li>The kind comes from the base object layer (layer 0, where walls and blocks go, D1): no
 *     object is {@link CellKind#EMPTY}; mineral wall, tank controller, tank valve, glass block are
 *     their kinds; anything else is {@link CellKind#OTHER}.</li>
 *     <li>Mineral walls carry their tier; valves their tier and the controller they belong to
 *     ({@link TankValveObjectEntity}, N13-3, N13-5); controllers the tank they keep
 *     ({@link TankControllerObjectEntity}, N13-3).</li>
 *     <li>For the interior checks (N15-4): whether any other object layer holds something (wall and
 *     table decorations, carpets, every other registered layer) except the underground pipe layer
 *     ({@link #countsForInterior}), and whether the floor is a liquid tile.</li>
 *     <li>Natural growth (N20-1, N23-4, N29-5): whether the base layer object is of the game's grass
 *     kind, placed by a player or not ({@link TankInteriorPlacement#isNaturalGrowth}).</li>
 *     <li>Tiles outside the level or in a region that is not loaded read as {@code null} (something
 *     else). {@link #isLoaded} tells the tiles that are not loaded apart, for the judgments that
 *     leave them out (N20-7, {@code TankJudgment}); tiles outside the level count as loaded.</li>
 * </ul>
 */
public final class LevelTankCellLookup implements TankCellLookup {

    private static final TankCell EMPTY = TankCell.of(CellKind.EMPTY);
    private static final TankCell OTHER = TankCell.of(CellKind.OTHER);
    private static final TankCell CONTROLLER = TankCell.controller();
    private static final TankCell GLASS = TankCell.of(CellKind.GLASS);

    private final Level level;

    public LevelTankCellLookup(Level level) {
        this.level = level;
    }

    @Override
    public TankCell getCell(int tileX, int tileY) {
        if (!isReadable(level, tileX, tileY)) {
            return null;
        }
        GameTile tile = level.getTile(tileX, tileY);
        return cellAt(level, tileX, tileY)
                .withTankFloor(isTankFloor(tile))
                .withLiquidFloor(tile.isLiquid)
                .withOtherLayerObject(hasOtherLayerObject(level, tileX, tileY))
                .withNaturalGrowth(TankInteriorPlacement.isNaturalGrowth(level, tileX, tileY));
    }

    @Override
    public boolean isLoaded(int tileX, int tileY) {
        return !level.isTileWithinBounds(tileX, tileY) || level.regionManager.isTileLoaded(tileX, tileY);
    }

    /** The cell of the base layer object on a tile, with its tier and ownership. */
    private static TankCell cellAt(Level level, int tileX, int tileY) {
        GameObject object = level.getObject(ObjectLayerRegistry.BASE_LAYER, tileX, tileY);
        if (object instanceof TankControllerObject) {
            TankControllerObjectEntity controller = level.entityManager.getObjectEntity(tileX, tileY,
                    TankControllerObjectEntity.class);
            TankBounds kept = controller == null ? null : controller.getKeptTank();
            return kept == null ? CONTROLLER : TankCell.controller(kept);
        }
        if (object instanceof TankValveObject) {
            TankValveObjectEntity valve = level.entityManager.getObjectEntity(tileX, tileY, TankValveObjectEntity.class);
            MineralTier tier = valve == null ? TankValveObjectItem.DEFAULT_TIER : valve.getTier();
            GridPos owner = valve == null ? null : valve.getOwner();
            return TankCell.valve(tier, owner);
        }
        return cellOf(object);
    }

    /** The tank kind of an object without per-tile state (controllers keep no tank). */
    public static TankCell cellOf(GameObject object) {
        if (object.getID() == 0) {
            return EMPTY;
        }
        if (object instanceof MineralWallObject) {
            return TankCell.mineralWall(((MineralWallObject) object).getMineralTier());
        }
        if (object instanceof TankControllerObject) {
            return CONTROLLER;
        }
        if (object instanceof TankValveObject) {
            return TankCell.valve(TankValveObjectItem.DEFAULT_TIER);
        }
        if (object instanceof GlassBlockObject) {
            return GLASS;
        }
        return OTHER;
    }

    /**
     * Whether any object layer other than the base layer holds an object on the tile, among the
     * layers that count for the interior checks (N15-4).
     */
    private static boolean hasOtherLayerObject(Level level, int tileX, int tileY) {
        for (int layerID : ObjectLayerRegistry.getLayerIDs()) {
            if (layerID == ObjectLayerRegistry.BASE_LAYER || !countsForInterior(layerID)) {
                continue;
            }
            if (level.getObjectID(layerID, tileX, tileY) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an object layer counts for the interior checks (N15-4): every layer except the
     * underground pipe layer (9-1, {@link UndergroundPipeLayer}): underground pipes pass under tank
     * interiors (10-4).
     */
    static boolean countsForInterior(int layerID) {
        return layerID != UndergroundPipeLayer.ID;
    }

    /**
     * Whether a floor tile is the tank floor tile (interior condition 3, 5-5, 5-11).
     * TODO(design): the tank floor tile (name and special function) is undecided (5-6, 8-8) and does
     * not exist yet, so no floor is the tank floor and condition 3 is never met.
     */
    public static boolean isTankFloor(GameTile tile) {
        return false;
    }

    private static boolean isReadable(Level level, int tileX, int tileY) {
        return level.isTileWithinBounds(tileX, tileY) && level.regionManager.isTileLoaded(tileX, tileY);
    }

}
