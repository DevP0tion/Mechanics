package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Multiblock tank recognition rules (decisions 3-1, 4-1, 4-3, 4-4, 4-5, 5-5, 5-7, 5-11, 5-13, 7-1)
 * and capacity (8-1, N4-1, N4-2, N4-4).
 *
 * <ul>
 *     <li>The tank is a rectangle. Every border cell is a mineral wall, the controller or a valve.</li>
 *     <li>Exactly one controller in the border; it may be on a corner.</li>
 *     <li>Any number of valves (0 allowed), never on a corner.</li>
 *     <li>Interior from 1x1 to 5x5 cells, each axis separately.</li>
 *     <li>Interior is (1) all glass, (2) all empty, or (3) all tank floor with only glass or
 *     nothing on it.</li>
 *     <li>Capacity = interior cells x 40 x the lowest capacity multiplier among the border's
 *     mineral walls.</li>
 * </ul>
 */
public final class TankStructure {

    /** Base capacity per interior cell, in fluid units: 40 = 4 buckets (N4-1). */
    public static final int BASE_CAPACITY_PER_CELL = 40;

    /** Smallest interior size per axis: the structural minimum (4-1). */
    public static final int MIN_INTERIOR_SIZE = 1;

    /** Largest interior size per axis, counted in interior cells (4-2, 7-1). */
    public static final int MAX_INTERIOR_SIZE = 5;

    /** Smallest outer size per axis (interior + 2 border cells). */
    public static final int MIN_OUTER_SIZE = MIN_INTERIOR_SIZE + 2;

    /** Largest outer size per axis (interior + 2 border cells). */
    public static final int MAX_OUTER_SIZE = MAX_INTERIOR_SIZE + 2;

    private TankStructure() {
    }

    /**
     * Capacity of a valid tank: interior cells x {@link #BASE_CAPACITY_PER_CELL} x the lowest wall
     * tier's multiplier (N4-4).
     */
    public static int capacity(int interiorCells, MineralTier lowestWallTier) {
        return interiorCells * BASE_CAPACITY_PER_CELL * lowestWallTier.getCapacityMultiplier();
    }

    public static TankValidation validate(int x, int y, int outerWidth, int outerHeight, TankCellLookup lookup) {
        return validate(new TankBounds(x, y, outerWidth, outerHeight), lookup);
    }

    /**
     * Checks whether the rectangle {@code bounds} (border included) is a multiblock tank.
     * When several rules are broken, the reason reported follows the order of
     * {@link TankValidation.Reason}.
     */
    public static TankValidation validate(TankBounds bounds, TankCellLookup lookup) {
        int interiorWidth = bounds.getInteriorWidth();
        int interiorHeight = bounds.getInteriorHeight();
        if (interiorWidth < MIN_INTERIOR_SIZE || interiorHeight < MIN_INTERIOR_SIZE) {
            return TankValidation.invalid(bounds, TankValidation.Reason.TOO_SMALL);
        }
        if (interiorWidth > MAX_INTERIOR_SIZE || interiorHeight > MAX_INTERIOR_SIZE) {
            return TankValidation.invalid(bounds, TankValidation.Reason.TOO_LARGE);
        }

        // Border
        boolean invalidBorderCell = false;
        boolean valveOnCorner = false;
        int controllers = 0;
        int controllerX = 0;
        int controllerY = 0;
        int valves = 0;
        MineralTier lowest = null;
        for (int tileY = bounds.y; tileY <= bounds.getMaxY(); tileY++) {
            for (int tileX = bounds.x; tileX <= bounds.getMaxX(); tileX++) {
                if (!bounds.isOnBorder(tileX, tileY)) {
                    continue;
                }
                TankCell cell = lookup.getCell(tileX, tileY);
                CellKind kind = cell == null ? CellKind.OTHER : cell.getKind();
                switch (kind) {
                    case MINERAL_WALL:
                        lowest = lowest == null ? cell.getMineral() : MineralTier.lowest(lowest, cell.getMineral());
                        break;
                    case CONTROLLER:
                        controllers++;
                        controllerX = tileX;
                        controllerY = tileY;
                        break;
                    case VALVE:
                        valves++;
                        if (bounds.isCorner(tileX, tileY)) {
                            valveOnCorner = true;
                        }
                        break;
                    default:
                        invalidBorderCell = true;
                        break;
                }
            }
        }
        if (invalidBorderCell) {
            return TankValidation.invalid(bounds, TankValidation.Reason.INVALID_BORDER_CELL);
        }
        if (valveOnCorner) {
            return TankValidation.invalid(bounds, TankValidation.Reason.VALVE_ON_CORNER);
        }
        if (controllers == 0) {
            return TankValidation.invalid(bounds, TankValidation.Reason.NO_CONTROLLER);
        }
        if (controllers > 1) {
            return TankValidation.invalid(bounds, TankValidation.Reason.MULTIPLE_CONTROLLERS);
        }
        if (lowest == null) {
            // Unreachable: with one controller and no valve on a corner, at least 3 of the 4
            // corners are mineral walls.
            throw new IllegalStateException("Valid border without mineral walls: " + bounds);
        }

        // Interior
        int interiorCells = bounds.getInteriorCellCount();
        int glass = 0;
        int empty = 0;
        int tankFloor = 0;
        for (int tileY = bounds.y + 1; tileY < bounds.getMaxY(); tileY++) {
            for (int tileX = bounds.x + 1; tileX < bounds.getMaxX(); tileX++) {
                TankCell cell = lookup.getCell(tileX, tileY);
                CellKind kind = cell == null ? CellKind.OTHER : cell.getKind();
                if (kind == CellKind.GLASS) {
                    glass++;
                } else if (kind == CellKind.EMPTY) {
                    empty++;
                } else {
                    return TankValidation.invalid(bounds, TankValidation.Reason.INVALID_INTERIOR_OBJECT);
                }
                if (cell.isTankFloor()) {
                    tankFloor++;
                }
            }
        }
        TankValidation.InteriorCondition condition;
        if (glass == interiorCells) {
            condition = TankValidation.InteriorCondition.ALL_GLASS;
        } else if (empty == interiorCells) {
            condition = TankValidation.InteriorCondition.ALL_EMPTY;
        } else if (tankFloor == interiorCells) {
            condition = TankValidation.InteriorCondition.TANK_FLOOR;
        } else {
            return TankValidation.invalid(bounds, TankValidation.Reason.MIXED_INTERIOR);
        }

        return TankValidation.valid(bounds, condition, lowest, capacity(interiorCells, lowest),
                controllerX, controllerY, valves);
    }

