package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Multiblock tank recognition rules (decisions 3-1, 4-1, 4-3, 4-4, 4-5, 5-5, 5-7, 5-11, 5-13, 7-1),
 * capacity (8-1, N4-1, N4-2, N4-4, N13-5) and ownership (N8-1, N11-1, N13-3, N15-3).
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
 * <h2>Ownership: first come, first served (N13-3, N15-3)</h2>
 * <ul>
 *     <li>A controller keeps the tank it recognized ({@link TankCell#getKeptTank()}) for as long as
 *     that rectangle is valid, even when another valid rectangle also has it in its border; while
 *     the kept tank is invalid it is still remembered, and the controller takes a new rectangle
 *     only when exactly one is valid ({@link #findTank}).</li>
 *     <li>A controller that keeps another tank is foreign to a rectangle: it does not count toward
 *     that rectangle's one controller (N13-3 ①).</li>
 *     <li>A valve belongs to the controller that recognized it first
 *     ({@link TankCell#getValveOwner()}, {@link #effectiveValveOwner}). A rectangle with a valve of
 *     another tank in its border is no tank; the other tank keeps the valve (N15-3). The placement
 *     that causes this is allowed.</li>
 *     <li>Placing a controller or valve that would be part of two tanks at once is rejected
 *     (N8-1, N11-1, {@link #canPlaceController}, {@link #canPlaceValve}), judged against the tanks
 *     their controllers hold now: a newly placed controller does not make an existing tank count as
 *     invalid for this check.</li>
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

    /**
     * The valve assumed by the placement checks ({@link #findTanksIfValvePlaced}): it belongs to no
     * tank yet. Its tier does not change which rectangles are tanks, only their capacity; the highest
     * tier leaves the reported capacities as they would be without it.
     */
    private static final TankCell PLACED_VALVE = TankCell.valve(MineralTier.highest());

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
     * interior cell of the rectangle.
     * <p>TODO(design): while border cells are not loaded, the lowest tier (the capacity multiplier,
     * N13-5) is taken from the loaded border cells only, so the capacity can differ from the whole
     * tank's until it is judged with every cell loaded; a lower capacity then loses the excess
     * (N11-2).
     */
    public static TankValidation validateLoaded(TankBounds bounds, TankCellLookup lookup, GridPos controller) {
        return validate(bounds, lookup, controller, true);
    }

    private static TankValidation validate(TankBounds bounds, TankCellLookup lookup, GridPos controller,
                                           boolean loadedOnly) {
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
        List<TankCell> valveCells = new ArrayList<>();
        MineralTier lowest = null;
        for (int tileY = bounds.y; tileY <= bounds.getMaxY(); tileY++) {
            for (int tileX = bounds.x; tileX <= bounds.getMaxX(); tileX++) {
                if (!bounds.isOnBorder(tileX, tileY) || loadedOnly && !lookup.isLoaded(tileX, tileY)) {
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
                        valveCells.add(cell);
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
        for (int i = 0; i < valvePositions.size(); i++) {
            GridPos position = valvePositions.get(i);
            GridPos owner = effectiveValveOwner(position.x, position.y, valveCells.get(i), lookup);
            if (owner != null && !owner.equals(ownController)) {
                // N15-3: valves are not shared; the tank that owns it keeps it.
                return TankValidation.invalid(bounds, TankValidation.Reason.FOREIGN_VALVE);
            }
            // N13-5 ②③: a valve's tier counts toward the tank's one lowest tier.
            lowest = lowerOf(lowest, valveCells.get(i).getMineral());
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
     * Whether a controller keeping {@code keptTank} owns a valve at ({@code tileX}, {@code tileY}):
     * the valve is in that tank's border, not on a corner.
     */
    public static boolean ownsValve(TankBounds keptTank, int tileX, int tileY) {
        return keptTank != null && keptTank.isOnBorder(tileX, tileY) && !keptTank.isCorner(tileX, tileY);
    }

    /**
     * The controller that owns the valve {@code valve} at ({@code tileX}, {@code tileY}) now
     * (N13-3), or {@code null} when it belongs to no tank. The valve remembers the controller that
     * recognized it first; that controller still owns it while it keeps a tank (valid or not) with
     * the valve in its border. When the controller is gone or keeps a tank without the valve, the
     * valve is free again. When the remembered controller's tile cannot be read (not loaded, D5),
     * the valve still counts as owned, so no other tank takes it by mistake.
     */
    public static GridPos effectiveValveOwner(int tileX, int tileY, TankCell valve, TankCellLookup lookup) {
        GridPos owner = valve == null ? null : valve.getValveOwner();
        if (owner == null) {
            return null;
        }
        TankCell ownerCell = lookup.getCell(owner.x, owner.y);
        if (ownerCell == null) {
            return owner;
        }
        if (ownerCell.getKind() != CellKind.CONTROLLER) {
            return null;
        }
        return ownsValve(ownerCell.getKeptTank(), tileX, tileY) ? owner : null;
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
        CachedLookup cached = new CachedLookup(controllerX, controllerY, REACH, lookup);
        TankCell start = cached.getCell(controllerX, controllerY);
        if (start == null || start.getKind() != CellKind.CONTROLLER) {
            return new TankSearchResult(TankSearchResult.Status.NOT_A_CONTROLLER, new ArrayList<TankValidation>());
        }
        GridPos controller = new GridPos(controllerX, controllerY);
        TankBounds kept = start.getKeptTank();
        if (kept != null && kept.isOnBorder(controllerX, controllerY)) {
            TankValidation keptTank = validate(kept, cached, controller);
            if (keptTank.isValid()) {
                List<TankValidation> found = new ArrayList<>();
                found.add(keptTank);
                return new TankSearchResult(TankSearchResult.Status.FOUND, found);
            }
        }
        List<TankValidation> found = search(controllerX, controllerY, cached, controller);
        TankSearchResult.Status status;
        if (found.isEmpty()) {
            status = TankSearchResult.Status.NOT_FOUND;
        } else if (found.size() == 1) {
            status = TankSearchResult.Status.FOUND;
        } else {
            status = TankSearchResult.Status.CONTROLLER_IN_SHARED_WALL;
        }
        return new TankSearchResult(status, found);
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

    /** Every rectangle with the tile on its border that is valid for {@code controller}. */
    private static List<TankValidation> search(int tileX, int tileY, TankCellLookup lookup, GridPos controller) {
        List<TankValidation> found = new ArrayList<>();
        for (int outerHeight = MIN_OUTER_SIZE; outerHeight <= MAX_OUTER_SIZE; outerHeight++) {
            for (int outerWidth = MIN_OUTER_SIZE; outerWidth <= MAX_OUTER_SIZE; outerWidth++) {
                for (int y = tileY - outerHeight + 1; y <= tileY; y++) {
                    for (int x = tileX - outerWidth + 1; x <= tileX; x++) {
                        TankBounds bounds = new TankBounds(x, y, outerWidth, outerHeight);
                        if (!bounds.isOnBorder(tileX, tileY)) {
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
     * Every other placement is allowed, including one that forms no tank yet.
     * <p>TODO(design): a controller placed in the border of one existing tank (not a shared wall) is
     * allowed and leaves that tank with two controllers of its own, so it stops being valid (review
     * #15④, behaviour unchanged since round 3).
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
     * The tanks whose border would contain a valve placed at ({@code tileX}, {@code tileY}), which
     * belongs to no tank yet, without changing anything else (the floor and the other layers of
     * that tile are kept): {@link #findTanksWithBorderCell} with the valve there. Nothing is placed;
     * {@code lookup} is only read.
     */
    public static List<TankValidation> findTanksIfValvePlaced(int tileX, int tileY, TankCellLookup lookup) {
        return findTanksWithBorderCell(tileX, tileY, withObject(tileX, tileY, PLACED_VALVE, lookup));
    }

    /**
     * Placement check for a tank valve (N11-1, the same rule as the controller's N8-1), for the
     * valve object's canPlace: a valve may not sit in a wall shared by two tanks. Returns
     * {@code false} when the valve, once placed, would be part of the border of two (or more) tanks
     * at once ({@link #findTanksIfValvePlaced}). Every other placement is allowed, including one that
     * forms no tank yet.
     */
    public static boolean canPlaceValve(int tileX, int tileY, TankCellLookup lookup) {
        return findTanksIfValvePlaced(tileX, tileY, lookup).size() < 2;
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
