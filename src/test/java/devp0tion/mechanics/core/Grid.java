package devp0tion.mechanics.core;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link TankCellLookup} built from ASCII rows, for tests. Row 0 is tile y = 0.
 *
 * <pre>
 * #        copper mineral wall
 * 0-9, a   mineral wall of MineralTier ordinal 0-9, a = 10 (0 = COPPER ... 8 = ANCIENTFOSSIL,
 *          9 = NIGHTSTEEL, a = SPIDERITE)
 * C        controller keeping no tank
 * V        copper valve belonging to no tank
 * G / g    glass (g: on the tank floor tile)
 * . / ,    empty (,: tank floor tile)
 * ~        empty on a liquid floor tile
 * *        empty base layer, but an object on another layer (a carpet, N15-4)
 * X / x    other object (x: on the tank floor tile)
 * n        the lookup returns null
 * </pre>
 * Tiles outside the rows are empty with a normal floor. {@link #set} overrides single tiles (valve
 * tiers, ownership, kept tanks).
 */
final class Grid implements TankCellLookup {

    private final String[] rows;
    private final Map<Long, Integer> reads = new HashMap<>();
    private final Map<Long, TankCell> overrides = new HashMap<>();

    private Grid(String[] rows) {
        this.rows = rows;
    }

    static Grid of(String... rows) {
        return new Grid(rows);
    }

    /** Replaces one tile's cell; returns this grid. */
    Grid set(int tileX, int tileY, TankCell cell) {
        overrides.put(key(tileX, tileY), cell);
        return this;
    }

    private static long key(int tileX, int tileY) {
        return ((long) tileX << 32) ^ (tileY & 0xffffffffL);
    }

    @Override
    public TankCell getCell(int tileX, int tileY) {
        reads.merge(key(tileX, tileY), 1, Integer::sum);
        if (overrides.containsKey(key(tileX, tileY))) {
            return overrides.get(key(tileX, tileY));
        }
        if (tileY < 0 || tileY >= rows.length || tileX < 0 || tileX >= rows[tileY].length()) {
            return TankCell.of(CellKind.EMPTY);
        }
        return parse(rows[tileY].charAt(tileX));
    }

    /** Highest number of times any single tile was read. */
    int maxReadsPerTile() {
        int max = 0;
        for (int count : reads.values()) {
            max = Math.max(max, count);
        }
        return max;
    }

    static TankCell parse(char c) {
        switch (c) {
            case '#':
                return TankCell.mineralWall(MineralTier.COPPER);
            case 'a':
                return TankCell.mineralWall(MineralTier.values()[10]);
            case 'C':
                return TankCell.controller();
            case 'V':
                return TankCell.valve(MineralTier.COPPER);
            case 'G':
                return TankCell.of(CellKind.GLASS);
            case 'g':
                return TankCell.of(CellKind.GLASS).withTankFloor(true);
            case '.':
                return TankCell.of(CellKind.EMPTY);
            case ',':
                return TankCell.of(CellKind.EMPTY).withTankFloor(true);
            case '~':
                return TankCell.of(CellKind.EMPTY).withLiquidFloor(true);
            case '*':
                return TankCell.of(CellKind.EMPTY).withOtherLayerObject(true);
            case 'X':
                return TankCell.of(CellKind.OTHER);
            case 'x':
                return TankCell.of(CellKind.OTHER).withTankFloor(true);
            case 'n':
                return null;
            default:
                if (c >= '0' && c <= '9') {
                    return TankCell.mineralWall(MineralTier.values()[c - '0']);
                }
                throw new IllegalArgumentException("Unknown grid char '" + c + "'");
        }
    }

}