    /**
     * Finds the valid tank(s) whose border contains the controller at ({@code controllerX},
     * {@code controllerY}). Every rectangle from {@link #MIN_OUTER_SIZE} to {@link #MAX_OUTER_SIZE}
     * per axis with the controller on its border is checked.
     */
    public static TankSearchResult findTank(int controllerX, int controllerY, TankCellLookup lookup) {
        CachedLookup cached = new CachedLookup(controllerX, controllerY, lookup);
        TankCell start = cached.getCell(controllerX, controllerY);
        if (start == null || start.getKind() != CellKind.CONTROLLER) {
            return new TankSearchResult(TankSearchResult.Status.NOT_A_CONTROLLER, new ArrayList<TankValidation>());
        }
        List<TankValidation> found = new ArrayList<>();
        for (int outerHeight = MIN_OUTER_SIZE; outerHeight <= MAX_OUTER_SIZE; outerHeight++) {
            for (int outerWidth = MIN_OUTER_SIZE; outerWidth <= MAX_OUTER_SIZE; outerWidth++) {
                for (int y = controllerY - outerHeight + 1; y <= controllerY; y++) {
                    for (int x = controllerX - outerWidth + 1; x <= controllerX; x++) {
                        TankBounds bounds = new TankBounds(x, y, outerWidth, outerHeight);
                        if (!bounds.isOnBorder(controllerX, controllerY)) {
                            continue;
                        }
                        TankValidation validation = validate(bounds, cached);
                        if (validation.isValid()) {
                            found.add(validation);
                        }
                    }
                }
            }
        }
        TankSearchResult.Status status;
        if (found.isEmpty()) {
            status = TankSearchResult.Status.NOT_FOUND;
        } else if (found.size() == 1) {
            status = TankSearchResult.Status.FOUND;
        } else {
            status = TankSearchResult.Status.AMBIGUOUS;
        }
        return new TankSearchResult(status, found);
    }

    /**
     * Caches lookups in the square a search around one controller can reach, so each tile is read
     * once per search.
     */
    private static final class CachedLookup implements TankCellLookup {
        private static final int REACH = MAX_OUTER_SIZE - 1;
        private static final int SIZE = REACH * 2 + 1;

        private final int originX;
        private final int originY;
        private final TankCellLookup source;
        private final TankCell[] cells = new TankCell[SIZE * SIZE];
        private final boolean[] loaded = new boolean[SIZE * SIZE];

        CachedLookup(int centerX, int centerY, TankCellLookup source) {
            this.originX = centerX - REACH;
            this.originY = centerY - REACH;
            this.source = source;
        }

        @Override
        public TankCell getCell(int tileX, int tileY) {
            int dx = tileX - originX;
            int dy = tileY - originY;
            if (dx < 0 || dy < 0 || dx >= SIZE || dy >= SIZE) {
                return source.getCell(tileX, tileY);
            }
            int index = dy * SIZE + dx;
            if (!loaded[index]) {
                cells[index] = source.getCell(tileX, tileY);
                loaded[index] = true;
            }
            return cells[index];
        }
    }

}
