package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Multiblock tank recognition rules (decisions 3-1, 4-1, 4-3, 4-4, 4-5, 5-5, 5-7, 5-11, 5-13, 7-1),
 * capacity (8-1, N4-1, N4-2, N4-4, N13-5) and ownership (N8-1, N13-3, N33-1).
 *
 * <h2>Structure</h2>
 * <ul>
 *     <li>The tank is a rectangle. Every border cell is a mineral wall, a controller or a valve.</li>
 *     <li>Exactly one controller of its own in the border; it may be on a corner.</li>
 *     <li>Any number of valves (0 allowed), never on a corner.</li>
 *     <li>Interior from 1x1 to 5x5 cells, each axis separately.</li>
 *     <li>Interior is (1) all glass, (2) all empty, or (3) all tank floor with only glass or
 *     nothing on it (5-5, 5-11). Every object layer counts except the underground pipe layer, and a
 *     liquid floor tile is never empty (N15-4).</li>
 * </ul>
 *
 * <h2>Capacity (N4-4, N13-5)</h2>
 * Interior cells x 40 x the multiplier of the lowest tier among the border's mineral walls and
 * valves: one multiplier for the whole tank. A valve has the tier of the mineral wall it was crafted
 * from; the controller counts as the highest tier ({@link #CONTROLLER_TIER}), so it never lowers the
 * multiplier.
 *
 * <h2>Ownership: first come, first served (N13-3)</h2>
 * <ul>
 *     <li>A controller keeps the tank it recognized ({@link TankCell#getKeptTank()}) for as long as
 *     that rectangle is valid, even when another valid rectangle also has it in its border; while
 *     the kept tank is invalid it is still remembered, and the controller takes a new rectangle
 *     only when exactly one is valid ({@link #findTank}).</li>
 *     <li>A controller that keeps another tank is foreign to a rectangle: it does not count toward
 *     that rectangle's one controller (N13-3 ①).</li>
 *     <li>Placing a controller that would be part of two tanks at once is rejected (N8-1,
 *     {@link #canPlaceController}), judged against the tanks their controllers hold now: a newly
 *     placed controller does not make an existing tank count as invalid for this check.</li>
 *     <li>Valves belong to no tank of their own here: every border valve counts for the rectangle,
 *     also one in the border of another tank, so two tanks sharing a wall with a valve are both
 *     tanks. A valve in a wall shared by two recognized tanks counts as a plain wall for both
 *     ({@link TankValveRole}, N33-1, replacing N15-3, N29-8 and the valve part of N13-3), and its
 *     tier still counts toward both tanks' lowest tier (N13-5). Placing a valve there is allowed
 *     (N11-1 dropped).</li>
 * </ul>
 *
 * <h2>Loaded cells only (N20-7, N21-3)</h2>
 * A controller judges the tank it keeps again with the loaded cells only
 * ({@link #validateLoaded}, used by {@link TankJudgment}); every other check here reads every cell,
 * and a cell that is not loaded reads as something else.
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

    /**
     * How far (per axis, in tiles) a tile can be from a border tile and still be part of a tank
     * through that border tile: a change farther away from a controller can never change its tank.
     */
    public static final int REACH = MAX_OUTER_SIZE - 1;

    /**
     * The tier the controller counts as in the lowest-tier calculation: the highest, so it never
     * lowers the tank's multiplier (N13-5 ①).
     */
    public static final MineralTier CONTROLLER_TIER = MineralTier.highest();

    private TankStructure() {
    }

    /**
     * Capacity of a valid tank: interior cells x {@link #BASE_CAPACITY_PER_CELL} x the multiplier of
     * the lowest tier among its mineral walls and valves (N4-4, N13-5).
     */
    public static int capacity(int interiorCells, MineralTier lowestTier) {
        return interiorCells * BASE_CAPACITY_PER_CELL * lowestTier.getCapacityMultiplier();
    }

    public static TankValidation validate(int x, int y, int outerWidth, int outerHeight, TankCellLookup lookup) {
        return validate(new TankBounds(x, y, outerWidth, outerHeight), lookup);
    }

    /**
     * Checks whether the rectangle {@code bounds} (border included) is a multiblock tank, from no
     * particular controller's point of view: a border controller counts as the tank's own unless it
     * keeps a different tank (N13-3).
     */
    public static TankValidation validate(TankBounds bounds, TankCellLookup lookup) {
        return validate(bounds, lookup, null);
    }

    /**
     * Checks whether the rectangle {@code bounds} (border included) is a multiblock tank for the
     * controller at {@code controller} (when not {@code null}): that controller always counts as the
     * tank's own, even while it keeps another rectangle, so it can move to a rebuilt tank. Every
     * other border controller that keeps a different tank is foreign and not counted (N13-3 ①).
     * When several rules are broken, the reason reported follows the order of
     * {@link TankValidation.Reason}.
     */
    public static TankValidation validate(TankBounds bounds, TankCellLookup lookup, GridPos controller) {
        return validate(bounds, lookup, controller, false);
    }

    /**
     * {@link #validate(TankBounds, TankCellLookup, GridPos)} with loaded cells only (N20-7, N21-3):
     * the cells that are not loaded ({@link TankCellLookup#isLoaded}) are left out, the way the
     * pump's 5x5 judgment leaves them out. A border cell left out breaks no border rule and adds no
     * tier, controller or valve; an interior cell left out breaks no interior rule, and the interior
     * conditions (5-5) hold when every loaded interior cell meets them. The capacity counts every
     * interior cell of the rectangle. The lowest tier (the capacity multiplier, N13-5) comes from the
     * loaded border cells only; see the overload with the judged tier (N29-1).
     */
    public static TankValidation validateLoaded(TankBounds bounds, TankCellLookup lookup, GridPos controller) {
        return validateLoaded(bounds, lookup, controller, null);
    }

    /**
     * {@link #validateLoaded(TankBounds, TankCellLookup, GridPos)} for a tank judged before (N29-1):
     * while border cells are not loaded, they count with {@code judgedTier}, the lowest tier of the
     * controller's last judgment, and the lower of it and the loaded cells' tiers wins. So the
     * capacity does not change just because more cells load later. {@code null}: loaded cells only.
     */
    public static TankValidation validateLoaded(TankBounds bounds, TankCellLookup lookup, GridPos controller,
                                                MineralTier judgedTier) {
        return validate(bounds, lookup, controller, true, judgedTier);
    }

    private static TankValidation validate(TankBounds bounds, TankCellLookup lookup, GridPos controller,
                                           boolean loadedOnly) {
        return validate(bounds, lookup, controller, loadedOnly, null);
    }

    private static TankValidation validate(TankBounds bounds, TankCellLookup lookup, GridPos controller,
                                           boolean loadedOnly, MineralTier judgedTier) {
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
        int ownControllers = 0;
        GridPos ownController = null;
        List<GridPos> valvePositions = new ArrayList<>();
        MineralTier lowest = null;
        for (int tileY = bounds.y; tileY <= bounds.getMaxY(); tileY++) {
            for (int tileX = bounds.x; tileX <= bounds.getMaxX(); tileX++) {
                if (!bounds.isOnBorder(tileX, tileY)) {
                    continue;
                }
                if (loadedOnly && !lookup.isLoaded(tileX, tileY)) {
                    // N29-1: an unloaded border cell counts with the judged tier.
                    if (judgedTier != null) {
                        lowest = lowerOf(lowest, judgedTier);
                    }
                    continue;
                }
                TankCell cell = lookup.getCell(tileX, tileY);
                CellKind kind = cell == null ? CellKind.OTHER : cell.getKind();
                switch (kind) {
                    case MINERAL_WALL:
                        lowest = lowerOf(lowest, cell.getMineral());
                        break;
                    case CONTROLLER:
                        // N13-5 ①: the controller counts as the highest tier, so it never lowers
                        // the multiplier. Folded in explicitly so the rule is visible here.
                        lowest = lowerOf(lowest, CONTROLLER_TIER);
                        GridPos position = new GridPos(tileX, tileY);
                        if (position.equals(controller) || !isForeignController(cell, bounds)) {
                            ownControllers++;
                            ownController = position;
                        }
                        break;
                    case VALVE:
                        valvePositions.add(new GridPos(tileX, tileY));
                        // N13-5 ②③: a valve's tier counts toward the tank's one lowest tier, also
                        // a valve in a wall shared with another tank, a plain wall there (N33-1).
                        lowest = lowerOf(lowest, cell.getMineral());
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
        if (ownControllers == 0) {
            return TankValidation.invalid(bounds, TankValidation.Reason.NO_CONTROLLER);
        }
        if (ownControllers > 1) {
            return TankValidation.invalid(bounds, TankValidation.Reason.MULTIPLE_CONTROLLERS);
        }

        // Interior (5-5, 5-11, N15-4)
        int interiorCells = bounds.getInteriorCellCount();
        int judgedCells = 0;
        boolean invalidObject = false;
        boolean liquidFloor = false;
        int glass = 0;
        int empty = 0;
        int tankFloor = 0;
        for (int tileY = bounds.y + 1; tileY < bounds.getMaxY(); tileY++) {
            for (int tileX = bounds.x + 1; tileX < bounds.getMaxX(); tileX++) {
                if (loadedOnly && !lookup.isLoaded(tileX, tileY)) {
                    continue;
                }
                judgedCells++;
                TankCell cell = lookup.getCell(tileX, tileY);
                CellKind kind = cell == null ? CellKind.OTHER : cell.getKind();
                if (kind == CellKind.GLASS) {
                    glass++;
                } else if (kind == CellKind.EMPTY) {
                    empty++;
                } else {
                    invalidObject = true;
                    continue;
                }
                if (cell.hasOtherLayerObject()) {
                    invalidObject = true;
                }
                if (cell.isLiquidFloor()) {
                    liquidFloor = true;
                }
                if (cell.isTankFloor()) {
                    tankFloor++;
                }
            }
        }
        if (invalidObject) {
            return TankValidation.invalid(bounds, TankValidation.Reason.INVALID_INTERIOR_OBJECT);
        }
        if (liquidFloor) {
            return TankValidation.invalid(bounds, TankValidation.Reason.LIQUID_FLOOR);
        }
        TankValidation.InteriorCondition condition;
        if (glass == judgedCells) {
            condition = TankValidation.InteriorCondition.ALL_GLASS;
        } else if (empty == judgedCells) {
            condition = TankValidation.InteriorCondition.ALL_EMPTY;
        } else if (tankFloor == judgedCells) {
            condition = TankValidation.InteriorCondition.TANK_FLOOR;
        } else {
            return TankValidation.invalid(bounds, TankValidation.Reason.MIXED_INTERIOR);
        }

        return TankValidation.valid(bounds, condition, lowest, capacity(interiorCells, lowest),
                ownController, valvePositions);
    }

    private static MineralTier lowerOf(MineralTier current, MineralTier tier) {
        return current == null ? tier : MineralTier.lowest(current, tier);
    }

    /** A border controller that keeps a tank other than {@code bounds} is foreign to it (N13-3 ①). */
    private static boolean isForeignController(TankCell controller, TankBounds bounds) {
        TankBounds kept = controller.getKeptTank();
        return kept != null && !kept.equals(bounds);
    }

    /** Whether every cell of the rectangle {@code bounds} is loaded ({@link TankCellLookup#isLoaded}). */
    public static boolean isLoaded(TankBounds bounds, TankCellLookup lookup) {
        for (int y = bounds.y; y <= bounds.getMaxY(); y++) {
            for (int x = bounds.x; x <= bounds.getMaxX(); x++) {
                if (!lookup.isLoaded(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A value that changes when a tile of the search area around ({@code tileX}, {@code tileY}) loads
     * or unloads (technical: a waiting search is tried again only then, N29-2).
     */
    public static long loadedSignature(int tileX, int tileY, TankCellLookup lookup) {
        long signature = 1;
        for (int y = tileY - REACH; y <= tileY + REACH; y++) {
            for (int x = tileX - REACH; x <= tileX + REACH; x++) {
                signature = signature * 31 + (lookup.isLoaded(x, y) ? 1 : 0);
            }
        }
        return signature;
    }

    /**
     * Whether every tile a tank search around ({@code tileX}, {@code tileY}) can read is loaded: the
     * square of {@link #REACH} tiles around it ({@link #findTank}).
     */
    public static boolean isSearchAreaLoaded(int tileX, int tileY, TankCellLookup lookup) {
        for (int y = tileY - REACH; y <= tileY + REACH; y++) {
            for (int x = tileX - REACH; x <= tileX + REACH; x++) {
                if (!lookup.isLoaded(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a valve at ({@code tileX}, {@code tileY}) is a valve of the rectangle {@code tank}: in
     * its border, not on a corner (4-5). {@code false} for a {@code null} tank.
     */
    public static boolean isValveCell(TankBounds tank, int tileX, int tileY) {
        return tank != null && tank.isOnBorder(tileX, tileY) && !tank.isCorner(tileX, tileY);
    }

    /**
     * The tank of the controller at ({@code controllerX}, {@code controllerY}) under the ownership
     * rules (N13-3):
     * <ol>
     *     <li>The tank the controller keeps, while it is still valid for it: first come, first served,
     *     even when another valid rectangle also has the controller in its border.</li>
     *     <li>Otherwise every rectangle from {@link #MIN_OUTER_SIZE} to {@link #MAX_OUTER_SIZE} per
     *     axis with the controller on its border is checked for it: exactly one valid is its new
     *     tank (a rebuilt or new tank); none is {@link TankSearchResult.Status#NOT_FOUND}; more is
     *     {@link TankSearchResult.Status#CONTROLLER_IN_SHARED_WALL}.</li>
     * </ol>
     */
    public static TankSearchResult findTank(int controllerX, int controllerY, TankCellLookup lookup) {
        return findTank(controllerX, controllerY, lookup, false);
    }

    /**
     * {@link #findTank} over the rectangles whose border and interior are all loaded (N29-2): one of
     * them is recognized at once, while the search area is not all loaded. The rectangles touching a
     * cell that is not loaded are left out until it loads
     * ({@link TankSearchResult#hasUnloadedCandidates}); so is the kept tank.
     */
    public static TankSearchResult findLoadedTank(int controllerX, int controllerY, TankCellLookup lookup) {
        return findTank(controllerX, controllerY, lookup, true);
    }

    private static TankSearchResult findTank(int controllerX, int controllerY, TankCellLookup lookup, boolean loadedOnly) {
        CachedLookup cached = new CachedLookup(controllerX, controllerY, REACH, lookup);
        TankCell start = cached.getCell(controllerX, controllerY);
        if (start == null || start.getKind() != CellKind.CONTROLLER) {
            return new TankSearchResult(TankSearchResult.Status.NOT_A_CONTROLLER, new ArrayList<TankValidation>());
        }
        GridPos controller = new GridPos(controllerX, controllerY);
        TankBounds kept = start.getKeptTank();
        if (kept != null && kept.isOnBorder(controllerX, controllerY) && (!loadedOnly || isLoaded(kept, cached))) {
            TankValidation keptTank = validate(kept, cached, controller);
            if (keptTank.isValid()) {
                List<TankValidation> found = new ArrayList<>();
                found.add(keptTank);
                return new TankSearchResult(TankSearchResult.Status.FOUND, found);
            }
        }
        int[] unloaded = new int[1];
        List<TankValidation> found = search(controllerX, controllerY, cached, controller, loadedOnly, unloaded);
        TankSearchResult.Status status;
        if (found.isEmpty()) {
            status = TankSearchResult.Status.NOT_FOUND;
        } else if (found.size() == 1) {
            status = TankSearchResult.Status.FOUND;
        } else {
            status = TankSearchResult.Status.CONTROLLER_IN_SHARED_WALL;
        }
        return new TankSearchResult(status, found, unloaded[0] > 0);
    }

    /**
     * The tanks whose border contains the tile ({@code tileX}, {@code tileY}), whatever is on it:
     * for every controller within {@link #REACH}, the tank {@link #findTank} gives it, when that tank
     * has the tile in its border. Tanks may share wall cells (N8-1), so a wall can be part of several
     * tanks. In the order the controllers are found (by tile y, then x).
     */
    public static List<TankValidation> findTanksWithBorderCell(int tileX, int tileY, TankCellLookup lookup) {
        CachedLookup cached = new CachedLookup(tileX, tileY, REACH * 2, lookup);
        List<TankValidation> found = new ArrayList<>();
        Set<TankBounds> seen = new LinkedHashSet<>();
        for (int y = tileY - REACH; y <= tileY + REACH; y++) {
            for (int x = tileX - REACH; x <= tileX + REACH; x++) {
                TankCell cell = cached.getCell(x, y);
                if (cell == null || cell.getKind() != CellKind.CONTROLLER) {
                    continue;
                }
                TankValidation tank = findTank(x, y, cached).getTank();
                if (tank != null && tank.getBounds().isOnBorder(tileX, tileY) && seen.add(tank.getBounds())) {
                    found.add(tank);
                }
            }
        }
        return found;
    }

    /**
     * Every rectangle with the tile on its border that is valid for {@code controller}; with
     * {@code loadedOnly}, the rectangles that are all loaded, counting the others in {@code unloaded[0]}.
     */
    private static List<TankValidation> search(int tileX, int tileY, TankCellLookup lookup, GridPos controller,
                                               boolean loadedOnly, int[] unloaded) {
        List<TankValidation> found = new ArrayList<>();
        for (int outerHeight = MIN_OUTER_SIZE; outerHeight <= MAX_OUTER_SIZE; outerHeight++) {
            for (int outerWidth = MIN_OUTER_SIZE; outerWidth <= MAX_OUTER_SIZE; outerWidth++) {
                for (int y = tileY - outerHeight + 1; y <= tileY; y++) {
                    for (int x = tileX - outerWidth + 1; x <= tileX; x++) {
                        TankBounds bounds = new TankBounds(x, y, outerWidth, outerHeight);
                        if (!bounds.isOnBorder(tileX, tileY)) {
                            continue;
                        }
                        if (loadedOnly && !isLoaded(bounds, lookup)) {
                            unloaded[0]++;
                            continue;
                        }
                        TankValidation validation = validate(bounds, lookup, controller);
                        if (validation.isValid()) {
                            found.add(validation);
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * The search {@link #findTank} would give a new controller (keeping no tank) placed at
     * ({@code tileX}, {@code tileY}) without changing anything else (the floor and the other layers
     * of that tile are kept). Nothing is placed; {@code lookup} is only read.
     */
    public static TankSearchResult findTankIfControllerPlaced(int tileX, int tileY, TankCellLookup lookup) {
        return findTank(tileX, tileY, withObject(tileX, tileY, TankCell.controller(), lookup));
    }

    /**
     * Placement check for a tank controller (N8-1), for the controller object's canPlace: tanks may
     * share wall cells, but a controller may not sit in a shared wall. Returns {@code false} when the
     * controller would be part of two (or more) tanks at once, counting
     * <ul>
     *     <li>the tanks that have the tile in their border now ({@link #findTanksWithBorderCell}):
     *     they keep their own controller (N13-3), so the new controller does not make them invalid
     *     for this check, and</li>
     *     <li>the rectangles that would be valid for the new controller
     *     ({@link #findTankIfControllerPlaced}).</li>
     * </ul>
     * Every other placement is allowed, including one that forms no tank yet. A controller placed in
     * the border of one existing tank (not a shared wall) is allowed: that tank then has two
     * controllers of its own and stops being valid (N29-6, review #15④).
     */
    public static boolean canPlaceController(int tileX, int tileY, TankCellLookup lookup) {
        CachedLookup cached = new CachedLookup(tileX, tileY, REACH * 2, lookup);
        Set<TankBounds> tanks = new LinkedHashSet<>();
        for (TankValidation tank : findTanksWithBorderCell(tileX, tileY, cached)) {
            tanks.add(tank.getBounds());
        }
        for (TankValidation tank : findTankIfControllerPlaced(tileX, tileY, cached).getCandidates()) {
            tanks.add(tank.getBounds());
        }
        return tanks.size() < 2;
    }

    /**
     * {@code lookup} with the base layer object of one tile replaced by {@code placed}, keeping that
     * tile's floor and other layers.
     */
    private static TankCellLookup withObject(final int tileX, final int tileY, TankCell placed,
                                             final TankCellLookup lookup) {
        TankCell current = lookup.getCell(tileX, tileY);
        final TankCell cell = current == null ? placed : placed.withTankFloor(current.isTankFloor())
                .withLiquidFloor(current.isLiquidFloor()).withOtherLayerObject(current.hasOtherLayerObject());
        return new TankCellLookup() {
            @Override
            public TankCell getCell(int x, int y) {
                return x == tileX && y == tileY ? cell : lookup.getCell(x, y);
            }

            @Override
            public boolean isLoaded(int x, int y) {
                return lookup.isLoaded(x, y);
            }
        };
    }

    /**
     * Caches lookups in the square of {@code radius} tiles around one tile, so each tile is read once
     * per search.
     */
    private static final class CachedLookup implements TankCellLookup {
        private final int size;
        private final int originX;
        private final int originY;
        private final TankCellLookup source;
        private final TankCell[] cells;
        private final boolean[] loaded;

        CachedLookup(int centerX, int centerY, int radius, TankCellLookup source) {
            this.size = radius * 2 + 1;
            this.originX = centerX - radius;
            this.originY = centerY - radius;
            this.source = source;
            this.cells = new TankCell[size * size];
            this.loaded = new boolean[size * size];
        }

        @Override
        public TankCell getCell(int tileX, int tileY) {
            int dx = tileX - originX;
            int dy = tileY - originY;
            if (dx < 0 || dy < 0 || dx >= size || dy >= size) {
                return source.getCell(tileX, tileY);
            }
            int index = dy * size + dx;
            if (!loaded[index]) {
                cells[index] = source.getCell(tileX, tileY);
                loaded[index] = true;
            }
            return cells[index];
        }

        @Override
        public boolean isLoaded(int tileX, int tileY) {
            return source.isLoaded(tileX, tileY);
        }
    }

}
