package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How a tank controller judges the tank it keeps (N20-7 tank part, N20-8, N21-3, N22-7, N23-4,
 * N26-3, N29-1, N29-2, N29-5, N29-8).
 *
 * <p>The judgment is anchored at the controller: the tank it keeps (its range, N13-3), whether that
 * tank is active and its capacity ({@link TankStorage}), and the tank's lowest tier (N29-1). The
 * controller saves them and restores them when it loads ({@link TankStorage#restoreJudgment}). The regions a tank spans are not kept
 * loaded together (N20-8, N15-6 replaced): parts of a tank load and unload on their own.
 *
 * <ul>
 *     <li>Loading ({@link Mode#LOAD}): the saved judgment applies at once. When part of the kept
 *     tank's cells are not loaded, it stays as saved, so an active tank stays active (N22-7); when
 *     every cell of the tank is loaded, the tank is judged as after a change.</li>
 *     <li>Cells are watched from the time they load: the game reports changes of loaded cells, and
 *     a change judges the kept tank again ({@link Mode#CHANGE}) with the loaded cells only
 *     ({@link TankStructure#validateLoaded}): the cells that are not loaded are left out, the way the
 *     pump's 5x5 judgment leaves them out (N20-7, N21-3); its unloaded border cells count with the
 *     lowest tier of the last judgment, so the capacity does not change only because more cells
 *     load (N29-1). A cell that loads is not a change.</li>
 *     <li>When everything a search can read is loaded, the judgment is the full search
 *     ({@link TankStructure#findTank}): the kept tank first (N13-3), else a new rectangle.</li>
 *     <li>Otherwise, with no kept tank or an invalid one, a rectangle whose border and interior are
 *     all loaded is recognized right away; a rectangle touching cells that are not loaded is not,
 *     until they load ({@link TankStructure#findLoadedTank}, N29-2, replacing the wait for the whole
 *     search area).</li>
 *     <li>Two tanks at once (N29-8): when one change makes the tanks of two controllers valid at the
 *     same time and they share a valve that belongs to no tank yet (on their shared wall), neither is
 *     recognized, whichever controller judges first, until a player changes something within reach
 *     of either tank (a contested controller also judges again after a change within reach of the
 *     other tank, {@code TankRegistry.onTileChanged}; TODO(confirm): that reading of "until a player
 *     changes something"), so the tank still valid then is recognized. A valve that already belongs
 *     to a tank keeps N13-3 and N15-3. A rectangle touching cells that are not loaded
 *     is no competitor (it cannot be judged valid). TODO(confirm): a competing controller that is not
 *     loaded (dormant) is not seen, so the loaded one recognizes its tank and takes the valve; the
 *     other finds the valve taken when it loads (N15-3).</li>
 *     <li>Natural growth (N23-4, N26-3, N29-5): while the tank is active, natural growth on its
 *     loaded interior cells ({@link TankCell#isNaturalGrowth}: every object of the game's grass kind,
 *     placed by a player or not) is broken when the tank is judged, without drops, instead of making
 *     the tank invalid: the game blocks it inside the tanks of loaded
 *     controllers (N20-1), and this covers what grew while no controller blocked it, for example in
 *     the world time simulation of a region that loaded before the controller's. The judgment sees
 *     those cells as broken ({@link Result#getNaturalGrowth}: the game breaks them). Inside an
 *     inactive tank natural growth is not blocked (N20-1) and still makes the tank invalid.</li>
 *     <li>Floors (N29-9): the floors grass or snow spread onto are not part of the judgment; the
 *     controller compares the interior with the floors it recorded after each judgment
 *     ({@link TankFloorRecord#afterJudgment}).</li>
 * </ul>
 */
public final class TankJudgment {

    /** What makes the controller judge. */
    public enum Mode {
        /** The first judgment after the controller loaded with a saved judgment (N22-7). */
        LOAD,
        /** A cell within reach changed (5-1), or the controller is new. */
        CHANGE,
        /** A search that waits for cells to load, tried again when cells of the search area load (D5, N29-2). */
        SEARCH
    }

    /** The controller's judgment before this one. */
    public enum Prior {
        ACTIVE,
        INACTIVE
    }

    /** What the judgment did. */
    public enum Kind {
        /** Everything within reach is loaded: the full search ({@link Result#getTank}, or no tank). */
        SEARCHED,
        /** The kept tank is valid with its loaded cells. */
        KEPT_VALID,
        /**
         * The kept tank is not valid with its loaded cells and no rectangle with all its cells loaded
         * is valid: inactive; a search waits for the cells still unloaded (N29-2).
         */
        KEPT_INVALID,
        /** Loaded while part of the kept tank is not loaded: the saved judgment stays (N22-7). */
        SAVED,
        /** Nothing is judged: the search waits for the area to load. */
        WAITING,
        /**
         * The search area is not all loaded, and a rectangle with all its cells loaded is the tank
         * (N29-2).
         */
        FOUND_LOADED,
        /**
         * The tank would be valid, but a valve of it that belongs to no tank is in the valid tank of
         * another controller too (N29-8): no tank, nothing waits.
         */
        CONTESTED
    }

    /** The outcome of one judgment. */
    public static final class Result {
        private final Kind kind;
        private final TankValidation tank;
        private final boolean settled;
        private final List<GridPos> naturalGrowth;

        Result(Kind kind, TankValidation tank, boolean settled, List<GridPos> naturalGrowth) {
            this.kind = kind;
            this.tank = tank;
            this.settled = settled;
            this.naturalGrowth = Collections.unmodifiableList(naturalGrowth);
        }

        public Kind getKind() {
            return kind;
        }

        /**
         * Whether the controller applies {@link #getTank} as its new judgment (a valid tank: active
         * with its capacity; {@code null}: inactive). Otherwise its judgment stays as it is.
         */
        public boolean appliesJudgment() {
            return kind == Kind.SEARCHED || kind == Kind.KEPT_VALID || kind == Kind.KEPT_INVALID
                    || kind == Kind.FOUND_LOADED || kind == Kind.CONTESTED;
        }

        /** The valid tank of the new judgment, or {@code null}. */
        public TankValidation getTank() {
            return tank;
        }

        /**
         * {@code false} while a search waits for the whole search area to load: the controller tries
         * again with {@link Mode#SEARCH}.
         */
        public boolean isSettled() {
            return settled;
        }

        /**
         * The interior cells whose natural growth the game breaks now, without drops (N23-4, N26-3),
         * in reading order. The judgment already sees them as broken.
         */
        public List<GridPos> getNaturalGrowth() {
            return naturalGrowth;
        }

        @Override
        public String toString() {
            return "TankJudgment.Result[" + kind + ", " + tank + (settled ? "" : ", search waits")
                    + (naturalGrowth.isEmpty() ? "" : ", natural growth " + naturalGrowth) + "]";
        }
    }

    private TankJudgment() {
    }

    /** {@link #judge(int, int, TankCellLookup, Mode, Prior, MineralTier)} without a judged tier. */
    public static Result judge(int controllerX, int controllerY, TankCellLookup lookup, Mode mode, Prior prior) {
        return judge(controllerX, controllerY, lookup, mode, prior, null);
    }

    /**
     * Judges the tank of the controller at ({@code controllerX}, {@code controllerY}). Nothing is
     * changed: the game applies the result.
     *
     * @param prior      the controller's judgment before this one
     * @param judgedTier the lowest tier of the controller's last valid judgment, or {@code null}
     *                   (N29-1)
     */
    public static Result judge(int controllerX, int controllerY, TankCellLookup lookup, Mode mode, Prior prior,
                               MineralTier judgedTier) {
        GridPos controller = new GridPos(controllerX, controllerY);
        TankCell start = lookup.getCell(controllerX, controllerY);
        TankBounds kept = start != null && start.getKind() == CellKind.CONTROLLER ? start.getKeptTank() : null;
        if (kept != null && !kept.isOnBorder(controllerX, controllerY)) {
            kept = null;
        }
        boolean areaLoaded = TankStructure.isSearchAreaLoaded(controllerX, controllerY, lookup);
        List<GridPos> growth = kept != null && prior != Prior.INACTIVE ? naturalGrowth(kept, lookup)
                : Collections.<GridPos>emptyList();
        TankCellLookup broken = growth.isEmpty() ? lookup : withNaturalGrowthBroken(lookup, growth);
        if (areaLoaded) {
            TankValidation tank = null;
            if (kept != null) {
                TankValidation keptTank = TankStructure.validate(kept, broken, controller);
                tank = keptTank.isValid() ? keptTank : null;
            }
            if (tank == null) {
                // Another rectangle: the cells as they are (the break covers the kept tank only).
                tank = TankStructure.findTank(controllerX, controllerY, lookup).getTank();
            }
            return result(Kind.SEARCHED, tank, true, growth, broken);
        }
        if (kept != null && mode != Mode.SEARCH) {
            if (mode == Mode.LOAD && !TankStructure.isLoaded(kept, lookup)) {
                // N22-7: the saved judgment stays. An inactive one searches like before.
                return new Result(Kind.SAVED, null, prior == Prior.ACTIVE, growth);
            }
            TankValidation keptTank = TankStructure.validateLoaded(kept, broken, controller, judgedTier);
            if (keptTank.isValid()) {
                return result(Kind.KEPT_VALID, keptTank, true, growth, broken);
            }
        }
        // N29-2: no kept tank, or it is not valid: a rectangle with all its cells loaded is recognized now.
        TankSearchResult loaded = TankStructure.findLoadedTank(controllerX, controllerY, lookup);
        if (loaded.getTank() != null) {
            return result(Kind.FOUND_LOADED, loaded.getTank(), true, growth, lookup);
        }
        boolean settled = loaded.getStatus() == TankSearchResult.Status.CONTROLLER_IN_SHARED_WALL
                || !loaded.hasUnloadedCandidates();
        if (kept != null && mode != Mode.SEARCH) {
            // The kept tank is not valid with its loaded cells: inactive.
            return new Result(Kind.KEPT_INVALID, null, settled, growth);
        }
        return new Result(Kind.WAITING, null, settled, Collections.<GridPos>emptyList());
    }

    /** A judgment with a valid tank, unless another controller's tank contests one of its valves (N29-8). */
    private static Result result(Kind kind, TankValidation tank, boolean settled, List<GridPos> growth,
                                 TankCellLookup lookup) {
        if (tank != null && isContested(tank, lookup)) {
            return new Result(Kind.CONTESTED, null, true, growth);
        }
        return new Result(kind, tank, settled, growth);
    }

    /**
     * N29-8: whether a valve of {@code tank} that belongs to no tank yet is also in the border of the
     * valid tank of another controller ({@link TankStructure#findTanksWithBorderCell}). The same for
     * both controllers, so neither tank is recognized whichever judges first.
     */
    static boolean isContested(TankValidation tank, TankCellLookup lookup) {
        for (GridPos valve : tank.getValves()) {
            if (TankStructure.effectiveValveOwner(valve.x, valve.y, lookup.getCell(valve.x, valve.y), lookup) != null) {
                // Already a tank's: N13-3, N15-3.
                continue;
            }
            for (TankValidation other : TankStructure.findTanksWithBorderCell(valve.x, valve.y, lookup)) {
                if (!other.getController().equals(tank.getController())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The loaded interior cells of {@code bounds} holding natural growth, in reading order. */
    static List<GridPos> naturalGrowth(TankBounds bounds, TankCellLookup lookup) {
        List<GridPos> cells = new ArrayList<>();
        for (int y = bounds.y + 1; y < bounds.getMaxY(); y++) {
            for (int x = bounds.x + 1; x < bounds.getMaxX(); x++) {
                if (!lookup.isLoaded(x, y)) {
                    continue;
                }
                TankCell cell = lookup.getCell(x, y);
                if (cell != null && cell.isNaturalGrowth()) {
                    cells.add(new GridPos(x, y));
                }
            }
        }
        return cells;
    }

    /** {@code lookup} with the natural growth on {@code cells} broken. */
    private static TankCellLookup withNaturalGrowthBroken(final TankCellLookup lookup, List<GridPos> cells) {
        final Set<GridPos> brokenCells = new HashSet<>(cells);
        return new TankCellLookup() {
            @Override
            public TankCell getCell(int tileX, int tileY) {
                TankCell cell = lookup.getCell(tileX, tileY);
                return cell != null && brokenCells.contains(new GridPos(tileX, tileY)) ? cell.withNaturalGrowthBroken() : cell;
            }

            @Override
            public boolean isLoaded(int tileX, int tileY) {
                return lookup.isLoaded(tileX, tileY);
            }
        };
    }

}
