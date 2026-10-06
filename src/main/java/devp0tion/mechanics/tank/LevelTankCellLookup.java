package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.CellKind;
import devp0tion.mechanics.core.TankCell;
import devp0tion.mechanics.core.TankCellLookup;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.objects.GlassBlockObject;
import devp0tion.mechanics.objects.MineralWallObject;
import devp0tion.mechanics.objects.TankControllerObject;
import devp0tion.mechanics.objects.TankValveObject;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;

/**
 * The game adapter of {@link TankCellLookup}: reads what is on a tile of a level for the tank rules
 * in {@link TankStructure}.
 *
 * <ul>
 *     <li>Only the base object layer (layer 0, where walls and blocks go, D1) is read
 *     ({@link #objectFor}).
 *     TODO(design): which layers count in the interior "empty" / "glass only" checks (5-5, 5-11)
 *     and for the border is undecided: wall decorations (wallDecor), the tile layer (carpets), the
 *     future underground pipe layer (9-1), and a real liquid tile underfoot. Today they are all
 *     ignored (an interior cell on a liquid tile with no object is empty). The decision belongs in
 *     {@link #objectFor} and {@link #getCell}.</li>
 *     <li>No object: {@link CellKind#EMPTY}. Mineral wall, tank controller, tank valve, glass block:
 *     their kinds. Anything else: {@link CellKind#OTHER}.</li>
 *     <li>Tiles outside the level or in a region that is not loaded read as {@code null} (something
 *     else). Callers that must not judge a tank by unloaded tiles check {@link #isAreaLoaded}
 *     first (D5).</li>
 * </ul>
 */
public final class LevelTankCellLookup implements TankCellLookup {

    private static final TankCell EMPTY = TankCell.of(CellKind.EMPTY);
    private static final TankCell OTHER = TankCell.of(CellKind.OTHER);
    private static final TankCell CONTROLLER = TankCell.of(CellKind.CONTROLLER);
    private static final TankCell VALVE = TankCell.of(CellKind.VALVE);
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
        return cellOf(objectFor(level, tileX, tileY)).withTankFloor(isTankFloor(level.getTile(tileX, tileY)));
    }

    /**
     * The object that decides a tile's kind: the base layer object.
     * TODO(design): other layers and liquid tiles are ignored (see the class comment).
     */
    private static GameObject objectFor(Level level, int tileX, int tileY) {
        return level.getObject(0, tileX, tileY);
    }

    /** The tank kind of an object. */
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
            return VALVE;
        }
        if (object instanceof GlassBlockObject) {
            return GLASS;
        }
        return OTHER;
    }

    /**
     * Whether a floor tile is the tank floor tile (interior condition 3, 5-5, 5-11).
     * TODO(design): the tank floor tile (name and special function) is undecided (5-6, 8-8) and does
     * not exist yet, so no floor is the tank floor and condition 3 is never met.
     */
    public static boolean isTankFloor(GameTile tile) {
        return false;
    }

    /**
     * Whether every tile a tank search around ({@code tileX}, {@code tileY}) can read is loaded
     * ({@link TankStructure#REACH}). Tiles outside the level count as loaded: they never will be,
     * and they are read as "something else".
     */
    public static boolean isAreaLoaded(Level level, int tileX, int tileY) {
        for (int y = tileY - TankStructure.REACH; y <= tileY + TankStructure.REACH; y++) {
            for (int x = tileX - TankStructure.REACH; x <= tileX + TankStructure.REACH; x++) {
                if (level.isTileWithinBounds(x, y) && !level.regionManager.isTileLoaded(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isReadable(Level level, int tileX, int tileY) {
        return level.isTileWithinBounds(tileX, tileY) && level.regionManager.isTileLoaded(tileX, tileY);
    }

}
