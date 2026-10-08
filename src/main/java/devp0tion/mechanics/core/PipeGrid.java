package devp0tion.mechanics.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The pipe engine of one level (N7-1, N21-1): an ECS-style world over the pipes, tank valves and
 * pumps, the networks the fluid forms, and the systems that move fluid.
 *
 * <h2>Entities, components, world (N22-1, N22-2)</h2>
 * A block cell, (x, y, layer), is an entity. Its components are the data the game keeps in object
 * entities (and, for underground pipes, in their holders): {@link PipeNode}, {@link Pump} and
 * {@link TankStorage} extend {@link LiquidStorage} as before, valves are {@link TankValve}. This
 * world only indexes the cells of loaded regions, references their components, and keeps derived
 * data: the networks, the click queue, the network summaries. Systems run from one place,
 * {@link #runTick}, in a fixed order (N22-5); structure changes (placing, removing, wrench, a pipe
 * breaking) apply at once (N22-4).
 *
 * <h2>Links</h2>
 * Every part has link flags ({@link LinkFlags}); a link needs both facing flags open. The wrench
 * toggles them ({@link #toggleSide}, {@link #toggleVertical}); a flag stays when the other side is
 * removed (N16-4).
 * <ul>
 *     <li>Pipes of the same layer on adjacent tiles link automatically (9-4).</li>
 *     <li>The basic and the underground pipe on one tile link only through the wrench: the pipe
 *     placed second starts with its vertical link cut (N16-4, 9-5 partly replaced).</li>
 *     <li>A basic pipe links to tank valves on adjacent tiles (2-3); an underground pipe only to the
 *     valve on its own tile (9-9), automatically, cut and linked again by the wrench (13-5).</li>
 *     <li>A pump pushes only into adjacent basic pipes, never into underground pipes (9-3, 9-9).</li>
 *     <li>A valve linked to a pump's side is one of the pump's sources (11-1 ②, 11-5), never a
 *     destination. A valve placed next to an existing pump starts with that link cut (N13-3,
 *     N16-3). Linking it with the wrench is refused when its tank holds another fluid than the
 *     pump's other sources (N16-3, {@link Check#DIFFERENT_SOURCE_FLUID}). A valve switched off by a
 *     wire signal is no source while it is off (N27-4).</li>
 *     <li>A valve in a wall shared by two recognized tanks counts as a plain wall (N33-1,
 *     {@link #setValvePlainWall}): no link to it counts, from pipes, the underground pipe on its tile
 *     or pumps, so it is neither a destination nor a source; its flags stay for when it is a valve
 *     again. The pumps next to it handle it like a valve switched off by a wire signal (N27-4): its
 *     source slot keeps its place in the pull order and is skipped while it is a plain wall, and it
 *     is pulled from in that place when it is a valve again, dormant while its tank holds another
 *     fluid than the baseline (N35-1, replacing N33-16, N33-21 and N33-23; N17-3, N20-3).</li>
 * </ul>
 * Pipes may always be placed and linked: where two fluids meet, the face is simply not used, a
 * dead end (N13-2; {@link #getFluidBlockedSides}, so the game can draw it).
 *
 * <h2>Networks (N13-1, N18-2, N18-3, N21-1)</h2>
 * A network ({@link PipeNetwork}) is the set of pipes the fluid has actually reached, linked and
 * holding the same fluid, with the pumps pushing that fluid into them. An empty pipe belongs to no
 * network. A new pump starts a network of its own; when its fluid reaches a pipe of another network
 * of the same fluid, the networks merge. Structure changes rebuild the networks around them. A
 * pipe whose region unloads leaves its network without a rebuild, and joins the networks around it
 * again when it loads.
 *
 * <h2>Cell hints (N22-3, N24-2, N25-1, N25-4, N25-6, N28-12, N28-13, N28-15)</h2>
 * Instead of a route cache per pump, each pipe keeps, per destination valve, the direction of the
 * next step toward it ({@link PipeNode#getHint}), no distance. A pump steps along them from its
 * output cells; the step count, when needed, is counted while stepping.
 * <ul>
 *     <li>A destination's hints come from a search back from its valve over the pipes the fluid can
 *     pass (empty, or holding one fluid along the way), loaded ones only. Ties of equal length go
 *     by the search order (north, east, south, west, then the other layer).</li>
 *     <li>A structure change marks destinations for a new search (N25-4): a removed pipe or a cut
 *     link, those whose hints step across it; a new link, or a new pipe linking two or more pipes,
 *     those with hints on the pipes it links (it may join or shorten paths); a valve's link or a new
 *     valve, that valve. A change inside a full stretch (at a pipe holding fluid, so in a network)
 *     marks every destination of that network, so a shortcut is taken at once (N28-12). A new pipe
 *     linked to one pipe only is a dead-end leaf and takes that pipe's hints, marking nothing (it
 *     changes no path). Marked destinations are searched again when they are used next: at the
 *     next pump cycle, before the pump reads its destinations. A loaded valve whose pipes already
 *     hold its saved hints is not searched (no full recompute on the first cycle, N25-1).
 *     TODO(confirm): a stale destination is searched at the next pump cycle of any pump, since a
 *     pump cannot step toward a destination it does not know yet (a new valve, a new connection).</li>
 *     <li>Hints are repaired when used: stepping reads every cell, so a missing pipe, a cut link or a
 *     loop sends the destination to a new search, and a cell of another fluid is a dead end for that
 *     push (N13-2). Stepping always ends: hints saved at different times can form a loop, so one
 *     stepping pass takes at most as many steps as there are loaded pipes, plus one; past that the
 *     destination is searched again (N28-13). TODO(confirm): that bound.</li>
 *     <li>When a region loads, each loaded pipe's saved hints are checked on that pipe only
 *     (N28-13): a direction must point at a pipe linked to it, or be the last step into the
 *     destination valve's tile. A bad hint is dropped and its destination searched again when used
 *     (N25-1). A direction toward a tile that is not loaded cannot be checked and stays. There are no
 *     save numbers per region, so a region file older than the rest (a crash, a restored backup)
 *     goes unnoticed, by the summary check too (N28-1).</li>
 *     <li>A destination in a pipe's hints is a number in the table of its group of linked pipes
 *     ({@link HintTable}, N28-15): merges and splits renumber the loaded pipes, a pipe of an unloaded
 *     region keeps its table's id and is renumbered when it loads.</li>
 *     <li>TODO(confirm): the hint codes are the four directions plus one for the other layer on the
 *     same tile (basic to underground pipe, underground pipe to the valve on its tile), so a code
 *     takes 3 bits rather than 2 (N24-2).</li>
 *     <li>TODO(confirm): N25-1 "hints are built as the fluid fills empty pipes" is read as: the
 *     search back from the valve covers empty pipes too, and the fluid then fills along those hints
 *     step by step, as far as the cap allows (N14-2; no movement speed, N32-1). The other connected
 *     empty pipes fill as well (N28-6, below).</li>
 * </ul>
 *
 * <h2>Pushing (N7, N12, N14, N18-1, N23-1, N24-3, N26-4, N28-5, N28-6, N28-8, N28-14, N32-1)</h2>
 * <ul>
 *     <li>A pump's destinations are the valves its output cells have hints for, reached through
 *     pipes that are empty or hold its fluid, whose tank has room (the pump's own source tanks
 *     excluded), each along its hints from the nearest output cell (the pump's first pipe = 1).</li>
 *     <li>Dead ends (N28-6): the fluid also fills every other connected empty pipe, dead-end
 *     branches included, not only the destinations' paths. Each end of a dead-end branch (a loaded
 *     pipe off those paths with nothing further to fill) gets a path from the pump, like a
 *     destination without a tank ({@link #branchRoutes}), while its branch is not full.</li>
 *     <li>Distribution (N24-3, N28-8): the pushed amount is split where the paths part. While
 *     filling, a junction gives every open direction one equal amount, a dead-end direction too
 *     (N28-8); otherwise it splits by the number of destinations behind each direction (N24-3).
 *     What does not divide evenly goes one unit per direction (N28-8) or per destination (N24-3),
 *     directions taken in the fixed order: the other layer (vertical) first, then north, east,
 *     south, west (N25-5, N27-1, N28-5). A junction counts only the destinations the fluid arriving
 *     there carries: the amount starts at the output cell with its destinations and the set narrows
 *     at each junction, so a loop never counts a destination twice (N28-14). TODO(confirm): "while
 *     filling" is read per junction: some pipe after the junction, on a path that leaves it, is not
 *     full yet. An amount a destination cannot take is split again among the others.</li>
 *     <li>Each share fills the pipes along its path, then enters the tank. Only the frontier (the
 *     first pipe not yet full) is written; full pipes are never written again (N7-1).</li>
 *     <li>No movement speed (N32-1): filling empty pipes is limited only by the cap below (N14-2).
 *     The cap equals a pipe's capacity, so a pipe filled from empty uses its whole cap and a path's
 *     fluid enters at most one empty pipe per cycle window; full stretches pass within the cycle.
 *     The per-tier fill speed and what it held back in the pump (N25-2, N25-3, N28-7, N28-9~N28-11)
 *     are discarded by N32-1.</li>
 *     <li>Each pipe lets at most its transport amount through per cycle window of
 *     {@link #CYCLE_TICKS} ticks, counted while stepping each share from the output cell to its end
 *     (N14-2, N23-1). Pumps pushing through the same pipe add up and share it (N18-1): among pumps
 *     whose cycles run in the same tick, the pump nearer to the shared pipe (fewer steps) goes
 *     first, equal ones by connection order: the pumps' placement order (N26-4, N28-17). The
 *     log-fueled pumps' cycles are aligned to the same ticks (N28-16, {@link #runTick}). A manual
 *     pump's click pushes in the next tick (N28-21): within a cycle window, whoever pushes first takes
 *     a cell's cap first.</li>
 *     <li>When the fluid reaches a new pipe, the tier conditions are judged against the lowest tier
 *     of the network it comes from and that pipe (N12-4, N14-1); if they fail, only that pipe breaks
 *     and only the share headed into it is lost.</li>
 *     <li>No destination at all: the pump stops (N7-4 as N28-6 reads it). While it has one, it
 *     keeps filling the empty pipes connected to it even when every tank is full; with every tank
 *     and pipe full nothing can move, and the cycle stops the same way.</li>
 * </ul>
 *
 * <h2>Unloaded regions: the network summary (N14-3, N15-1, N23-2, N28-1~N28-4)</h2>
 * The engine holds no mirror of unloaded pipes. Instead, each pump's path to each destination is
 * summarized ({@link RouteSummary}): the pump (source position), the valve (last output position)
 * and the regions passed through, as runs of path cells with their count, lowest transport amount
 * and tier, whether they were full, and the region's structure change number when written.
 * <ul>
 *     <li>Written (N28-2): every cycle that pushed along the path to its valve with every cell of
 *     the path loaded (a normal run) writes the whole summary again. A cycle that skips an unloaded
 *     stretch changes nothing in it. TODO(confirm): "pushed through to the end" is read as: the
 *     cycle pushed and the path it stepped reached the valve, whether or not fluid entered the tank
 *     in that cycle.</li>
 *     <li>Intact (N28-1): a summary is intact while every region it passes still has the structure
 *     change number written in it ({@link #getRegionChange}). Placing, breaking or wrenching a pipe,
 *     pump or valve, or a pipe breaking, raises its region's number: that marks the summary entries
 *     of that region invalid, without writing them again (N28-2). Changes in regions the path does
 *     not pass do not matter. So an edit inside a passed region stops the flow beyond an unloaded
 *     stretch until a normal run writes the summary again.</li>
 *     <li>While a summary is intact a path may skip an unloaded stretch whose runs were full of its
 *     fluid, also several in a row; any other unloaded cell is a dead end (N14-3, N15-1).</li>
 *     <li>Inside a skipped stretch (N28-3) the cap and the split follow the same rules as for loaded
 *     pipes: each run keeps the lowest transport amount of its cells (its cap counter is shared by
 *     the pumps that skip it), and the runs are cut where the pump's destinations' paths part, so a
 *     junction inside the stretch splits by the destinations behind each direction as when loaded.
 *     A skipped stretch is full, so nothing there is filling (N28-8).</li>
 *     <li>A destination whose valve is in an unloaded cell is a dead end (N28-4, N14-3): its share
 *     goes to the other destinations; with none left the pump stops (N7-4).</li>
 * </ul>
 *
 * <p>The engine is verified by tests of its own rules only (N28-20, replacing the comparison with
 * the engine before the ECS restructure, N26-1).
 */
public final class PipeGrid implements PumpHost {

    /** Result of a placement or wrench check. */
    public enum Check {
        OK,
        /** The layer is already taken on that tile (one object per layer, D1). */
        OCCUPIED,
        /** No part there to act on. */
        NOTHING_THERE,
        /**
         * The pump's sources would hold different fluids: pump placement (N17-1) or linking a
         * valve to a pump with the wrench (N16-3).
         * TODO(design): a multi-fluid tank would make this case possible (N16-3).
         */
        DIFFERENT_SOURCE_FLUID,
        /**
         * A part the wrench would change is in an unloaded region: nothing changed. The game loads
         * that region first, so the change reaches the region's own state.
         */
        NOT_LOADED
    }

    /** The parts that carry link flags. */
    public enum Part {
        BASIC_PIPE,
        UNDERGROUND_PIPE,
        VALVE,
        PUMP
    }

    /** Told about every change the game saves or syncs. */
    public interface Listener {
        void onLinksChanged(int tileX, int tileY, Part part);

        /**
         * The pipe at the tile started or stopped holding fluid (reached, removed or loaded with
         * other contents), so the faces it shares with the pipes next to it may have become or
         * stopped being dead ends between two fluids ({@link #getFluidBlockedSides}).
         */
        default void onPipeFluidChanged(int tileX, int tileY, PipeLayer layer) {
        }

        /** A pump ran a cycle from {@link #runTick} (broken pipes are in the result, N12-5). */
        default void onPumpCycle(int tileX, int tileY, PumpResult result) {
        }
    }

    /** Whether a tile's region is loaded (the game's region manager). */
    public interface TileLoadedLookup {
        boolean isTileLoaded(int tileX, int tileY);
    }

    /** The cycle window of the transport cap: one pump cycle, 20 ticks (N6-1, N14-2). */
    public static final int CYCLE_TICKS = 20;

    /** {@link Direction#values()} without a copy per call (technical). */
    private static final Direction[] DIRS = Direction.values();

    private static final Listener NO_LISTENER = new Listener() {
        @Override
        public void onLinksChanged(int tileX, int tileY, Part part) {
        }
    };

    private final PipeTierRules tierRules;
    private Listener listener = NO_LISTENER;
    private TileLoadedLookup loadedLookup;

    // World: the cell index and the references to the components (N22-2).
    private final Map<Long, PipeNode> basePipes = new HashMap<>();
    private final Map<Long, PipeNode> undergroundPipes = new HashMap<>();
    private final Map<Long, TankValve> valves = new HashMap<>();
    private final Map<TankValve, Long> valvePositions = new IdentityHashMap<>();
    /** Pumps by tile; the cap order goes by their install numbers (N26-4, N28-17). */
    private final Map<Long, Pump> pumps = new LinkedHashMap<>();
    /** The next install number (N28-17): level-wide placement order, saved with the level. */
    private long nextInstall;
    private final Set<PipeNetwork> networks = new LinkedHashSet<>();
    /** Tiles of parts that left because their region unloaded, while no {@link #loadedLookup} is set. */
    private final Set<Long> unloadedTiles = new HashSet<>();
    private long tick;

    // Hints (derived data, saved by the pipes' holders, N25-6).
    /** The destination number tables of the pipe groups, by id (N28-15); saved with the level. */
    private final Map<Integer, HintTable> hintTables = new LinkedHashMap<>();
    private int nextTableId;
    private final Map<Long, Set<PipeNode>> hintCells = new HashMap<>();
    private final Set<Long> staleDestinations = new LinkedHashSet<>();
    private final Set<Long> checkDestinations = new LinkedHashSet<>();
    private final Map<Long, Long> searchedAt = new HashMap<>();
    /** Pipes loaded with saved hints, to check before the hints are next used (N28-13). */
    private final Set<PipeNode> loadedToCheck = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
    /** Number of searches back from a valve so far (tests, benchmarks). */
    int searches;

    // Network summaries (N23-2): pump tile -> destination -> summary.
    private final Map<Long, Map<Long, RouteSummary>> summaries = new LinkedHashMap<>();
    private final Map<Long, Set<Long>> summaryPumpsByDestination = new HashMap<>();
    private final Map<RunKey, long[]> runFlow = new HashMap<>();

    // The routes each pump stepped, reused while nothing in the world changed (technical: any change
    // of cells, links, hints, contents, valves or pumps counts, see changed()).
    private long changes;
    private final Map<Pump, RouteMemo> routeMemo = new IdentityHashMap<>();

    /** The routes a pump stepped for one fluid, valid while {@link #changes} is unchanged. */
    private static final class RouteMemo {
        final FluidType fluid;
        final long changes;
        final List<Route> routes;
        /** The dead-end ends (N28-6) worked out for these destinations, while the memo holds. */
        List<Route> branchDestinations;
        List<Route> branches;

        RouteMemo(FluidType fluid, long changes, List<Route> routes) {
            this.fluid = fluid;
            this.changes = changes;
            this.routes = routes;
        }
    }

    // The last cap order (N26-4), reused for the same due pumps while nothing changed (technical).
    private List<Pump> lastDue = Collections.emptyList();
    private List<Pump> lastOrder = Collections.emptyList();
    private long lastOrderChanges = -1;

    /** The stamps of the dry runs (technical, {@code DryLedger}). */
    private long dryStamps;
    /** The {@link #changes} at each pump's last recorded summary (technical). */
    private final Map<Pump, Long> summaryRecordedAt = new IdentityHashMap<>();
    /**
     * The structure change number of each region (N28-1): raised by every structure change there
     * (placing, removing, breaking or wrenching a pipe, pump or valve), never by a region loading or
     * unloading. A summary run keeps its region's number when it is written and is invalid once they
     * differ (N28-2). The game saves the numbers with the level, next to the summaries
     * ({@link #getSavedRegionChanges}): the number is compared while the region is unloaded, when its
     * own file cannot be read, and the level file is written together with the summaries, so the two
     * always match. In this map, the numbers also outlive any region reload.
     */
    private final Map<Long, Integer> regionChanges = new HashMap<>();

    // Manual pump clicks waiting for the next tick (N22-5).
    private final List<Long> clickQueue = new ArrayList<>();

    public PipeGrid(PipeTierRules tierRules) {
        this.tierRules = Objects.requireNonNull(tierRules, "tierRules");
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? NO_LISTENER : listener;
    }

    public PipeTierRules getTierRules() {
        return tierRules;
    }

    /**
     * Where the engine asks whether a tile's region is loaded (the game's region manager). Without
     * one, a tile counts as unloaded from the moment a part on it unloads until a part on it loads.
     */
    public void setTileLoadedLookup(TileLoadedLookup lookup) {
        this.loadedLookup = lookup;
    }

    /** Whether the tile's region is loaded. */
    public boolean isTileLoaded(int x, int y) {
        return loadedLookup != null ? loadedLookup.isTileLoaded(x, y) : !unloadedTiles.contains(key(x, y));
    }

    // ---------------------------------------------------------------- clock and systems

    /** One game tick of the clock only (the cycle windows of the transport cap, N14-2). */
    public void tick() {
        tick++;
    }

    public long getTick() {
        return tick;
    }

    long window() {
        return Math.floorDiv(tick, CYCLE_TICKS);
    }

    /**
     * Queues a click on the manual pump at the tile (11-3): it runs in the next {@link #runTick}
     * (N22-5), at most one cycle per click cooldown (N3-3).
     */
    public void queueClick(int x, int y) {
        clickQueue.add(key(x, y));
    }

    /**
     * One game tick of every system, in a fixed order (N22-5). The game calls it from the pipe
     * system's level data tick, after the entity ticks; object entities run nothing themselves.
     * <ol>
     *     <li>Clock (cycle windows, N14-2).</li>
     *     <li>Timers: lit logs burn down (N18-4), cycle counters advance (N6-1, N3-3), wire state is
     *     the pump's {@code enabled} (N11-3). Every log-fueled pump of the level pushes on the same
     *     ticks (N28-16): one global phase, every cycle, when the engine tick is a multiple of the
     *     cycle (20 ticks); a pump placed or loaded in between waits for the next one, and merges or
     *     splits need no realignment.</li>
     *     <li>Clicks queued since the last tick: a manual pump's click pushes in the next tick, not
     *     on the global push tick (N28-21, N22-5).</li>
     *     <li>Push: the pumps due now, ordered for the shared caps (N26-4). Hints are repaired as the
     *     pumps use them (N25-4).</li>
     * </ol>
     *
     * @return the results of the cycles that ran, by pump tile
     */
    public Map<Long, PumpResult> runTick() {
        tick();
        List<Pump> due = new ArrayList<>();
        Set<Pump> clicked = Collections.newSetFromMap(new IdentityHashMap<Pump, Boolean>());
        for (Pump pump : new ArrayList<>(pumps.values())) {
            PumpResult timers = pump.advanceTimers(Math.floorMod(tick, pump.getTier().getCycleTicks()) == 0);
            if (timers == null) {
                due.add(pump);
            }
        }
        for (long position : clickQueue) {
            Pump pump = pumps.get(position);
            if (pump != null && pump.getTier().getPower() == PumpTier.Power.HAND_CLICK && clicked.add(pump)) {
                due.add(pump);
            }
        }
        clickQueue.clear();
        Map<Long, PumpResult> results = new LinkedHashMap<>();
        for (Pump pump : orderForCaps(due)) {
            if (pump.host != this) {
                continue;
            }
            PumpResult result = clicked.contains(pump) ? pump.click() : pump.runCycle();
            results.put(key(pump.getTileX(), pump.getTileY()), result);
            listener.onPumpCycle(pump.getTileX(), pump.getTileY(), result);
        }
        return results;
    }

    /**
     * The order of the pumps whose cycles run in this tick (N26-4): where two of them push through
     * the same pipe, the one with fewer steps to it goes first, equal ones by connection order; the
     * pipe compared is the shared one nearest to both. Pumps sharing nothing keep connection order.
     */
    List<Pump> orderForCaps(List<Pump> due) {
        List<Pump> byConnection = new ArrayList<>(due);
        byConnection.sort(Comparator.comparingLong(this::connectionOf));
        if (byConnection.size() < 2) {
            return byConnection;
        }
        if (byConnection.equals(lastDue) && lastOrderChanges == changes) {
            return new ArrayList<>(lastOrder);
        }
        int n = byConnection.size();
        // Every cell the due pumps' paths cross, with the fewest steps of each pump to it: marked
        // on the pipes themselves (runs of unloaded stretches in a map).
        long stamp = ++capStamps;
        List<PipeNode> touched = new ArrayList<>();
        Map<Object, List<int[]>> crossing = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Pump pump = byConnection.get(i);
            FluidType fluid = pump.cycleFluid();
            if (fluid == null) {
                continue;
            }
            for (Route route : routesFor(pump, fluid)) {
                for (int e = 0; e < route.path.length; e++) {
                    int steps = route.stepsTo[e];
                    if (route.path[e] instanceof PipeNode) {
                        PipeNode node = (PipeNode) route.path[e];
                        if (node.capStamp != stamp) {
                            node.capStamp = stamp;
                            node.capCount = 0;
                            touched.add(node);
                        }
                        node.addCapEntry(i, steps);
                    } else {
                        Object key = route.element(e);
                        List<int[]> pumpsHere = crossing.get(key);
                        if (pumpsHere == null) {
                            pumpsHere = new ArrayList<>(2);
                            crossing.put(key, pumpsHere);
                        }
                        addMin(pumpsHere, i, steps);
                    }
                }
            }
        }
        for (PipeNode node : touched) {
            if (node.capCount > 1) {
                List<int[]> pumpsHere = new ArrayList<>(node.capCount);
                for (int k = 0; k < node.capCount; k++) {
                    pumpsHere.add(new int[]{node.capPumps[k], node.capSteps[k]});
                }
                crossing.put(node, pumpsHere);
            }
        }
        // Per pair: the shared cell nearest to both (smallest sum of steps), and their steps there.
        int[][] bestSum = new int[n][n];
        int[][] stepsI = new int[n][n];
        int[][] stepsJ = new int[n][n];
        for (int[] row : bestSum) {
            java.util.Arrays.fill(row, Integer.MAX_VALUE);
        }
        for (List<int[]> pumpsHere : crossing.values()) {
            for (int a = 0; a < pumpsHere.size(); a++) {
                for (int b = a + 1; b < pumpsHere.size(); b++) {
                    int[] first = pumpsHere.get(a)[0] < pumpsHere.get(b)[0] ? pumpsHere.get(a) : pumpsHere.get(b);
                    int[] second = first == pumpsHere.get(a) ? pumpsHere.get(b) : pumpsHere.get(a);
                    int i = first[0];
                    int j = second[0];
                    int sum = first[1] + second[1];
                    if (sum < bestSum[i][j]) {
                        bestSum[i][j] = sum;
                        stepsI[i][j] = first[1];
                        stepsJ[i][j] = second[1];
                    }
                }
            }
        }
        List<List<Integer>> after = new ArrayList<>();
        int[] before = new int[n];
        for (int i = 0; i < n; i++) {
            after.add(new ArrayList<Integer>());
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (bestSum[i][j] == Integer.MAX_VALUE) {
                    continue;
                }
                // i is connected first: it goes first unless j is nearer to the shared pipe.
                if (stepsJ[i][j] < stepsI[i][j]) {
                    after.get(j).add(i);
                    before[i]++;
                } else {
                    after.get(i).add(j);
                    before[j]++;
                }
            }
        }
        List<Pump> result = new ArrayList<>(n);
        boolean[] done = new boolean[n];
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < n; i++) {
            if (before[i] == 0) {
                ready.add(i);
            }
        }
        while (result.size() < n) {
            if (ready.isEmpty()) {
                // A cycle (opposite flows through shared pipes): connection order breaks it.
                for (int i = 0; i < n; i++) {
                    if (!done[i]) {
                        ready.add(i);
                        break;
                    }
                }
            }
            int i = ready.poll();
            if (done[i]) {
                continue;
            }
            done[i] = true;
            result.add(byConnection.get(i));
            for (int j : after.get(i)) {
                if (--before[j] == 0 && !done[j]) {
                    ready.add(j);
                }
            }
        }
        lastDue = byConnection;
        lastOrder = new ArrayList<>(result);
        lastOrderChanges = changes;
        return result;
    }

    private static void addMin(List<int[]> pumpsHere, int pump, int steps) {
        for (int[] entry : pumpsHere) {
            if (entry[0] == pump) {
                entry[1] = Math.min(entry[1], steps);
                return;
            }
        }
        pumpsHere.add(new int[]{pump, steps});
    }

    /** The stamps of the cap orders (technical, {@link #orderForCaps}). */
    private long capStamps;

    /** The connection order tie-break: the install number, the placement order (N28-17). */
    private long connectionOf(Pump pump) {
        long number = pump.getInstallNumber();
        return number < 0 ? Long.MAX_VALUE : number;
    }

    // ---------------------------------------------------------------- queries

    public PipeNode getPipe(int x, int y, PipeLayer layer) {
        return pipes(layer).get(key(x, y));
    }

    @Override
    public TankValve getValve(int x, int y) {
        return valves.get(key(x, y));
    }

    public Pump getPump(int x, int y) {
        return pumps.get(key(x, y));
    }

    /** Every pipe of the loaded regions. */
    public List<PipeNode> getPipes() {
        List<PipeNode> result = new ArrayList<>(basePipes.size() + undergroundPipes.size());
        result.addAll(basePipes.values());
        result.addAll(undergroundPipes.values());
        return result;
    }

    /** Every pump, in connection order. */
    public List<Pump> getPumps() {
        return new ArrayList<>(pumps.values());
    }

    /** The network of the pipe at the tile, or {@code null} if there is no pipe or it is empty (N13-1). */
    public PipeNetwork getNetwork(int x, int y, PipeLayer layer) {
        PipeNode node = getPipe(x, y, layer);
        return node == null ? null : node.network;
    }

    public List<PipeNetwork> getNetworks() {
        return Collections.unmodifiableList(new ArrayList<>(networks));
    }

    /**
     * The faces of the pipe at the tile where it meets a pipe holding another fluid than it does
     * ({@link LinkFlags} bits): a side bit when the neighbouring pipe of the same layer does, the
     * vertical bit when the pipe of the other layer on its tile does. Those faces are dead ends
     * whatever their link flags say (N13-2). 0 when there is no pipe or it is empty.
     */
    public int getFluidBlockedSides(int x, int y, PipeLayer layer) {
        PipeNode node = getPipe(x, y, layer);
        FluidType fluid = node == null ? null : node.getFluid();
        if (fluid == null) {
            return 0;
        }
        int sides = 0;
        for (Direction d : DIRS) {
            if (holdsOtherFluid(getPipe(x + d.dx, y + d.dy, layer), fluid)) {
                sides |= LinkFlags.bit(d);
            }
        }
        if (holdsOtherFluid(getPipe(x, y, layer.other()), fluid)) {
            sides |= LinkFlags.VERTICAL;
        }
        return sides;
    }

    private static boolean holdsOtherFluid(PipeNode node, FluidType fluid) {
        return node != null && node.getFluid() != null && node.getFluid() != fluid;
    }

    /** Whether two pipes are linked (both facing flags open, 9-4, N16-4). Fluids are not considered. */
    public boolean areLinked(PipeNode a, PipeNode b) {
        return linkedPipes(a).contains(b);
    }

    /** Valves the pipe is linked to (2-3, 9-9, 13-4, 13-5); a valve that is a plain wall is none (N33-1). */
    public List<TankValve> getLinkedValves(PipeNode node) {
        List<TankValve> result = new ArrayList<>();
        if (node.getLayer() == PipeLayer.BASE) {
            for (Direction d : DIRS) {
                TankValve valve = valves.get(key(node.getTileX() + d.dx, node.getTileY() + d.dy));
                if (valve != null && node.isSideOpen(d) && valve.linksSide(d.opposite())) {
                    result.add(valve);
                }
            }
        } else {
            TankValve valve = valves.get(key(node.getTileX(), node.getTileY()));
            if (valve != null && node.isVerticalOpen() && valve.linksVertical()) {
                result.add(valve);
            }
        }
        return result;
    }

    /** The basic pipes a pump pushes into (9-3, 9-9, 13-4), in {@link Direction} order. */
    public List<PipeNode> getPumpEntries(Pump pump) {
        List<PipeNode> result = new ArrayList<>();
        for (Direction d : DIRS) {
            PipeNode node = basePipes.get(key(pump.getTileX() + d.dx, pump.getTileY() + d.dy));
            if (node != null && pump.isSideOpen(d) && node.isSideOpen(d.opposite())) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * The fluids in a pump's output cells, the basic pipes it pushes into ({@link #getPumpEntries}),
     * in {@link Direction} order; empty pipes left out. Read per cell, never from the network (N20-5).
     */
    @Override
    public Set<FluidType> getOutputFluids(Pump pump) {
        Set<FluidType> result = new LinkedHashSet<>();
        for (PipeNode entry : getPumpEntries(pump)) {
            if (entry.getFluid() != null) {
                result.add(entry.getFluid());
            }
        }
        return result;
    }

    /**
     * The valves a pump pulls through: linked to its sides (11-1 ②, N16-3), in pull order, not
     * switched off by a wire signal (N27-4) and not a plain wall (N33-1). Like a valve that is off, a
     * plain wall keeps its slot in the pump's sources and is skipped there (N35-1).
     */
    public List<TankValve> getSourceValves(Pump pump) {
        List<TankValve> result = new ArrayList<>();
        for (Pump.SourceSlot slot : pump.getSourceSlots()) {
            if (slot.direction != null && isPumpValveLinked(pump, slot.direction)) {
                TankValve valve = valves.get(key(pump.getTileX() + slot.direction.dx, pump.getTileY() + slot.direction.dy));
                if (valve.isEnabled()) {
                    result.add(valve);
                }
            }
        }
        return result;
    }

    /**
     * Whether a pump and the valve on its side {@code direction} are linked (N16-3): both flags
     * toward each other open, and the valve no plain wall (N33-1). A valve that is a plain wall keeps
     * its slot in the pump's sources, skipped while it is one, and is pulled from in that place when
     * it is a valve again (N35-1, like a valve switched off by a wire signal, N27-4).
     */
    @Override
    public boolean isPumpValveLinked(Pump pump, Direction direction) {
        TankValve valve = valves.get(key(pump.getTileX() + direction.dx, pump.getTileY() + direction.dy));
        return valve != null && pump.isSideOpen(direction) && valve.linksSide(direction.opposite());
    }

    // ---------------------------------------------------------------- pipes

    /** Whether a pipe can be placed: only the layer must be free (fluids never block, N13-2). */
    public Check checkPipePlacement(int x, int y, PipeLayer layer) {
        long key = key(x, y);
        boolean taken = layer == PipeLayer.BASE ? isBaseLayerTaken(key) : undergroundPipes.containsKey(key);
        return taken ? Check.OCCUPIED : Check.OK;
    }

    /**
     * Places a new, empty pipe of its tier's capacity (N12-3). Its sides start open (9-4); its
     * vertical link starts cut when the other layer already has a pipe on the tile (N16-4), and
     * open under a valve (9-9).
     *
     * @throws IllegalStateException if {@link #checkPipePlacement} is not {@link Check#OK}
     */
    public PipeNode placePipe(int x, int y, PipeLayer layer, MineralTier tier) {
        Objects.requireNonNull(tier, "tier");
        Check check = checkPipePlacement(x, y, layer);
        if (check != Check.OK) {
            throw new IllegalStateException("Cannot place " + layer + " pipe at " + x + "," + y + ": " + check);
        }
        PipeNode node = newNode(x, y, layer, tier);
        if (getPipe(x, y, layer.other()) != null) {
            node.setVerticalOpen(false);
        }
        unloadedTiles.remove(key(x, y));
        putCell(node);
        joinGroup(node, null);
        onPipePlaced(node);
        structureChanged(x, y);
        return node;
    }

    /** {@link #loadPipe(int, int, PipeLayer, MineralTier, int, FluidType, int, boolean, long[], byte[])} without hints. */
    public PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                             boolean loaded) {
        return loadPipe(x, y, layer, tier, links, fluid, amount, loaded, null, null);
    }

    /**
     * {@link #loadPipe(int, int, PipeLayer, MineralTier, int, FluidType, int, boolean, long[], byte[])}
     * with hints as saved by the game (N28-15): numbers in the table {@code hintTable}. The pipe is
     * renumbered into its group's table; a number the table no longer knows (it was dropped) gives
     * no hint, repaired when used (N25-1).
     */
    public PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                             int hintTable, int[] hintNumbers, byte[] hintCodes) {
        HintTable saved = hintTables.get(hintTable);
        long[] dests = null;
        if (hintNumbers != null) {
            dests = new long[hintNumbers.length];
            for (int i = 0; i < dests.length; i++) {
                dests[i] = saved == null ? Long.MIN_VALUE : saved.destinationOf(hintNumbers[i]);
            }
        }
        PipeNode before = getPipe(x, y, layer);
        PipeNode node = loadPipe(x, y, layer, tier, links, fluid, amount, true, dests, hintCodes, saved);
        if (saved != null && node != before) {
            // One pipe of an unloaded region less refers to it.
            saved.unloadedRefs = Math.max(0, saved.unloadedRefs - 1);
            dropIfUnused(saved);
        }
        return node;
    }

    /**
     * Adds a pipe with saved state, its region being loaded, or updates the pipe there with it (a
     * pipe of the same tier keeps its object, its network is regrouped). Saved hints are kept
     * (N25-6); where they disagree with the pipes around, the destinations concerned are searched
     * again when used. {@code loaded} false adds nothing: this engine holds no mirror of unloaded
     * regions (N23-2); it unloads the pipe there, if any.
     *
     * @return the pipe, or {@code null} when {@code loaded} is false
     */
    public PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                             boolean loaded, long[] hintDests, byte[] hintCodes) {
        return loadPipe(x, y, layer, tier, links, fluid, amount, loaded, hintDests, hintCodes, null);
    }

    private PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                              boolean loaded, long[] hintDests, byte[] hintCodes, HintTable savedTable) {
        Objects.requireNonNull(tier, "tier");
        if (!loaded) {
            unloadPipe(x, y, layer);
            return null;
        }
        long key = key(x, y);
        FluidType contents = amount > 0 ? fluid : null;
        int contentsAmount = contents == null ? 0 : amount;
        PipeNode existing = pipes(layer).get(key);
        if (existing != null && existing.getTier() == tier) {
            if (hintDests != null) {
                unindexHints(existing);
                existing.setHints(hintDests, hintCodes);
                indexHints(existing);
                onCellLoaded(existing);
                changed();
            }
            if (existing.getLinks() == LinkFlags.sanitize(links) && existing.getFluid() == contents
                    && existing.getAmount() == contentsAmount) {
                return existing;
            }
            // The same pipe with other saved state: update it in place and regroup its network.
            changed();
            boolean emptied = existing.getFluid() != null && contents == null;
            PipeNetwork network = existing.network;
            if (network != null) {
                network.nodes.remove(existing);
                existing.network = null;
            }
            existing.setLinks(links);
            existing.setContents(contents, contentsAmount);
            if (network != null) {
                rebuild(Collections.singletonList(network));
            }
            if (existing.isReached()) {
                attachReached(existing);
            } else if (emptied) {
                listener.onPipeFluidChanged(x, y, layer);
            }
            markAffected(x, y);
            return existing;
        }
        if (existing != null) {
            removePipe(x, y, layer);
        }
        if (layer == PipeLayer.BASE && isBaseLayerTaken(key)) {
            throw new IllegalStateException("Cannot load basic pipe at " + x + "," + y + ": base layer taken");
        }
        PipeNode node = newNode(x, y, layer, tier);
        node.setLinks(links);
        if (contents != null) {
            node.setContents(contents, contentsAmount);
        }
        unloadedTiles.remove(key);
        putCell(node);
        joinGroup(node, savedTable);
        node.setHints(hintDests, hintCodes);
        indexHints(node);
        if (node.isReached()) {
            attachReached(node);
        }
        onCellLoaded(node);
        return node;
    }

    /**
     * The pipe's region unloaded: it leaves the engine (no mirror, N23-2) and its network without a
     * rebuild; its state and hints stay with its holder, which saves them.
     *
     * @return the pipe that left, or {@code null}
     */
    public PipeNode unloadPipe(int x, int y, PipeLayer layer) {
        PipeNode node = takeCell(x, y, layer);
        if (node == null) {
            return null;
        }
        if (loadedLookup == null) {
            unloadedTiles.add(key(x, y));
        }
        if (node.network != null) {
            node.network.nodes.remove(node);
            node.network = null;
        }
        unindexHints(node);
        loadedToCheck.remove(node);
        if (node.hintTable != null) {
            // It keeps its table's id in its save (N28-15).
            node.hintTable.cells.remove(node);
            node.hintTable.unloadedRefs++;
        }
        node.loaded = false;
        return node;
    }

    /**
     * Removes a pipe (picked up or broken). Its fluid is lost (N12-1); the network splits as needed
     * and every remaining pipe keeps its fluid.
     *
     * @return the removed pipe, or {@code null} if there was none
     */
    public PipeNode removePipe(int x, int y, PipeLayer layer) {
        PipeNode existing = getPipe(x, y, layer);
        if (existing == null) {
            return null;
        }
        Set<Long> affected = destinationsThrough(existing);
        List<PipeNode> sides = linkedPipes(existing);
        PipeNode node = takeCell(x, y, layer);
        node.removed = true;
        structureChanged(x, y);
        unindexHints(node);
        loadedToCheck.remove(node);
        leaveGroup(node);
        splitIfApart(sides);
        PipeNetwork network = node.network;
        if (network != null) {
            network.nodes.remove(node);
            node.network = null;
            rebuild(Collections.singletonList(network));
        }
        markStale(affected);
        if (node.getFluid() != null) {
            listener.onPipeFluidChanged(x, y, layer);
        }
        return node;
    }

    private PipeNode newNode(int x, int y, PipeLayer layer, MineralTier tier) {
        return new PipeNode(x, y, layer, tier, tierRules.getTransportAmount(tier));
    }

    // ---------------------------------------------------------------- valves

    /** Whether a valve can be placed (base layer free). */
    public Check checkValvePlacement(int x, int y) {
        return isBaseLayerTaken(key(x, y)) ? Check.OCCUPIED : Check.OK;
    }

    /**
     * Places a new valve, a new destination. Its links start open, except toward pumps already next
     * to it: those start cut, so the pumps keep their sources (N13-3, N16-3). A valve placed as a
     * plain wall (N33-1) starts the same way.
     */
    public void placeValve(int x, int y, TankValve valve) {
        addValve(x, y, valve);
        structureChanged(x, y);
        boolean changed = false;
        for (Direction d : DIRS) {
            if (pumps.containsKey(key(x + d.dx, y + d.dy)) && valve.isSideOpen(d)) {
                valve.setSideOpen(d, false);
                changed = true;
            }
        }
        // A new destination; a valve is never passed through, so no other destination changes.
        staleDestinations.add(key(x, y));
        if (changed) {
            listener.onLinksChanged(x, y, Part.VALVE);
        }
    }

    /**
     * Adds a valve with its saved link flags (its region was loaded). Its saved hints are in its
     * pipes; it is searched only when none of its linked pipes holds a hint for it (N25-1). The pumps
     * next to it keep its slot as it is: a valve loaded as a plain wall (N33-1) is skipped there while
     * it is one (N35-1).
     */
    public void loadValve(int x, int y, TankValve valve) {
        addValve(x, y, valve);
        checkDestinations.add(key(x, y));
    }

    private void addValve(int x, int y, TankValve valve) {
        Objects.requireNonNull(valve, "valve");
        long key = key(x, y);
        if (isBaseLayerTaken(key) || valvePositions.containsKey(valve)) {
            throw new IllegalStateException("Cannot place valve at " + x + "," + y);
        }
        unloadedTiles.remove(key);
        valves.put(key, valve);
        valvePositions.put(valve, key);
        changed();
    }

    /** The valve was removed: pumps next to it lose it as a source, and it is no destination any more. */
    public TankValve removeValve(int x, int y) {
        TankValve valve = valves.remove(key(x, y));
        if (valve != null) {
            changed();
            structureChanged(x, y);
            valvePositions.remove(valve);
            for (Direction d : DIRS) {
                Pump pump = pumps.get(key(x + d.dx, y + d.dy));
                if (pump != null) {
                    pump.removeSourceSlot(Pump.SourceSlot.valve(d.opposite()));
                }
            }
            long dest = key(x, y);
            dropHints(dest);
            staleDestinations.remove(dest);
            checkDestinations.remove(dest);
        }
        return valve;
    }

    /** The valve's region unloaded: it leaves the engine, the pumps keep it as a source. */
    public TankValve unloadValve(int x, int y) {
        TankValve valve = valves.remove(key(x, y));
        if (valve != null) {
            changed();
            valvePositions.remove(valve);
            if (loadedLookup == null) {
                unloadedTiles.add(key(x, y));
            }
        }
        return valve;
    }

    /**
     * N33-1: the valve at the tile counts as a plain wall ({@code true}) while it is in a wall shared
     * by two recognized tanks, or works as a valve again ({@code false}). Its link flags stay as they
     * are (N16-4); the links they make disappear or appear again, handled as when the wrench cuts or
     * links them: a structure change of the valve's region and of each linked part's (N28-1, so the
     * summaries passing them are invalid, N28-2), the networks there rebuilt, and the valve's
     * destination and the destinations of the linked pipes' networks searched again when used
     * (N25-4, N28-12). The tank's fluid is untouched. Nothing happens when the state does not change
     * or no valve is there.
     * <p>N35-1 (replacing N33-16, N33-21 and N33-23): the pumps next to it keep its slot in their
     * sources where it is, as for a valve switched off by a wire signal (N27-4). Nothing is pulled
     * through it while it is a plain wall ({@link #isPumpValveLinked}); when it is a valve again it is
     * pulled from in that place, dormant while its tank holds another fluid than the pump's baseline
     * (N17-3, N20-3).
     */
    public void setValvePlainWall(int x, int y, boolean plainWall) {
        TankValve valve = valves.get(key(x, y));
        if (valve == null || valve.isPlainWall() == plainWall) {
            return;
        }
        // The parts its flags link: linked until now, or linked from now on.
        List<PipeNode> pipes = new ArrayList<>();
        List<Long> pumpTiles = new ArrayList<>();
        for (Direction d : DIRS) {
            long side = key(x + d.dx, y + d.dy);
            PipeNode node = basePipes.get(side);
            if (node != null && node.isSideOpen(d.opposite()) && valve.isSideOpen(d)) {
                pipes.add(node);
            }
            Pump pump = pumps.get(side);
            if (pump != null && pump.isSideOpen(d.opposite()) && valve.isSideOpen(d)) {
                pumpTiles.add(side);
            }
        }
        PipeNode under = undergroundPipes.get(key(x, y));
        if (under != null && under.isVerticalOpen() && valve.isVerticalOpen()) {
            pipes.add(under);
        }
        valve.setPlainWall(plainWall);
        changed();
        structureChanged(x, y);
        // No longer a destination, or a destination again (as a new valve).
        staleDestinations.add(key(x, y));
        for (PipeNode node : pipes) {
            int px = node.getTileX();
            int py = node.getTileY();
            afterLinkChange(x, y, px, py);
            linkChanged(px, py, node.getLayer() == PipeLayer.BASE ? Part.BASIC_PIPE : Part.UNDERGROUND_PIPE,
                    x, y, Part.VALVE, plainWall);
            structureChanged(px, py);
        }
        for (long pump : pumpTiles) {
            afterLinkChange(x, y, keyX(pump), keyY(pump));
            structureChanged(keyX(pump), keyY(pump));
        }
    }

    // ---------------------------------------------------------------- pumps

    /**
     * Placement check of a pump (N17-1): the base layer must be free, and the sources it would
     * connect (the liquid tile under it and the valves next to it whose side toward it is not cut)
     * must hold one fluid or be empty. A valve that is a plain wall (N33-1) holds nothing here: the
     * pump may be placed whatever its tank holds, and it connects as a source that is skipped while
     * it is a plain wall (N35-1, {@link #placePump}).
     *
     * @param tileFluid the fluid of the liquid tile under it ({@link FluidType#fromPumpedTile}), or
     *                  {@code null} on land
     */
    public Check checkPumpPlacement(int x, int y, FluidType tileFluid) {
        if (isBaseLayerTaken(key(x, y))) {
            return Check.OCCUPIED;
        }
        List<FluidType> fluids = new ArrayList<>();
        fluids.add(tileFluid);
        for (Direction d : DIRS) {
            TankValve valve = valves.get(key(x + d.dx, y + d.dy));
            if (valve != null && !valve.isPlainWall() && valve.isSideOpen(d.opposite())) {
                fluids.add(valve.getStoredFluid());
            }
        }
        return Pump.canConnectSources(fluids) ? Check.OK : Check.DIFFERENT_SOURCE_FLUID;
    }

    /**
     * Places a new pump. It connects its sources in this order: the liquid tile under it (when the
     * game set a {@link Pump#setTileSource tile source}), then the valves next to it whose links are
     * open, north, east, south, west (a technical order for sources connected at the same moment).
     * A valve that is a plain wall (N33-1) connects the same way: skipped while it is one, pulled from
     * in its place when it is a valve again, dormant while its tank holds another fluid than the
     * baseline (N35-1, N17-3, N20-3). It starts a network of its own (N18-2).
     *
     * @throws IllegalStateException if {@link #checkPumpPlacement} is not {@link Check#OK}
     */
    public void placePump(int x, int y, Pump pump) {
        Objects.requireNonNull(pump, "pump");
        Check check = checkPumpPlacement(x, y, Pump.storedFluidOf(pump.getTileSource()));
        if (check != Check.OK) {
            throw new IllegalStateException("Cannot place pump at " + x + "," + y + ": " + check);
        }
        List<Pump.SourceSlot> slots = new ArrayList<>();
        if (pump.getTileSource() != null) {
            slots.add(Pump.SourceSlot.TILE);
        }
        for (Direction d : DIRS) {
            TankValve valve = valves.get(key(x + d.dx, y + d.dy));
            if (valve != null && pump.isSideOpen(d) && valve.isSideOpen(d.opposite())) {
                slots.add(Pump.SourceSlot.valve(d));
            }
        }
        pump.setSourceSlots(slots);
        // A new pump: an old summary at its tile belonged to another pump.
        dropSummariesOf(key(x, y));
        // N28-17: the placement order, kept for good (wrench, merges, saves).
        pump.setInstallNumber(nextInstall++);
        addPump(x, y, pump);
        structureChanged(x, y);
    }

    /**
     * Adds a pump with its saved state (its region was loaded). Saved valve sources behind its own
     * cut sides are dropped; a slot whose valve cut the link is skipped while it is cut
     * ({@link Pump#getSources}), and linking it again makes it the last source (N19-1). A slot whose
     * valve is a plain wall (N33-1) stays too, skipped while it is one (N35-1).
     */
    public void loadPump(int x, int y, Pump pump) {
        Objects.requireNonNull(pump, "pump");
        pump.dropCutSourceSlots();
        addPump(x, y, pump);
        if (pump.getInstallNumber() >= 0) {
            nextInstall = Math.max(nextInstall, pump.getInstallNumber() + 1);
        } else {
            // Saved without one (older formats are not read, N28-19): numbered as if placed now.
            pump.setInstallNumber(nextInstall++);
        }
    }

    /** The next install number (N28-17), saved with the level. */
    public long getNextInstallNumber() {
        return nextInstall;
    }

    /** Restores the saved next install number (level load, N28-17). */
    public void setNextInstallNumber(long next) {
        nextInstall = Math.max(nextInstall, next);
    }



    private void addPump(int x, int y, Pump pump) {
        long key = key(x, y);
        if (isBaseLayerTaken(key) || pump.host != null) {
            throw new IllegalStateException("Cannot place pump at " + x + "," + y);
        }
        unloadedTiles.remove(key);
        changed();
        pumps.put(key, pump);
        pump.place(this, x, y);
        PipeNetwork own = new PipeNetwork();
        networks.add(own);
        own.addPump(pump);
        // A loaded pump that pushed before joins the network its fluid reached (N18-2).
        rebuild(Collections.singletonList(own));
    }

    /**
     * Removes a pump that was picked up: it stops (N14-3), its summaries go with it, and it is a
     * structure change of its region (N28-1). A pump whose region unloads leaves through
     * {@link #unloadPump}.
     */
    public Pump removePump(int x, int y) {
        Pump pump = takePump(x, y);
        if (pump != null) {
            structureChanged(x, y);
            dropSummariesOf(key(x, y));
        }
        return pump;
    }

    /**
     * The pump's region unloaded, or the game only replaces its entity with another one of the same
     * pump: it leaves the engine and stops (N14-3). Its summaries stay, kept by its tile, for when its
     * region loads again (N23-2); not a structure change (N28-1).
     */
    public Pump unloadPump(int x, int y) {
        return takePump(x, y);
    }

    private Pump takePump(int x, int y) {
        Pump pump = pumps.remove(key(x, y));
        if (pump != null) {
            changed();
            routeMemo.remove(pump);
            summaryRecordedAt.remove(pump);
            pump.host = null;
            PipeNetwork network = pump.network;
            if (network != null) {
                network.pumps.remove(pump);
                pump.network = null;
                rebuild(Collections.singletonList(network));
            }
        }
        return pump;
    }

    // ---------------------------------------------------------------- wrench

    /**
     * Wrench right-click toward a side (12-8, 13-4, N16-3): toggles the link of the part at the tile
     * toward {@code direction}. With a linkable neighbour there (pipe-pipe of one layer, basic
     * pipe-valve, basic pipe-pump, pump-valve) both facing flags are set: cut when linked, open
     * otherwise. Without one, only the part's own flag flips (it stays for a later neighbour, N16-4).
     * Linking a valve to a pump is refused when the valve's tank holds another fluid than the pump's
     * other sources (N16-3); a linked valve becomes the pump's last source (N19-1).
     * When the tile or the neighbour's tile is not loaded, nothing changes ({@link Check#NOT_LOADED}):
     * the game loads the neighbour's region before it uses the wrench toward it.
     * N33-15: a valve that is a plain wall (N33-1) is no part here, like a wall: toward it only the
     * part's own flag flips, and on it the click finds nothing ({@link Check#NOTHING_THERE}); its
     * flags stay as they were for when it is a valve again. A pump's flag flipped toward it changes
     * the pump's sources as a cut or a link would (N35-1, {@link #pumpFlagTowardPlainWall}): cut, the
     * valve leaves them; opened, it becomes the last source (N19-1) without a fluid check, skipped
     * while it is a plain wall.
     */
    public Check toggleSide(int x, int y, Part part, Direction direction) {
        int nx = x + direction.dx;
        int ny = y + direction.dy;
        if (!isTileLoaded(x, y) || !isTileLoaded(nx, ny)) {
            return Check.NOT_LOADED;
        }
        End own = end(x, y, part, direction);
        if (own == null) {
            return Check.NOTHING_THERE;
        }
        End other = neighbourEnd(part, nx, ny, direction.opposite());
        boolean wasLinked = other != null && own.isOpen() && other.isOpen();
        Pump pump = part == Part.PUMP ? pumps.get(key(x, y)) : other != null && other.part == Part.PUMP ? pumps.get(key(nx, ny)) : null;
        boolean pumpValve = other != null && (part == Part.PUMP && other.part == Part.VALVE
                || part == Part.VALVE && other.part == Part.PUMP);
        Direction pumpSide = part == Part.PUMP ? direction : direction.opposite();
        if (other == null) {
            own.set(!own.isOpen());
            if (part == Part.PUMP) {
                pumpFlagTowardPlainWall(pump, direction, own.isOpen());
            }
        } else if (own.isOpen() && other.isOpen()) {
            own.set(false);
            other.set(false);
            if (pumpValve) {
                pump.removeSourceSlot(Pump.SourceSlot.valve(pumpSide));
            }
        } else {
            if (pumpValve) {
                TankValve valve = part == Part.VALVE ? valves.get(key(x, y)) : valves.get(key(nx, ny));
                List<FluidType> fluids = pump.getSourceFluids(Pump.SourceSlot.valve(pumpSide));
                fluids.add(valve.getStoredFluid());
                if (!Pump.canConnectSources(fluids)) {
                    return Check.DIFFERENT_SOURCE_FLUID;
                }
            }
            own.set(true);
            other.set(true);
            if (pumpValve) {
                pump.addSourceSlot(Pump.SourceSlot.valve(pumpSide));
            }
        }
        afterLinkChange(x, y, nx, ny);
        if (other != null) {
            linkChanged(x, y, own.part, nx, ny, other.part, wasLinked);
        }
        listener.onLinksChanged(x, y, own.part);
        if (other != null) {
            listener.onLinksChanged(nx, ny, other.part);
        }
        structureChanged(x, y);
        if (other != null) {
            structureChanged(nx, ny);
        }
        return Check.OK;
    }

    /**
     * N35-1: the wrench flipped a pump's own flag toward the valve on its side {@code direction}
     * while that valve is a plain wall (N33-1), which the wrench treats as a wall (N33-15). Cut: the
     * valve leaves the pump's sources, as any cut source does. Opened: it becomes the pump's last
     * source (N19-1) when the valve's flag toward the pump is open too (both flags, as for any valve
     * source), with no fluid comparison: the wrench links no wall, so the N16-3 refusal does not run.
     * It is skipped while it is a plain wall; once it is a valve again it is dormant while its tank
     * holds another fluid than the baseline (N17-3, N20-3). Nothing happens toward anything else.
     */
    private void pumpFlagTowardPlainWall(Pump pump, Direction direction, boolean open) {
        TankValve valve = valves.get(key(pump.getTileX() + direction.dx, pump.getTileY() + direction.dy));
        if (valve == null || !valve.isPlainWall()) {
            return;
        }
        Pump.SourceSlot slot = Pump.SourceSlot.valve(direction);
        if (!open) {
            pump.removeSourceSlot(slot);
        } else if (valve.isSideOpen(direction.opposite())) {
            pump.addSourceSlot(slot);
        }
    }

    /**
     * Wrench right-click on the middle of a tile (12-8, 13-5, N16-4): toggles the vertical link
     * between the basic pipe or valve there and the underground pipe there. With only one of them,
     * only its own flag flips. Nothing changes on a tile that is not loaded ({@link Check#NOT_LOADED}).
     * A valve that is a plain wall (N33-1) counts as no valve here, as in {@link #toggleSide} (N33-15).
     */
    public Check toggleVertical(int x, int y) {
        if (!isTileLoaded(x, y)) {
            return Check.NOT_LOADED;
        }
        long key = key(x, y);
        PipeNode base = basePipes.get(key);
        TankValve valve = valves.get(key);
        PipeNode under = undergroundPipes.get(key);
        End top = base != null ? new End(Part.BASIC_PIPE, () -> base.isVerticalOpen(), base::setVerticalOpen)
                : valve != null && !valve.isPlainWall() ? new End(Part.VALVE, () -> valve.isVerticalOpen(), valve::setVerticalOpen)
                : null;
        End bottom = under == null ? null : new End(Part.UNDERGROUND_PIPE, () -> under.isVerticalOpen(), under::setVerticalOpen);
        if (top == null && bottom == null) {
            return Check.NOTHING_THERE;
        }
        boolean wasLinked = top != null && bottom != null && top.isOpen() && bottom.isOpen();
        if (top == null || bottom == null) {
            End only = top != null ? top : bottom;
            only.set(!only.isOpen());
        } else {
            boolean open = !(top.isOpen() && bottom.isOpen());
            top.set(open);
            bottom.set(open);
        }
        afterLinkChange(x, y, x, y);
        if (top != null && bottom != null) {
            linkChanged(x, y, top.part, x, y, bottom.part, wasLinked);
        }
        if (top != null) {
            listener.onLinksChanged(x, y, top.part);
        }
        if (bottom != null) {
            listener.onLinksChanged(x, y, bottom.part);
        }
        structureChanged(x, y);
        return Check.OK;
    }

    /**
     * What {@link #toggleSide} would return now, changing nothing: the wrench's tooltip previews why
     * a right click would be refused (N30-1). Read only.
     */
    public Check checkToggleSide(int x, int y, Part part, Direction direction) {
        int nx = x + direction.dx;
        int ny = y + direction.dy;
        if (!isTileLoaded(x, y) || !isTileLoaded(nx, ny)) {
            return Check.NOT_LOADED;
        }
        End own = end(x, y, part, direction);
        if (own == null) {
            return Check.NOTHING_THERE;
        }
        End other = neighbourEnd(part, nx, ny, direction.opposite());
        boolean pumpValve = other != null && (part == Part.PUMP && other.part == Part.VALVE
                || part == Part.VALVE && other.part == Part.PUMP);
        if (!pumpValve || own.isOpen() && other.isOpen()) {
            return Check.OK;
        }
        Pump pump = pumps.get(part == Part.PUMP ? key(x, y) : key(nx, ny));
        TankValve valve = valves.get(part == Part.VALVE ? key(x, y) : key(nx, ny));
        List<FluidType> fluids = pump.getSourceFluids(Pump.SourceSlot.valve(part == Part.PUMP ? direction : direction.opposite()));
        fluids.add(valve.getStoredFluid());
        return Pump.canConnectSources(fluids) ? Check.OK : Check.DIFFERENT_SOURCE_FLUID;
    }

    /** What {@link #toggleVertical} would return now, changing nothing (N30-1). Read only. */
    public Check checkToggleVertical(int x, int y) {
        if (!isTileLoaded(x, y)) {
            return Check.NOT_LOADED;
        }
        long key = key(x, y);
        TankValve valve = valves.get(key);
        return basePipes.containsKey(key) || valve != null && !valve.isPlainWall() || undergroundPipes.containsKey(key)
                ? Check.OK : Check.NOTHING_THERE;
    }

    /** One part's flag toward one side (or the vertical one). */
    private static final class End {
        final Part part;
        final java.util.function.BooleanSupplier getter;
        final java.util.function.Consumer<Boolean> setter;

        End(Part part, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
            this.part = part;
            this.getter = getter;
            this.setter = setter;
        }

        boolean isOpen() {
            return getter.getAsBoolean();
        }

        void set(boolean open) {
            setter.accept(open);
        }
    }

    private End end(int x, int y, Part part, Direction d) {
        long key = key(x, y);
        switch (part) {
            case BASIC_PIPE:
            case UNDERGROUND_PIPE: {
                PipeNode node = (part == Part.BASIC_PIPE ? basePipes : undergroundPipes).get(key);
                return node == null ? null : new End(part, () -> node.isSideOpen(d), open -> node.setSideOpen(d, open));
            }
            case VALVE: {
                // N33-1: a valve that is a plain wall is no part the wrench links.
                TankValve valve = valves.get(key);
                return valve == null || valve.isPlainWall() ? null
                        : new End(part, () -> valve.isSideOpen(d), open -> valve.setSideOpen(d, open));
            }
            case PUMP: {
                Pump pump = pumps.get(key);
                return pump == null ? null : new End(part, () -> pump.isSideOpen(d), open -> pump.setSideOpen(d, open));
            }
            default:
                return null;
        }
    }

    /** The linkable neighbour of a part at (nx, ny), facing back with side {@code d}. */
    private End neighbourEnd(Part from, int nx, int ny, Direction d) {
        if (from == Part.UNDERGROUND_PIPE) {
            return end(nx, ny, Part.UNDERGROUND_PIPE, d);
        }
        long key = key(nx, ny);
        if (basePipes.containsKey(key)) {
            return end(nx, ny, Part.BASIC_PIPE, d);
        }
        if (valves.containsKey(key) && from != Part.VALVE) {
            return end(nx, ny, Part.VALVE, d);
        }
        if (pumps.containsKey(key) && from != Part.PUMP) {
            return end(nx, ny, Part.PUMP, d);
        }
        return null;
    }

    private void afterLinkChange(int x1, int y1, int x2, int y2) {
        changed();
        Set<PipeNetwork> affected = new LinkedHashSet<>();
        collectNetworks(x1, y1, affected);
        collectNetworks(x2, y2, affected);
        if (!affected.isEmpty()) {
            rebuild(affected);
        }
    }

    private void collectNetworks(int x, int y, Set<PipeNetwork> out) {
        long key = key(x, y);
        PipeNode base = basePipes.get(key);
        PipeNode under = undergroundPipes.get(key);
        Pump pump = pumps.get(key);
        if (base != null && base.network != null) {
            out.add(base.network);
        }
        if (under != null && under.network != null) {
            out.add(under.network);
        }
        if (pump != null && pump.network != null) {
            out.add(pump.network);
        }
    }

    // ---------------------------------------------------------------- networks

    /** The parts a network member is joined to: linked, reached and holding the same fluid. */
    private List<Object> edges(Object member) {
        List<Object> result = new ArrayList<>();
        if (member instanceof PipeNode) {
            PipeNode node = (PipeNode) member;
            FluidType fluid = node.getFluid();
            if (fluid == null) {
                return result;
            }
            for (PipeNode next : linkedPipes(node)) {
                if (next.getFluid() == fluid) {
                    result.add(next);
                }
            }
            if (node.getLayer() == PipeLayer.BASE) {
                for (Direction d : DIRS) {
                    Pump pump = pumps.get(key(node.getTileX() + d.dx, node.getTileY() + d.dy));
                    if (pump != null && node.isSideOpen(d) && pump.isSideOpen(d.opposite())
                            && pump.getLastPushedFluid() == fluid) {
                        result.add(pump);
                    }
                }
            }
        } else {
            Pump pump = (Pump) member;
            FluidType fluid = pump.getLastPushedFluid();
            if (fluid != null) {
                for (PipeNode entry : getPumpEntries(pump)) {
                    if (entry.getFluid() == fluid) {
                        result.add(entry);
                    }
                }
            }
        }
        return result;
    }

    private static PipeNetwork networkOf(Object member) {
        return member instanceof PipeNode ? ((PipeNode) member).network : ((Pump) member).network;
    }

    private boolean isPresent(Object member) {
        if (member instanceof PipeNode) {
            PipeNode node = (PipeNode) member;
            return !node.removed && node.isReached() && getPipe(node.getTileX(), node.getTileY(), node.getLayer()) == node;
        }
        Pump pump = (Pump) member;
        return pump.host == this && pumps.get(key(pump.getTileX(), pump.getTileY())) == pump;
    }

    /** A pipe was just reached (or loaded holding fluid): it joins the networks it is linked to. */
    private void attachReached(PipeNode node) {
        changed();
        Set<PipeNetwork> adjacent = new LinkedHashSet<>();
        for (Object next : edges(node)) {
            PipeNetwork network = networkOf(next);
            if (network != null) {
                adjacent.add(network);
            }
        }
        if (adjacent.isEmpty()) {
            PipeNetwork network = new PipeNetwork();
            networks.add(network);
            network.addNode(node);
        } else if (adjacent.size() == 1) {
            adjacent.iterator().next().addNode(node);
        } else {
            PipeNetwork target = null;
            for (PipeNetwork network : adjacent) {
                if (target == null || network.nodes.size() + network.pumps.size() > target.nodes.size() + target.pumps.size()) {
                    target = network;
                }
            }
            for (PipeNetwork network : adjacent) {
                if (network == target) {
                    continue;
                }
                networks.remove(network);
                for (PipeNode member : network.nodes) {
                    target.addNode(member);
                }
                for (Pump member : network.pumps) {
                    target.addPump(member);
                }
            }
            target.addNode(node);
        }
        listener.onPipeFluidChanged(node.getTileX(), node.getTileY(), node.getLayer());
    }

    /**
     * Rebuilds networks after a change (N18-3): every member of {@code dissolve} (plus any network a
     * member turns out to be joined to) is regrouped by its current links.
     */
    private void rebuild(Collection<PipeNetwork> dissolve) {
        Set<PipeNetwork> old = Collections.newSetFromMap(new IdentityHashMap<PipeNetwork, Boolean>());
        List<Object> members = new ArrayList<>();
        for (PipeNetwork network : dissolve) {
            dissolveInto(network, old, members);
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (int i = 0; i < members.size(); i++) {
            Object start = members.get(i);
            if (visited.contains(start) || !isPresent(start)) {
                continue;
            }
            PipeNetwork network = new PipeNetwork();
            networks.add(network);
            ArrayDeque<Object> queue = new ArrayDeque<>();
            visited.add(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                Object member = queue.poll();
                if (member instanceof PipeNode) {
                    network.addNode((PipeNode) member);
                } else {
                    network.addPump((Pump) member);
                }
                for (Object next : edges(member)) {
                    PipeNetwork nextNetwork = networkOf(next);
                    if (nextNetwork != null && nextNetwork != network && !old.contains(nextNetwork)) {
                        // Joined to a network that was not dissolved yet: regroup it too.
                        dissolveInto(nextNetwork, old, members);
                    }
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
    }

    private void dissolveInto(PipeNetwork network, Set<PipeNetwork> old, List<Object> members) {
        if (!old.add(network)) {
            return;
        }
        networks.remove(network);
        for (PipeNode node : network.nodes) {
            node.network = null;
            members.add(node);
        }
        for (Pump pump : network.pumps) {
            pump.network = null;
            members.add(pump);
        }
    }

    /** A pump is about to push {@code fluid}: when it is not its network's fluid, it leaves that network (N17-3, N18-2). */
    @Override
    public void onPumpPushing(Pump pump, FluidType fluid) {
        if (pump.getLastPushedFluid() != fluid) {
            pump.setLastPushedFluid(fluid);
            if (pump.network != null) {
                rebuild(Collections.singletonList(pump.network));
            }
        }
    }

    // ---------------------------------------------------------------- hints

    /**
     * Marks destinations for a new search when used (N25-4). Their summaries stay: whether a summary
     * still holds is told by the structure change numbers of the regions it passes (N28-1).
     */
    private void markStale(Set<Long> dests) {
        staleDestinations.addAll(dests);
    }

    /**
     * A new, empty pipe (N25-4: only the destinations it affects). Linked to one pipe and no valve,
     * it is a dead-end leaf: no path changes, and it takes that pipe's hints with the step toward it.
     * Linked to more, it may join paths or shorten them: the destinations with hints on the pipes it
     * links to are searched again, and so are the valves it links to (a new way to them).
     */
    private void onPipePlaced(PipeNode node) {
        List<PipeNode> linked = linkedPipes(node);
        Set<Long> valvesLinked = new LinkedHashSet<>();
        for (TankValve valve : getLinkedValves(node)) {
            valvesLinked.add(valvePositions.get(valve));
        }
        Set<Long> stale = new LinkedHashSet<>(valvesLinked);
        if (linked.size() > 1) {
            // Inside a full stretch: every destination of the networks it joins (N28-12).
            networkDestinations(linked, stale);
        }
        if (linked.size() == 1) {
            PipeNode only = linked.get(0);
            int code = codeToward(node, only);
            for (int i = 0; i < only.getHintCount(); i++) {
                long dest = only.getHintDestination(i);
                if (!valvesLinked.contains(dest)) {
                    setHint(node, dest, code);
                }
            }
        } else {
            for (PipeNode next : linked) {
                for (int i = 0; i < next.getHintCount(); i++) {
                    stale.add(next.getHintDestination(i));
                }
            }
        }
        markStale(stale);
    }

    /**
     * The destinations whose paths pass a pipe being removed: the pipes linked to it whose hint
     * steps into it, and the valves it links to (N25-4).
     */
    private Set<Long> destinationsThrough(PipeNode node) {
        Set<Long> result = new LinkedHashSet<>();
        for (PipeNode next : linkedPipes(node)) {
            addCrossing(next, node, result);
        }
        for (TankValve valve : getLinkedValves(node)) {
            result.add(valvePositions.get(valve));
        }
        // A pipe of a full stretch: every destination of its network (N28-12).
        networkDestinations(Collections.singletonList(node), result);
        return result;
    }

    /**
     * N28-12: every destination of the networks of the given pipes (those holding fluid): each
     * destination with a hint on one of their pipes.
     */
    private static void networkDestinations(List<PipeNode> cells, Set<Long> out) {
        Set<PipeNetwork> done = null;
        for (PipeNode cell : cells) {
            PipeNetwork network = cell.network;
            if (network == null) {
                continue;
            }
            if (done == null) {
                done = Collections.newSetFromMap(new IdentityHashMap<PipeNetwork, Boolean>());
            }
            if (!done.add(network)) {
                continue;
            }
            for (PipeNode node : network.nodes) {
                for (int i = 0; i < node.getHintCount(); i++) {
                    out.add(node.getHintDestination(i));
                }
            }
        }
    }

    /**
     * A link between two parts was cut or made by the wrench (N25-4). Pipe to pipe: a cut affects
     * the destinations whose hints step across it; a new link may join or shorten paths, so it
     * affects every destination with a hint on either pipe. Pipe to valve: that valve's destination.
     * At a pipe holding fluid, every destination of its network too (N28-12). Links of pumps change
     * no path.
     */
    private void linkChanged(int x1, int y1, Part part1, int x2, int y2, Part part2, boolean wasLinked) {
        Set<Long> stale = new LinkedHashSet<>();
        boolean pipe1 = part1 == Part.BASIC_PIPE || part1 == Part.UNDERGROUND_PIPE;
        boolean pipe2 = part2 == Part.BASIC_PIPE || part2 == Part.UNDERGROUND_PIPE;
        if (pipe1 && pipe2) {
            PipeNode a = getPipe(x1, y1, part1 == Part.BASIC_PIPE ? PipeLayer.BASE : PipeLayer.UNDERGROUND);
            PipeNode b = getPipe(x2, y2, part2 == Part.BASIC_PIPE ? PipeLayer.BASE : PipeLayer.UNDERGROUND);
            // The groups of linked pipes (N28-15): a new link merges, a cut may split.
            if (wasLinked) {
                splitIfApart(java.util.Arrays.asList(a, b));
            } else if (a.hintTable != b.hintTable) {
                merge(new LinkedHashSet<>(java.util.Arrays.asList(a.hintTable, b.hintTable)));
            }
            if (wasLinked) {
                addCrossing(a, b, stale);
                addCrossing(b, a, stale);
            } else {
                for (PipeNode node : new PipeNode[]{a, b}) {
                    for (int i = 0; i < node.getHintCount(); i++) {
                        stale.add(node.getHintDestination(i));
                    }
                }
            }
            // Inside a full stretch: every destination of the networks on either side (N28-12).
            networkDestinations(java.util.Arrays.asList(a, b), stale);
        } else if (pipe1 && part2 == Part.VALVE) {
            stale.add(key(x2, y2));
            networkDestinations(Collections.singletonList(
                    getPipe(x1, y1, part1 == Part.BASIC_PIPE ? PipeLayer.BASE : PipeLayer.UNDERGROUND)), stale);
        } else if (pipe2 && part1 == Part.VALVE) {
            stale.add(key(x1, y1));
            networkDestinations(Collections.singletonList(
                    getPipe(x2, y2, part2 == Part.BASIC_PIPE ? PipeLayer.BASE : PipeLayer.UNDERGROUND)), stale);
        }
        markStale(stale);
    }

    /** The destinations whose hint at {@code from} steps into {@code to}. */
    private static void addCrossing(PipeNode from, PipeNode to, Set<Long> out) {
        int code = codeToward(from, to);
        for (int i = 0; i < from.getHintCount(); i++) {
            if (from.getHintCode(i) == code) {
                out.add(from.getHintDestination(i));
            }
        }
    }

    /** The contents of a loaded pipe changed (saved state): the fluid met on the way may differ (N13-2). */
    private void markAffected(int x, int y) {
        Set<Long> dests = new LinkedHashSet<>();
        collectAt(x, y, dests);
        for (Direction d : DIRS) {
            collectAt(x + d.dx, y + d.dy, dests);
        }
        markStale(dests);
    }

    private void collectAt(int x, int y, Set<Long> out) {
        long key = key(x, y);
        PipeNode base = basePipes.get(key);
        PipeNode under = undergroundPipes.get(key);
        if (base != null) {
            for (int i = 0; i < base.getHintCount(); i++) {
                out.add(base.getHintDestination(i));
            }
        }
        if (under != null) {
            for (int i = 0; i < under.getHintCount(); i++) {
                out.add(under.getHintDestination(i));
            }
        }
    }

    /**
     * A pipe was loaded with its saved hints (N25-6): looked at before the hints are next used, when
     * the rest of its region has loaded too ({@link #checkLoadedHints}).
     */
    private void onCellLoaded(PipeNode node) {
        loadedToCheck.add(node);
    }

    /**
     * The pipes loaded since the hints were last used:
     * <ul>
     *     <li>N28-13: each saved hint must point at a pipe linked to its pipe, or be the last step
     *     into the destination valve's tile; that is all that is checked. A bad one is dropped and
     *     its destination searched again (repaired when used, N25-1). A hint toward a tile that is
     *     not loaded cannot be checked and stays.</li>
     *     <li>A destination a linked pipe next to it has a hint for and it has none is searched again
     *     (N25-6): that is how a destination reaches the pipes behind a region that loads (a valve
     *     placed while the region between was unloaded). It checks no saved hint.</li>
     * </ul>
     */
    void checkLoadedHints() {
        if (loadedToCheck.isEmpty()) {
            return;
        }
        List<PipeNode> cells = new ArrayList<>(loadedToCheck);
        loadedToCheck.clear();
        for (PipeNode node : cells) {
            if (node.removed || getPipe(node.getTileX(), node.getTileY(), node.getLayer()) != node) {
                continue;
            }
            for (PipeNode next : linkedPipes(node)) {
                for (int i = 0; i < next.getHintCount(); i++) {
                    long dest = next.getHintDestination(i);
                    if (node.getHint(dest) < 0) {
                        staleDestinations.add(dest);
                    }
                }
            }
            for (int i = node.getHintCount() - 1; i >= 0; i--) {
                long dest = node.getHintDestination(i);
                if (!hintChecks(node, dest, node.getHintCode(i))) {
                    changed();
                    node.removeHint(dest);
                    Set<PipeNode> holders = hintCells.get(dest);
                    if (holders != null) {
                        holders.remove(node);
                        if (holders.isEmpty()) {
                            hintCells.remove(dest);
                        }
                    }
                    staleDestinations.add(dest);
                }
            }
        }
    }

    /** Whether a hint points at a pipe linked to its pipe, or into its valve (N28-13); unloaded: unknown, kept. */
    private boolean hintChecks(PipeNode node, long dest, int code) {
        int x = node.getTileX();
        int y = node.getTileY();
        if (code == PipeNode.HINT_VERTICAL) {
            if (node.getLayer() == PipeLayer.UNDERGROUND && key(x, y) == dest) {
                return true;
            }
            PipeNode other = node.around[PipeNode.HINT_VERTICAL];
            return other != null && linked(node, other, code);
        }
        Direction d = DIRS[code];
        if (node.getLayer() == PipeLayer.BASE && key(x + d.dx, y + d.dy) == dest) {
            return true;
        }
        PipeNode next = node.around[code];
        if (next == null) {
            return !isTileLoaded(x + d.dx, y + d.dy);
        }
        return linked(node, next, code);
    }

    // ---------------------------------------------------------------- number tables (N28-15)

    /**
     * A pipe entered the engine: it joins the group of the pipes it is linked to (merging their
     * groups when there are several), else the live table its save refers to, else a new group.
     */
    private void joinGroup(PipeNode node, HintTable saved) {
        Set<HintTable> around = new LinkedHashSet<>();
        for (PipeNode next : linkedPipes(node)) {
            if (next.hintTable != null) {
                around.add(next.hintTable);
            }
        }
        HintTable table;
        if (around.isEmpty()) {
            table = live(saved);
            if (table == null) {
                table = newTable();
            }
        } else {
            table = merge(around);
        }
        node.hintTable = table;
        table.cells.add(node);
    }

    /** The table that holds what {@code table} held: itself, or the one its pipes were renumbered into. */
    private HintTable live(HintTable table) {
        for (int guard = 0; table != null && table.retired && guard < 64; guard++) {
            table = hintTables.get(table.successor);
        }
        return table == null || table.retired ? null : table;
    }

    private HintTable newTable() {
        HintTable table = new HintTable(nextTableId++);
        hintTables.put(table.id, table);
        return table;
    }

    /**
     * Groups that became linked merge (N28-15): the one with the most loaded pipes stays, the others'
     * loaded pipes are renumbered into it, and their tables stay read only for their unloaded pipes.
     */
    private HintTable merge(Set<HintTable> tables) {
        HintTable survivor = null;
        for (HintTable table : tables) {
            if (survivor == null || table.cells.size() > survivor.cells.size()
                    || table.cells.size() == survivor.cells.size() && table.id < survivor.id) {
                survivor = table;
            }
        }
        for (HintTable table : tables) {
            if (table == survivor) {
                continue;
            }
            for (PipeNode cell : new ArrayList<>(table.cells)) {
                cell.renumber(survivor);
                survivor.cells.add(cell);
            }
            table.cells.clear();
            table.retired = true;
            table.successor = survivor.id;
            dropIfUnused(table);
        }
        return survivor;
    }

    /**
     * After a cut or a removal: when the pipes on its sides no longer reach each other through loaded
     * linked pipes, the group split (N28-15). The largest part keeps the table; every other part gets
     * a table of its own, its pipes renumbered into it. Pipes of unloaded regions keep the old table;
     * when they load, they join the group around them.
     */
    private void splitIfApart(List<PipeNode> sides) {
        if (sides.size() < 2) {
            return;
        }
        Set<PipeNode> seen = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
        List<List<PipeNode>> parts = new ArrayList<>();
        for (PipeNode side : sides) {
            if (seen.contains(side) || side.removed || getPipe(side.getTileX(), side.getTileY(), side.getLayer()) != side) {
                continue;
            }
            List<PipeNode> part = new ArrayList<>();
            ArrayDeque<PipeNode> queue = new ArrayDeque<>();
            seen.add(side);
            queue.add(side);
            while (!queue.isEmpty()) {
                PipeNode cell = queue.poll();
                part.add(cell);
                for (PipeNode next : linkedPipes(cell)) {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            parts.add(part);
        }
        if (parts.size() < 2) {
            return;
        }
        parts.sort((a, b) -> Integer.compare(b.size(), a.size()));
        for (int i = 1; i < parts.size(); i++) {
            HintTable own = newTable();
            for (PipeNode cell : parts.get(i)) {
                HintTable old = cell.hintTable;
                if (old != null) {
                    old.cells.remove(cell);
                    cell.renumber(own);
                    dropIfUnused(old);
                } else {
                    cell.hintTable = own;
                }
                own.cells.add(cell);
            }
        }
    }

    /** A pipe left for good (removed or broken). */
    private void leaveGroup(PipeNode node) {
        if (node.hintTable != null) {
            node.hintTable.cells.remove(node);
            dropIfUnused(node.hintTable);
        }
    }

    /** A table no pipe refers to any more is dropped (N28-15). */
    private void dropIfUnused(HintTable table) {
        if (table.cells.isEmpty() && table.unloadedRefs <= 0) {
            hintTables.remove(table.id);
        }
    }

    /** The tables the game saves with the level (N28-15): those some pipe refers to. */
    public List<HintTable> getHintTables() {
        List<HintTable> result = new ArrayList<>();
        for (HintTable table : hintTables.values()) {
            if (table.getReferences() > 0) {
                result.add(table);
            }
        }
        return result;
    }

    /** Restores a saved table (level load, N28-15): every pipe that refers to it is unloaded now. */
    public void loadHintTable(int id, long[] destinations, int references, boolean retired, int successor) {
        if (id < 0) {
            return;
        }
        HintTable table = new HintTable(id);
        table.restore(destinations);
        table.unloadedRefs = Math.max(0, references);
        table.retired = retired;
        table.successor = successor;
        hintTables.put(id, table);
        nextTableId = Math.max(nextTableId, id + 1);
    }

    /** The table of an id (tests, diagnostics), or {@code null}. */
    public HintTable getHintTable(int id) {
        return hintTables.get(id);
    }

    public int getNextHintTableId() {
        return nextTableId;
    }

    public void setNextHintTableId(int next) {
        nextTableId = Math.max(nextTableId, next);
    }

    private void indexHints(PipeNode node) {
        for (int i = 0; i < node.getHintCount(); i++) {
            cellsOf(node.getHintDestination(i)).add(node);
        }
    }

    private void unindexHints(PipeNode node) {
        for (int i = 0; i < node.getHintCount(); i++) {
            Set<PipeNode> cells = hintCells.get(node.getHintDestination(i));
            if (cells != null) {
                cells.remove(node);
                if (cells.isEmpty()) {
                    hintCells.remove(node.getHintDestination(i));
                }
            }
        }
    }

    private Set<PipeNode> cellsOf(long dest) {
        Set<PipeNode> cells = hintCells.get(dest);
        if (cells == null) {
            cells = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
            hintCells.put(dest, cells);
        }
        return cells;
    }

    private void setHint(PipeNode node, long dest, int code) {
        if (node.getHint(dest) == code) {
            return;
        }
        changed();
        if (node.setHint(dest, code)) {
            cellsOf(dest).add(node);
        }
    }

    /** Drops every loaded pipe's hint toward a destination that is gone. */
    private void dropHints(long dest) {
        changed();
        Set<PipeNode> cells = hintCells.remove(dest);
        if (cells != null) {
            for (PipeNode node : cells) {
                node.removeHint(dest);
            }
        }
        dropSummariesTo(dest);
    }

    /** Number of hints the loaded pipes hold (memory, tests). */
    public int getHintCount() {
        int count = 0;
        for (Set<PipeNode> cells : hintCells.values()) {
            count += cells.size();
        }
        return count;
    }

    /** The destinations waiting for a new search (tests). */
    Set<Long> getStaleDestinations() {
        return Collections.unmodifiableSet(staleDestinations);
    }

    /** Searches the destinations marked since the last use (N25-4) and new valves without hints. */
    private void refreshHints() {
        checkLoadedHints();
        if (!checkDestinations.isEmpty()) {
            for (long dest : checkDestinations) {
                if (valves.containsKey(dest) && !hasAttachedHint(dest)) {
                    staleDestinations.add(dest);
                }
            }
            checkDestinations.clear();
        }
        if (staleDestinations.isEmpty()) {
            return;
        }
        List<Long> todo = new ArrayList<>(staleDestinations);
        staleDestinations.clear();
        for (long dest : todo) {
            search(dest);
        }
    }

    private boolean hasAttachedHint(long dest) {
        int vx = keyX(dest);
        int vy = keyY(dest);
        for (Direction d : DIRS) {
            PipeNode node = basePipes.get(key(vx + d.dx, vy + d.dy));
            if (node != null && node.getHint(dest) == d.opposite().ordinal()) {
                return true;
            }
        }
        PipeNode under = undergroundPipes.get(dest);
        return under != null && under.getHint(dest) == PipeNode.HINT_VERTICAL;
    }

    /**
     * The search back from a destination valve (N22-3, N24-2): every loaded pipe the fluid can pass
     * toward it gets the direction of its next step. A pipe can be passed when it is empty or holds
     * the one fluid already met on the way back from the valve (one fluid per path, 12-7, N13-2).
     * First found wins: the queue takes the pipes linked to the valve north, east, south, west, then
     * the underground one, and each pipe's neighbours in the same order, then the other layer.
     */
    private void search(long dest) {
        searches++;
        searchedAt.put(dest, tick);
        TankValve valve = valves.get(dest);
        if (valve == null) {
            return;
        }
        int vx = keyX(dest);
        int vy = keyY(dest);
        Map<PipeNode, FluidType> seen = new IdentityHashMap<>();
        ArrayDeque<PipeNode> queue = new ArrayDeque<>();
        for (Direction d : DIRS) {
            PipeNode node = basePipes.get(key(vx + d.dx, vy + d.dy));
            if (node != null && valve.linksSide(d) && node.isSideOpen(d.opposite()) && !seen.containsKey(node)) {
                seen.put(node, node.getFluid());
                setHint(node, dest, d.opposite().ordinal());
                queue.add(node);
            }
        }
        PipeNode under = undergroundPipes.get(dest);
        if (under != null && valve.linksVertical() && under.isVerticalOpen() && !seen.containsKey(under)) {
            seen.put(under, under.getFluid());
            setHint(under, dest, PipeNode.HINT_VERTICAL);
            queue.add(under);
        }
        while (!queue.isEmpty()) {
            PipeNode node = queue.poll();
            FluidType context = seen.get(node);
            for (PipeNode next : linkedPipes(node)) {
                if (seen.containsKey(next)) {
                    continue;
                }
                FluidType held = next.getFluid();
                if (held != null && context != null && held != context) {
                    continue;
                }
                seen.put(next, held != null ? held : context);
                setHint(next, dest, codeToward(next, node));
                queue.add(next);
            }
        }
    }

    /** The hint code of the step from {@code from} to the pipe {@code to} linked to it. */
    private static int codeToward(PipeNode from, PipeNode to) {
        if (from.getLayer() != to.getLayer()) {
            return PipeNode.HINT_VERTICAL;
        }
        return faceBetween(from.getTileX(), from.getTileY(), to.getTileX(), to.getTileY());
    }

    /** The face code from one position to an adjacent one (or the same tile: vertical). */
    private static int faceBetween(int x0, int y0, int x1, int y1) {
        for (Direction d : DIRS) {
            if (x0 + d.dx == x1 && y0 + d.dy == y1) {
                return d.ordinal();
            }
        }
        return PipeNode.HINT_VERTICAL;
    }

    // ---------------------------------------------------------------- routes

    /**
     * One destination's path from a pump, read by stepping along the hints (N24-4): the cells from
     * the output cell to the pipe linked to the valve, with the unloaded stretches it skips as
     * summary runs (N23-2).
     */
    static final class Route {
        static final Comparator<Route> ORDER = (a, b) -> {
            if (a.distance != b.distance) {
                return Integer.compare(a.distance, b.distance);
            }
            if (a.valveY != b.valveY) {
                return Integer.compare(a.valveY, b.valveY);
            }
            return Integer.compare(a.valveX, b.valveX);
        };

        final TankValve valve;
        final long dest;
        final int valveX;
        final int valveY;
        /** {@link PipeNode}s of loaded cells and {@link SummaryRun}s of skipped unloaded stretches. */
        final Object[] path;
        /** The face code of the step into each element ([0]: the pump's side), and last into the valve. */
        final int[] faces;
        /** Steps from the pump to each element (its last cell, for a run). */
        final int[] stepsTo;
        /** Pipe path distance: the number of pipe cells (N7-2, N26-4). */
        final int distance;
        /** Elements before this index are known to be full: the frontier (N7-1). */
        int frontier;
        /** What follows the last element: the valve, or for a dead-end end (N28-6) a mark of its own. */
        final Object end;

        Route(TankValve valve, long dest, Object[] path, int[] faces, int[] stepsTo, int distance) {
            this.end = valve != null ? valve : new Object();
            this.valve = valve;
            this.dest = dest;
            this.valveX = keyX(dest);
            this.valveY = keyY(dest);
            this.path = path;
            this.faces = faces;
            this.stepsTo = stepsTo;
            this.distance = distance;
        }

        /** The identity of element {@code i} for grouping at junctions, or of the end past the last one. */
        Object element(int i) {
            if (i < path.length) {
                return path[i] instanceof SummaryRun ? ((SummaryRun) path[i]).key() : path[i];
            }
            return end;
        }
    }

    private static final int WALK_OK = 0;
    private static final int WALK_REPAIR = 1;
    private static final int WALK_DEAD_END = 2;

    /** Result of one {@link #walk}. */
    private static final class Walk {
        int status;
        Route route;

        Walk(int status) {
            this.status = status;
        }
    }

    /**
     * The destinations of a pump for {@code fluid}: the valves its output cells have hints for,
     * each along its hints from the output cell with the fewest steps (equal ones: the earlier side
     * in {@link Direction} order), nearest first (N7-2: distance, then valve y, then x).
     */
    List<Route> routesFor(Pump pump, FluidType fluid) {
        refreshHints();
        RouteMemo memo = routeMemo.get(pump);
        if (memo != null && memo.fluid == fluid && memo.changes == changes) {
            return memo.routes;
        }
        Map<Long, Route> best = new LinkedHashMap<>();
        Set<Long> dests = new LinkedHashSet<>();
        List<PipeNode> outputs = new ArrayList<>();
        List<Direction> sides = new ArrayList<>();
        for (Direction d : DIRS) {
            PipeNode node = basePipes.get(key(pump.getTileX() + d.dx, pump.getTileY() + d.dy));
            if (node != null && pump.isSideOpen(d) && node.isSideOpen(d.opposite())) {
                outputs.add(node);
                sides.add(d);
                for (int i = 0; i < node.getHintCount(); i++) {
                    dests.add(node.getHintDestination(i));
                }
            }
        }
        for (long dest : dests) {
            TankValve valve = valves.get(dest);
            if (valve == null || valve.isPlainWall()) {
                // Its region is not loaded, or it is a plain wall (N33-1): no destination now.
                continue;
            }
            for (int o = 0; o < outputs.size(); o++) {
                PipeNode output = outputs.get(o);
                if (output.getHint(dest) < 0 || !traversable(output, fluid)) {
                    continue;
                }
                Walk walk = walk(pump, sides.get(o), output, dest, valve, fluid);
                if (walk.status == WALK_REPAIR) {
                    Long searched = searchedAt.get(dest);
                    if (searched == null || searched != tick) {
                        // Repaired when used (N25-4): search again, then step once more.
                        search(dest);
                        walk = output.getHint(dest) < 0 ? new Walk(WALK_REPAIR)
                                : walk(pump, sides.get(o), output, dest, valve, fluid);
                    }
                    if (walk.status == WALK_REPAIR) {
                        // Searched this tick and still broken: a hint left where the search no
                        // longer reaches. It leaves this output cell.
                        changed();
                        if (output.removeHint(dest)) {
                            Set<PipeNode> cells = hintCells.get(dest);
                            if (cells != null) {
                                cells.remove(output);
                            }
                        }
                        continue;
                    }
                }
                if (walk.status != WALK_OK) {
                    continue;
                }
                Route current = best.get(dest);
                if (current == null || walk.route.distance < current.distance) {
                    best.put(dest, walk.route);
                }
            }
        }
        List<Route> routes = new ArrayList<>(best.values());
        routes.sort(Route.ORDER);
        routeMemo.put(pump, new RouteMemo(fluid, changes, routes));
        return routes;
    }

    /** Whether {@code fluid} may enter the pipe: empty or holding it (N13-2). */
    private static boolean traversable(PipeNode node, FluidType fluid) {
        FluidType held = node.getFluid();
        return !node.removed && (held == null || held == fluid);
    }

    /**
     * Steps from an output cell along the hints toward a destination (N24-4), reading every cell:
     * a missing pipe, a cut link, a missing hint or a loop asks for a repair; a pipe of another fluid
     * or an unloaded stretch the summary cannot skip is a dead end.
     */
    private Walk walk(Pump pump, Direction side, PipeNode output, long dest, TankValve valve, FluidType fluid) {
        PathBuilder path = new PathBuilder();
        PathBuilder faces = path;
        PathBuilder steps = path;
        int limit = basePipes.size() + undergroundPipes.size() + 1;
        int loadedSteps = 1;
        PipeNode cell = output;
        int count = 1;
        path.add(cell, side.ordinal(), count);
        RouteSummary summary = null;
        while (true) {
            int code = cell.getHint(dest);
            if (code < 0) {
                return new Walk(WALK_REPAIR);
            }
            int x = cell.getTileX();
            int y = cell.getTileY();
            if (code == PipeNode.HINT_VERTICAL) {
                if (cell.getLayer() == PipeLayer.UNDERGROUND && key(x, y) == dest) {
                    if (!(cell.isVerticalOpen() && valve.linksVertical())) {
                        return new Walk(WALK_REPAIR);
                    }
                    path.end(PipeNode.HINT_VERTICAL);
                    break;
                }
            } else {
                Direction d = DIRS[code];
                if (cell.getLayer() == PipeLayer.BASE && key(x + d.dx, y + d.dy) == dest) {
                    if (!(cell.isSideOpen(d) && valve.linksSide(d.opposite()))) {
                        return new Walk(WALK_REPAIR);
                    }
                    path.end(code);
                    break;
                }
            }
            PipeLayer layer = code == PipeNode.HINT_VERTICAL ? cell.getLayer().other() : cell.getLayer();
            int nx = code == PipeNode.HINT_VERTICAL ? x : x + DIRS[code].dx;
            int ny = code == PipeNode.HINT_VERTICAL ? y : y + DIRS[code].dy;
            PipeNode next = cell.around[code];
            if (next == null) {
                if (isTileLoaded(nx, ny)) {
                    return new Walk(WALK_REPAIR);
                }
                // An unloaded stretch: only the summary of the last normal cycle can skip it (N23-2).
                if (code != PipeNode.HINT_VERTICAL && !cell.isSideOpen(DIRS[code])) {
                    return new Walk(WALK_REPAIR);
                }
                if (summary == null) {
                    summary = summaryOf(pump, dest);
                    if (summary != null && !isIntact(summary)) {
                        // A structure change in a region it passes since it was written (N28-1).
                        return new Walk(WALK_DEAD_END);
                    }
                }
                int run = summary == null ? -1 : summary.indexOfRunAt(nx, ny, layer);
                if (run < 0) {
                    return new Walk(WALK_DEAD_END);
                }
                SummaryRun last = null;
                while (run < summary.runs.length && !isTileLoaded(summary.runs[run].firstX, summary.runs[run].firstY)) {
                    SummaryRun skipped = summary.runs[run];
                    if (!skipped.full || skipped.fluid != fluid) {
                        // Not full of this fluid when last seen: a dead end (N14-3).
                        return new Walk(WALK_DEAD_END);
                    }
                    count += skipped.count;
                    path.add(skipped, last == null ? code : faceBetween(last.lastX, last.lastY, skipped.firstX, skipped.firstY),
                            count);
                    last = skipped;
                    run++;
                }
                if (last == null) {
                    return new Walk(WALK_DEAD_END);
                }
                if (run >= summary.runs.length) {
                    // The skipped stretch reaches the valve: a loaded last output position takes the
                    // fluid (N23-2); only one in an unloaded cell is a dead end (N28-4).
                    int end = endFaceInto(last, dest, valve);
                    if (end < 0) {
                        return new Walk(WALK_DEAD_END);
                    }
                    path.end(end);
                    break;
                }
                SummaryRun resume = summary.runs[run];
                next = getPipe(resume.firstX, resume.firstY, resume.firstLayer);
                if (next == null || !traversable(next, fluid)) {
                    return new Walk(WALK_DEAD_END);
                }
                code = last.lastLayer != next.getLayer() && last.lastX == next.getTileX() && last.lastY == next.getTileY()
                        ? PipeNode.HINT_VERTICAL : faceBetween(last.lastX, last.lastY, next.getTileX(), next.getTileY());
            } else {
                if (!linked(cell, next, code)) {
                    return new Walk(WALK_REPAIR);
                }
                if (!traversable(next, fluid)) {
                    return new Walk(WALK_DEAD_END);
                }
            }
            count++;
            path.add(next, code, count);
            cell = next;
            // N28-13: hints saved at different times may form a loop; a pass never takes more steps
            // than there are loaded pipes (plus one). TODO(confirm): that bound.
            if (++loadedSteps > limit) {
                return new Walk(WALK_REPAIR);
            }
        }
        Walk result = new Walk(WALK_OK);
        result.route = new Route(valve, dest, path.elements(), path.faces(), path.steps(), count);
        return result;
    }

    /**
     * The face from the last cell of a skipped run into the valve right after it, or -1 when that
     * cell does not reach the valve (only the valve's own flag can be read; the run's cells were
     * linked to it when the summary was written, and nothing changed since, N28-1).
     */
    private static int endFaceInto(SummaryRun last, long dest, TankValve valve) {
        int vx = keyX(dest);
        int vy = keyY(dest);
        if (last.lastLayer == PipeLayer.UNDERGROUND) {
            return last.lastX == vx && last.lastY == vy && valve.linksVertical() ? PipeNode.HINT_VERTICAL : -1;
        }
        for (Direction d : DIRS) {
            if (last.lastX + d.dx == vx && last.lastY + d.dy == vy) {
                return valve.linksSide(d.opposite()) ? d.ordinal() : -1;
            }
        }
        return -1;
    }

    /** A path being stepped: its elements, the face into each (and last into the valve), the steps to each. */
    private static final class PathBuilder {
        private Object[] elements = new Object[32];
        private int[] faces = new int[33];
        private int[] steps = new int[32];
        private int size;
        private int endFace = -1;

        void add(Object element, int face, int stepsTo) {
            if (size == elements.length) {
                elements = java.util.Arrays.copyOf(elements, size * 2);
                faces = java.util.Arrays.copyOf(faces, size * 2 + 1);
                steps = java.util.Arrays.copyOf(steps, size * 2);
            }
            elements[size] = element;
            faces[size] = face;
            steps[size] = stepsTo;
            size++;
        }

        void end(int face) {
            endFace = face;
        }

        Object[] elements() {
            return java.util.Arrays.copyOf(elements, size);
        }

        int[] faces() {
            int[] result = java.util.Arrays.copyOf(faces, size + 1);
            result[size] = endFace;
            return result;
        }

        int[] steps() {
            return java.util.Arrays.copyOf(steps, size);
        }
    }

    private static boolean linked(PipeNode from, PipeNode to, int code) {
        if (code == PipeNode.HINT_VERTICAL) {
            return from.isVerticalOpen() && to.isVerticalOpen();
        }
        Direction d = DIRS[code];
        return from.isSideOpen(d) && to.isSideOpen(d.opposite());
    }

    /** Pipe path distance from the pump to a valve for {@code fluid} (for tests and the game), or -1. */
    public int getPathDistance(Pump pump, TankValve valve, FluidType fluid) {
        for (Route route : routesFor(pump, fluid)) {
            if (route.valve == valve) {
                return route.distance;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- network summary (N23-2)

    /** A run of path cells in one region, as last seen (N23-2: the regions passed through). */
    public static final class SummaryRun {
        /** The region ({@link TileBuckets#bucketOf}). */
        public final long region;
        public final int firstX;
        public final int firstY;
        public final PipeLayer firstLayer;
        public final int lastX;
        public final int lastY;
        public final PipeLayer lastLayer;
        /** Number of path cells. */
        public final int count;
        /**
         * The lowest transport amount of its cells: the cap of the stretch when it is skipped, worked
         * out as for loaded pipes (N14-2, N28-3). With the lowest tier and {@link #full}, the data the
         * summary keeps for the cap, the tier judgment (N12-4) and "only full stretches are skipped"
         * (N14-3) across unloaded regions; the runs are also cut where the pump's paths part, which
         * keeps the split per direction there (N28-3).
         */
        public final int capacity;
        /** The lowest tier of its cells (N12-4). */
        public final MineralTier lowestTier;
        /** Whether every cell was full of {@link #fluid} (only a full stretch may be skipped, N14-3, N15-1). */
        public final boolean full;
        public final FluidType fluid;
        /** The structure change number of its region when it was written (N28-1). */
        public final int regionChange;

        public SummaryRun(long region, int firstX, int firstY, PipeLayer firstLayer, int lastX, int lastY,
                          PipeLayer lastLayer, int count, int capacity, MineralTier lowestTier, boolean full,
                          FluidType fluid, int regionChange) {
            this.region = region;
            this.firstX = firstX;
            this.firstY = firstY;
            this.firstLayer = firstLayer;
            this.lastX = lastX;
            this.lastY = lastY;
            this.lastLayer = lastLayer;
            this.count = count;
            this.capacity = capacity;
            this.lowestTier = lowestTier;
            this.full = full;
            this.fluid = fluid;
            this.regionChange = regionChange;
        }

        RunKey key() {
            return new RunKey(firstX, firstY, firstLayer, lastX, lastY, lastLayer);
        }
    }

    /** The identity of an unloaded stretch; its cap counter is shared by every pump that skips it (N18-1). */
    static final class RunKey {
        final int firstX;
        final int firstY;
        final PipeLayer firstLayer;
        final int lastX;
        final int lastY;
        final PipeLayer lastLayer;

        RunKey(int firstX, int firstY, PipeLayer firstLayer, int lastX, int lastY, PipeLayer lastLayer) {
            this.firstX = firstX;
            this.firstY = firstY;
            this.firstLayer = firstLayer;
            this.lastX = lastX;
            this.lastY = lastY;
            this.lastLayer = lastLayer;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RunKey)) {
                return false;
            }
            RunKey k = (RunKey) o;
            return firstX == k.firstX && firstY == k.firstY && firstLayer == k.firstLayer && lastX == k.lastX
                    && lastY == k.lastY && lastLayer == k.lastLayer;
        }

        @Override
        public int hashCode() {
            return ((firstX * 31 + firstY) * 31 + lastX) * 31 + lastY + firstLayer.ordinal() * 7 + lastLayer.ordinal();
        }
    }

    /**
     * One pump's path to one destination in its last normal run (N23-2, N28-2): the source (the
     * pump's tile), the last output position (the valve's tile) and the regions passed through, as
     * runs in path order (cut at region borders and where the pump's paths part, N28-3).
     */
    public static final class RouteSummary {
        public final int pumpX;
        public final int pumpY;
        public final int valveX;
        public final int valveY;
        public final SummaryRun[] runs;

        public RouteSummary(int pumpX, int pumpY, int valveX, int valveY, SummaryRun[] runs) {
            this.pumpX = pumpX;
            this.pumpY = pumpY;
            this.valveX = valveX;
            this.valveY = valveY;
            this.runs = runs;
        }

        int indexOfRunAt(int x, int y, PipeLayer layer) {
            for (int i = 0; i < runs.length; i++) {
                if (runs[i].firstX == x && runs[i].firstY == y && runs[i].firstLayer == layer) {
                    return i;
                }
            }
            return -1;
        }
    }

    private RouteSummary summaryOf(Pump pump, long dest) {
        Map<Long, RouteSummary> byDest = summaries.get(key(pump.getTileX(), pump.getTileY()));
        return byDest == null ? null : byDest.get(dest);
    }

    /**
     * N28-1: a summary is intact while every region it passes still has the structure change number
     * written in it; a structure change there marked its entries invalid (N28-2).
     */
    boolean isIntact(RouteSummary summary) {
        for (SummaryRun run : summary.runs) {
            if (run.regionChange != getRegionChange(run.region)) {
                return false;
            }
        }
        return true;
    }

    /** A structure change at the tile: its region's number rises (N28-1). */
    private void structureChanged(int x, int y) {
        regionChanges.merge(TileBuckets.bucketOf(x, y), 1, Integer::sum);
    }

    /** The structure change number of a region ({@link TileBuckets#bucketOf}), N28-1. */
    public int getRegionChange(long region) {
        Integer number = regionChanges.get(region);
        return number == null ? 0 : number;
    }

    /**
     * The structure change numbers the game saves with the level (N28-1): those of the regions some
     * summary passes. No summary needs the others; after a restart they count from 0 again, and a
     * summary written then keeps that.
     */
    public Map<Long, Integer> getSavedRegionChanges() {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Map<Long, RouteSummary> byDest : summaries.values()) {
            for (RouteSummary summary : byDest.values()) {
                for (SummaryRun run : summary.runs) {
                    Integer number = regionChanges.get(run.region);
                    if (number != null) {
                        result.put(run.region, number);
                    }
                }
            }
        }
        return result;
    }

    /** Restores a saved structure change number (level load, N28-1). */
    public void loadRegionChange(long region, int number) {
        if (number == 0) {
            regionChanges.remove(region);
        } else {
            regionChanges.put(region, number);
        }
    }

    /** Every summary (the game saves them with the level, N23-2). */
    public List<RouteSummary> getSummaries() {
        List<RouteSummary> result = new ArrayList<>();
        for (Map<Long, RouteSummary> byDest : summaries.values()) {
            result.addAll(byDest.values());
        }
        return result;
    }

    /** Restores a saved summary (level load). */
    public void loadSummary(RouteSummary summary) {
        changed();
        putSummary(key(summary.pumpX, summary.pumpY), key(summary.valveX, summary.valveY), summary);
    }

    private void putSummary(long pump, long dest, RouteSummary summary) {
        Map<Long, RouteSummary> byDest = summaries.get(pump);
        if (byDest == null) {
            byDest = new LinkedHashMap<>();
            summaries.put(pump, byDest);
        }
        byDest.put(dest, summary);
        Set<Long> pumpsTo = summaryPumpsByDestination.get(dest);
        if (pumpsTo == null) {
            pumpsTo = new LinkedHashSet<>();
            summaryPumpsByDestination.put(dest, pumpsTo);
        }
        pumpsTo.add(pump);
    }

    private void dropSummariesTo(long dest) {
        Set<Long> pumpsTo = summaryPumpsByDestination.remove(dest);
        if (pumpsTo == null) {
            return;
        }
        changed();
        for (long pump : pumpsTo) {
            Map<Long, RouteSummary> byDest = summaries.get(pump);
            if (byDest != null) {
                byDest.remove(dest);
                if (byDest.isEmpty()) {
                    summaries.remove(pump);
                }
            }
        }
    }

    private void dropSummariesOf(long pump) {
        Map<Long, RouteSummary> byDest = summaries.remove(pump);
        if (byDest != null) {
            changed();
            for (long dest : byDest.keySet()) {
                Set<Long> pumpsTo = summaryPumpsByDestination.get(dest);
                if (pumpsTo != null) {
                    pumpsTo.remove(pump);
                    if (pumpsTo.isEmpty()) {
                        summaryPumpsByDestination.remove(dest);
                    }
                }
            }
        }
    }

    /**
     * Writes the summaries of a push (N28-2): every destination path that is a normal run, every cell
     * of it loaded and none removed, gets its whole summary written again. A path that skipped an
     * unloaded stretch leaves its summary as it is. The runs are cut at region borders and right
     * after the cells where the pump's destination paths part (N28-3).
     */
    private void recordSummaries(Pump pump, List<Route> routes, FluidType fluid) {
        Junctions junctions = null;
        for (Route route : routes) {
            if (valves.get(route.dest) != route.valve || !isNormalRun(route)) {
                continue;
            }
            if (junctions == null) {
                junctions = new Junctions(routes);
            }
            recordSummary(pump, route, fluid, junctions.partsAfter(route));
        }
    }

    private static boolean isNormalRun(Route route) {
        for (Object element : route.path) {
            if (!(element instanceof PipeNode) || ((PipeNode) element).removed) {
                return false;
            }
        }
        return true;
    }

    /** Where a pump's paths part (N28-3): the paths' elements as a tree from the pump. */
    private static final class Junctions {
        private final Map<Object, Junctions> next = new HashMap<>(4);

        Junctions() {
        }

        Junctions(List<Route> routes) {
            for (Route route : routes) {
                Junctions node = this;
                for (int i = 0; i <= route.path.length; i++) {
                    Object element = route.element(i);
                    Junctions child = node.next.get(element);
                    if (child == null) {
                        child = new Junctions();
                        node.next.put(element, child);
                    }
                    node = child;
                }
            }
        }

        /** For each element of the route, whether the paths part right after it. */
        boolean[] partsAfter(Route route) {
            boolean[] result = new boolean[route.path.length];
            Junctions node = this;
            for (int i = 0; i < route.path.length; i++) {
                node = node.next.get(route.element(i));
                result[i] = node.next.size() > 1;
            }
            return result;
        }
    }

    /** Writes one destination's summary from a normal run (every element a loaded pipe). */
    private void recordSummary(Pump pump, Route route, FluidType fluid, boolean[] partsAfter) {
        List<SummaryRun> runs = new ArrayList<>();
        PipeNode first = null;
        PipeNode last = null;
        long region = 0;
        int count = 0;
        int capacity = 0;
        MineralTier lowest = null;
        boolean full = true;
        for (int i = 0; i < route.path.length; i++) {
            PipeNode node = (PipeNode) route.path[i];
            long here = TileBuckets.bucketOf(node.getTileX(), node.getTileY());
            if (first != null && (here != region || partsAfter[i - 1])) {
                runs.add(newRun(region, first, last, count, capacity, lowest, full, fluid));
                first = null;
            }
            if (first == null) {
                first = node;
                region = here;
                count = 0;
                capacity = Integer.MAX_VALUE;
                lowest = node.getTier();
                full = true;
            }
            last = node;
            count++;
            capacity = Math.min(capacity, node.getCapacity());
            lowest = MineralTier.lowest(lowest, node.getTier());
            full &= node.getFluid() == fluid && node.isFull();
        }
        if (first != null) {
            runs.add(newRun(region, first, last, count, capacity, lowest, full, fluid));
        }
        putSummary(key(pump.getTileX(), pump.getTileY()), route.dest,
                new RouteSummary(pump.getTileX(), pump.getTileY(), route.valveX, route.valveY,
                        runs.toArray(new SummaryRun[0])));
    }

    private SummaryRun newRun(long region, PipeNode first, PipeNode last, int count, int capacity, MineralTier lowest,
                              boolean full, FluidType fluid) {
        return new SummaryRun(region, first.getTileX(), first.getTileY(), first.getLayer(), last.getTileX(),
                last.getTileY(), last.getLayer(), count, capacity, lowest, full, fluid, getRegionChange(region));
    }

    // ---------------------------------------------------------------- pushing

    /**
     * The destinations of one push from {@code pump} with {@code fluid}: valves reached along the
     * hints through pipes that can carry it, excluding the tanks the pump is linked to for pulling.
     * With at least one destination, the ends of the dead-end branches are filled too (N28-6).
     */
    @Override
    public PushPlan planPush(Pump pump, FluidType fluid) {
        List<TankStorage> sourceTanks = pump.getSourceTanks();
        List<Route> destinations = new ArrayList<>();
        for (Route route : routesFor(pump, fluid)) {
            TankStorage tank = route.valve.getTank();
            boolean ownSource = false;
            for (TankStorage source : sourceTanks) {
                if (source == tank) {
                    ownSource = true;
                    break;
                }
            }
            if (!ownSource) {
                destinations.add(route);
            }
        }
        List<Route> branches = destinations.isEmpty() ? Collections.<Route>emptyList()
                : branchRoutes(pump, fluid, destinations);
        return new GridPushPlan(pump, fluid, destinations, branches);
    }

    /**
     * The paths to the ends of the dead-end branches (N28-6). The branches are the loaded pipes the
     * fluid can enter (empty or holding it, N13-2) that are on no destination path, followed outward
     * from the pipes of those paths and from the pump's own output cells. Each end (a branch pipe with
     * nothing further) whose branch is not full yet gets a path: the destination path it hangs from
     * up to the pipe it leaves, then its branch. So the paths part where the branch leaves, and that
     * direction counts at the junction (N28-8). Kept while nothing changes (technical).
     */
    private List<Route> branchRoutes(Pump pump, FluidType fluid, List<Route> destinations) {
        RouteMemo memo = routeMemo.get(pump);
        boolean memoValid = memo != null && memo.fluid == fluid && memo.changes == changes;
        if (memoValid && destinations.equals(memo.branchDestinations)) {
            return memo.branches;
        }
        Set<PipeNode> onPath = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
        for (Route route : destinations) {
            for (Object element : route.path) {
                if (element instanceof PipeNode) {
                    onPath.add((PipeNode) element);
                }
            }
        }
        Set<PipeNode> claimed = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
        Set<PipeNode> hasNext = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
        Map<PipeNode, PipeNode> before = new IdentityHashMap<>();
        // Where each branch leaves: {destination index, element index}, or {-1, side} at the pump.
        Map<PipeNode, int[]> leaves = new IdentityHashMap<>();
        ArrayDeque<PipeNode> queue = new ArrayDeque<>();
        for (Direction d : DIRS) {
            PipeNode node = basePipes.get(key(pump.getTileX() + d.dx, pump.getTileY() + d.dy));
            if (node != null && pump.isSideOpen(d) && node.isSideOpen(d.opposite()) && !onPath.contains(node)
                    && traversable(node, fluid) && claimed.add(node)) {
                leaves.put(node, new int[]{-1, d.ordinal()});
                queue.add(node);
            }
        }
        for (int r = 0; r < destinations.size(); r++) {
            Object[] path = destinations.get(r).path;
            for (int i = 0; i < path.length; i++) {
                if (!(path[i] instanceof PipeNode)) {
                    continue;
                }
                for (PipeNode next : linkedPipes((PipeNode) path[i])) {
                    if (!onPath.contains(next) && traversable(next, fluid) && claimed.add(next)) {
                        leaves.put(next, new int[]{r, i});
                        queue.add(next);
                    }
                }
            }
        }
        List<PipeNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            PipeNode cell = queue.poll();
            order.add(cell);
            for (PipeNode next : linkedPipes(cell)) {
                if (!onPath.contains(next) && traversable(next, fluid) && claimed.add(next)) {
                    before.put(next, cell);
                    hasNext.add(cell);
                    queue.add(next);
                }
            }
        }
        List<Route> result = new ArrayList<>();
        for (PipeNode end : order) {
            if (hasNext.contains(end)) {
                continue;
            }
            List<PipeNode> chain = new ArrayList<>();
            boolean room = false;
            for (PipeNode cell = end; cell != null; cell = before.get(cell)) {
                chain.add(cell);
                room |= cell.getFluid() != fluid || !cell.isFull();
            }
            if (!room) {
                continue;
            }
            Collections.reverse(chain);
            int[] from = leaves.get(chain.get(0));
            Route base = from[0] < 0 ? null : destinations.get(from[0]);
            int prefix = base == null ? 0 : from[1] + 1;
            int length = prefix + chain.size();
            Object[] path = new Object[length];
            int[] faces = new int[length + 1];
            int[] steps = new int[length];
            if (base != null) {
                System.arraycopy(base.path, 0, path, 0, prefix);
                System.arraycopy(base.faces, 0, faces, 0, prefix);
                System.arraycopy(base.stepsTo, 0, steps, 0, prefix);
            }
            for (int j = 0; j < chain.size(); j++) {
                int e = prefix + j;
                path[e] = chain.get(j);
                faces[e] = e == 0 ? from[1] : codeToward((PipeNode) path[e - 1], chain.get(j));
                steps[e] = e == 0 ? 1 : steps[e - 1] + 1;
            }
            faces[length] = -1;
            result.add(new Route(null, Long.MIN_VALUE, path, faces, steps, steps[length - 1]));
        }
        if (memoValid) {
            memo.branchDestinations = new ArrayList<>(destinations);
            memo.branches = result;
        }
        return result;
    }

    /** The order of the directions at a junction (N28-5): the other layer (vertical) first, then N, E, S, W. */
    static int directionOrder(int code) {
        return code == PipeNode.HINT_VERTICAL ? -1 : code < 0 ? Integer.MAX_VALUE : code;
    }

    /** One push: the destinations and the logic that moves fluid to them, as a dry run or for real. */
    final class GridPushPlan implements PushPlan {
        private final Pump pump;
        private final FluidType fluid;
        private final List<Route> destinations;
        /** The ends of the dead-end branches (N28-6): filled like destinations, without a tank. */
        private final List<Route> branches;

        GridPushPlan(Pump pump, FluidType fluid, List<Route> destinations, List<Route> branches) {
            this.pump = pump;
            this.fluid = fluid;
            this.destinations = destinations;
            this.branches = branches;
        }

        /** What a push of {@code amount} would use: filled, delivered and lost. */
        @Override
        public long simulate(int amount) {
            Stats stats = distribute(amount, new DryLedger());
            return (long) stats.pipeFill + stats.deliveredTotal + stats.lost;
        }

        @Override
        public PumpResult run(int amount) {
            Stats stats = distribute(amount, new RealLedger());
            Long recorded = summaryRecordedAt.get(pump);
            if (recorded == null || recorded != changes) {
                recordSummaries(pump, destinations, fluid);
                summaryRecordedAt.put(pump, changes);
            }
            return new PumpResult(PumpResult.Status.PUMPED, fluid, stats.pipeFill + stats.deliveredTotal, stats.pipeFill,
                    stats.updated.size(), stats.lost, stats.delivered, stats.broken);
        }

        /**
         * Amounts over the paths that have room, delivered nearest first; what one cannot take is split
         * again among the others (N7-2, N20-6).
         */
        private Stats distribute(int amount, Ledger ledger) {
            Stats stats = new Stats();
            List<Route> active = new ArrayList<>();
            for (Route route : destinations) {
                if (space(route, ledger) > 0) {
                    active.add(route);
                }
            }
            for (Route route : branches) {
                if (space(route, ledger) > 0) {
                    active.add(route);
                }
            }
            int remaining = amount;
            while (remaining > 0 && !active.isEmpty()) {
                Map<Route, Integer> shares = splitAtJunctions(remaining, active, ledger);
                int used = 0;
                List<Route> saturated = new ArrayList<>();
                for (Route route : active) {
                    Integer share = shares.get(route);
                    if (share == null || share == 0) {
                        continue;
                    }
                    int taken = deliver(route, share, ledger, stats);
                    used += taken;
                    if (taken < share) {
                        saturated.add(route);
                    }
                }
                remaining -= used;
                for (int i = active.size() - 1; i >= 0; i--) {
                    Route route = active.get(i);
                    if (saturated.contains(route) || space(route, ledger) == 0) {
                        active.remove(i);
                    }
                }
                if (used == 0) {
                    break;
                }
            }
            return stats;
        }

        /** Room at a path's end: its tank's space, or for a dead-end end (N28-6) 1 while a pipe is left to fill. */
        private int space(Route route, Ledger ledger) {
            if (route.valve != null) {
                return ledger.tankSpace(route.valve);
            }
            int frontier = advance(route, ledger);
            return frontier >= 0 && frontier < route.path.length ? 1 : 0;
        }

        /**
         * The amount is split where the paths part (N24-3, N28-8): while filling, one equal amount per
         * open direction, a dead-end direction too; otherwise by the number of destinations behind each
         * direction. The remainder of each split goes one unit per direction or destination, directions
         * taken vertical, north, east, south, west (N25-5, N27-1, N28-5).
         */
        private Map<Route, Integer> splitAtJunctions(int amount, List<Route> active, Ledger ledger) {
            Map<Route, Integer> shares = new IdentityHashMap<>();
            split(amount, new ArrayList<>(active), 0, shares, ledger);
            return shares;
        }

        /**
         * Splits {@code amount} among the paths of {@code group}, the destinations this amount carries:
         * at each junction only those count, and each direction's amount carries its own part of them
         * on (N28-14), so a loop never counts a destination twice.
         */
        private void split(int amount, List<Route> group, int depth, Map<Route, Integer> out, Ledger ledger) {
            if (group.size() == 1) {
                out.put(group.get(0), amount);
                return;
            }
            int k = depth;
            while (true) {
                Object element = group.get(0).element(k);
                boolean same = true;
                for (Route route : group) {
                    if (!element.equals(route.element(k))) {
                        same = false;
                        break;
                    }
                }
                if (!same) {
                    break;
                }
                k++;
            }
            // Group the paths by the element they step to: one group per outgoing direction.
            Map<Object, List<Route>> byNext = new LinkedHashMap<>();
            Map<Object, Integer> faceOf = new HashMap<>();
            for (Route route : group) {
                Object next = route.element(k);
                List<Route> members = byNext.get(next);
                if (members == null) {
                    members = new ArrayList<>();
                    byNext.put(next, members);
                    faceOf.put(next, route.faces[Math.min(k, route.faces.length - 1)]);
                }
                members.add(route);
            }
            List<Object> faces = new ArrayList<>(byNext.keySet());
            faces.sort(Comparator.comparingInt(face -> directionOrder(faceOf.get(face))));
            boolean filling = isFilling(group, k, ledger);
            int units = filling ? faces.size() : group.size();
            int base = amount / units;
            int extra = amount % units;
            for (Object face : faces) {
                List<Route> members = byNext.get(face);
                int weight = filling ? 1 : members.size();
                int bonus = Math.min(extra, weight);
                extra -= bonus;
                split(base * weight + bonus, members, k + 1, out, ledger);
            }
        }

        /**
         * N28-8 "while filling", read per junction (TODO(confirm), see the class comment): a pipe from
         * element {@code k} on, on one of the group's paths, is not full of the fluid yet.
         */
        private boolean isFilling(List<Route> group, int k, Ledger ledger) {
            for (Route route : group) {
                int frontier = advance(route, ledger);
                if (frontier < 0) {
                    continue;
                }
                if (frontier >= k) {
                    if (frontier < route.path.length) {
                        return true;
                    }
                    continue;
                }
                for (int i = k; i < route.path.length; i++) {
                    if (route.path[i] instanceof PipeNode) {
                        PipeNode node = (PipeNode) route.path[i];
                        if (ledger.fluid(node) != fluid || ledger.amount(node) < node.getCapacity()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Moves one share along a route: the frontier pipes first, then the tank. Returns what the
         * share used: filled, delivered, and lost into a pipe that broke (N14-1).
         */
        private int deliver(Route route, int share, Ledger ledger, Stats stats) {
            Object[] path = route.path;
            int used = 0;
            while (share > 0) {
                int frontier = advance(route, ledger);
                if (frontier < 0) {
                    break;
                }
                if (frontier < path.length) {
                    PipeNode node = (PipeNode) path[frontier];
                    if (ledger.amount(node) == 0) {
                        MineralTier lowest = lowestBefore(route, frontier);
                        lowest = lowest == null ? node.getTier() : MineralTier.lowest(lowest, node.getTier());
                        if (!tierRules.canCarry(lowest, fluid)) {
                            // Only the newly reached pipe breaks, only this share is lost (N12-4, N12-5, N14-1).
                            ledger.breakPipe(node, stats);
                            stats.lost += share;
                            used += share;
                            break;
                        }
                    }
                    int put = Math.min(Math.min(share, node.getCapacity() - ledger.amount(node)),
                            flowLeft(path, frontier + 1, ledger));
                    if (put <= 0) {
                        break;
                    }
                    ledger.fill(node, put, stats);
                    useFlow(path, frontier + 1, put, ledger);
                    stats.pipeFill += put;
                    used += put;
                    share -= put;
                    if (ledger.amount(node) < node.getCapacity()) {
                        // Limited by the flow cap: the rest of the share cannot pass this cycle.
                        break;
                    }
                } else {
                    if (route.valve == null) {
                        // The end of a dead-end branch, full (N28-6).
                        break;
                    }
                    int take = Math.min(Math.min(share, ledger.tankSpace(route.valve)), flowLeft(path, path.length, ledger));
                    if (take <= 0) {
                        break;
                    }
                    ledger.deliver(route.valve, take, stats);
                    useFlow(path, path.length, take, ledger);
                    used += take;
                    break;
                }
            }
            return used;
        }

        /**
         * Moves the route's frontier past full pipes of this fluid and skipped runs (reads only).
         * Returns the frontier, {@code path.length} when every pipe is full, or -1 when the path is
         * blocked: a pipe was removed or holds another fluid (a dead end, N13-2).
         */
        private int advance(Route route, Ledger ledger) {
            Object[] path = route.path;
            int frontier = ledger.frontier(route);
            while (frontier < path.length) {
                if (path[frontier] instanceof SummaryRun) {
                    frontier++;
                    continue;
                }
                PipeNode node = (PipeNode) path[frontier];
                if (ledger.removed(node)) {
                    return -1;
                }
                FluidType held = ledger.fluid(node);
                if (held != null && held != fluid) {
                    return -1;
                }
                if (held == fluid && ledger.amount(node) >= node.getCapacity()) {
                    frontier++;
                    continue;
                }
                break;
            }
            ledger.setFrontier(route, frontier);
            return frontier;
        }

        /** The lowest tier of the network the fluid comes from into {@code path[index]} (N12-4). */
        private MineralTier lowestBefore(Route route, int index) {
            if (index == 0) {
                PipeNetwork network = pump.network;
                return network != null && pump.getLastPushedFluid() == fluid ? network.lowestTier : null;
            }
            Object previous = route.path[index - 1];
            if (previous instanceof PipeNode && ((PipeNode) previous).network != null) {
                return ((PipeNode) previous).network.lowestTier;
            }
            // Dry run, or the pipe before is a skipped unloaded stretch: the pump's network and every
            // cell before on the path.
            MineralTier lowest = lowestBefore(route, 0);
            for (int i = 0; i < index; i++) {
                MineralTier tier = route.path[i] instanceof PipeNode ? ((PipeNode) route.path[i]).getTier()
                        : ((SummaryRun) route.path[i]).lowestTier;
                lowest = lowest == null ? tier : MineralTier.lowest(lowest, tier);
            }
            return lowest;
        }

        private int flowLeft(Object[] path, int count, Ledger ledger) {
            int left = Integer.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                left = Math.min(left, path[i] instanceof PipeNode ? ledger.flowLeft((PipeNode) path[i])
                        : ledger.runFlowLeft((SummaryRun) path[i]));
            }
            return left;
        }

        private void useFlow(Object[] path, int count, int amount, Ledger ledger) {
            for (int i = 0; i < count; i++) {
                if (path[i] instanceof PipeNode) {
                    ledger.useFlow((PipeNode) path[i], amount);
                } else {
                    ledger.useRunFlow((SummaryRun) path[i], amount);
                }
            }
        }

        /** Real or dry state of the pipes, tanks and flow caps the push reads and writes. */
        private abstract class Ledger {
            final long window = window();

            abstract int amount(PipeNode node);

            abstract FluidType fluid(PipeNode node);

            abstract boolean removed(PipeNode node);

            abstract int flowLeft(PipeNode node);

            abstract void useFlow(PipeNode node, int amount);

            abstract int runFlowLeft(SummaryRun run);

            abstract void useRunFlow(SummaryRun run, int amount);

            abstract void fill(PipeNode node, int amount, Stats stats);

            abstract int tankSpace(TankValve valve);

            abstract void deliver(TankValve valve, int amount, Stats stats);

            abstract void breakPipe(PipeNode node, Stats stats);

            abstract int frontier(Route route);

            abstract void setFrontier(Route route, int frontier);

            int realRunFlowLeft(SummaryRun run) {
                long[] used = runFlow.get(run.key());
                return used == null || used[0] != window ? run.capacity : (int) Math.max(0, run.capacity - used[1]);
            }
        }

        private final class RealLedger extends Ledger {

            @Override
            int amount(PipeNode node) {
                return node.getAmount();
            }

            @Override
            FluidType fluid(PipeNode node) {
                return node.getFluid();
            }

            @Override
            boolean removed(PipeNode node) {
                return node.removed;
            }

            @Override
            int flowLeft(PipeNode node) {
                return node.flowLeft(window);
            }

            @Override
            void useFlow(PipeNode node, int amount) {
                node.useFlow(window, amount);
            }

            @Override
            int runFlowLeft(SummaryRun run) {
                return realRunFlowLeft(run);
            }

            @Override
            void useRunFlow(SummaryRun run, int amount) {
                RunKey key = run.key();
                long[] used = runFlow.get(key);
                if (used == null || used[0] != window) {
                    runFlow.put(key, new long[]{window, amount});
                } else {
                    used[1] += amount;
                }
            }

            @Override
            void fill(PipeNode node, int amount, Stats stats) {
                boolean reached = node.isReached();
                node.insert(fluid, amount);
                stats.updated.add(node);
                if (!reached && node.isReached()) {
                    attachReached(node);
                }
                if (node.isFull()) {
                    changed();
                }
            }

            @Override
            int tankSpace(TankValve valve) {
                return valve.getSpaceFor(fluid);
            }

            @Override
            void deliver(TankValve valve, int amount, Stats stats) {
                int taken = valve.insert(fluid, amount);
                stats.deliveredTotal += taken;
                Integer before = stats.delivered.get(valve);
                stats.delivered.put(valve, (before == null ? 0 : before) + taken);
            }

            @Override
            void breakPipe(PipeNode node, Stats stats) {
                removePipe(node.getTileX(), node.getTileY(), node.getLayer());
                stats.broken.add(node);
            }

            @Override
            int frontier(Route route) {
                return route.frontier;
            }

            @Override
            void setFrontier(Route route, int frontier) {
                route.frontier = frontier;
            }
        }

        private final class DryLedger extends Ledger {
            /** Marks the pipes this dry run wrote to (their dry fields), instead of maps (technical). */
            private final long stamp = ++dryStamps;
            private final Map<RunKey, Integer> runs = new HashMap<>();
            private final Map<TankStorage, Integer> tanks = new IdentityHashMap<>();
            private final Set<PipeNode> broken = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
            private final Map<Route, Integer> frontiers = new IdentityHashMap<>();

            private void touch(PipeNode node) {
                if (node.dryStamp != stamp) {
                    node.dryStamp = stamp;
                    node.dryAdded = 0;
                    node.dryFlow = 0;
                    node.dryFilled = false;
                }
            }

            @Override
            int amount(PipeNode node) {
                return node.getAmount() + (node.dryStamp == stamp ? node.dryAdded : 0);
            }

            @Override
            FluidType fluid(PipeNode node) {
                return node.getFluid() != null ? node.getFluid() : node.dryStamp == stamp && node.dryFilled ? fluid : null;
            }

            @Override
            boolean removed(PipeNode node) {
                return node.removed || !broken.isEmpty() && broken.contains(node);
            }

            @Override
            int flowLeft(PipeNode node) {
                int used = node.dryStamp == stamp ? node.dryFlow : 0;
                return Math.max(0, node.flowLeft(window) - used);
            }

            @Override
            void useFlow(PipeNode node, int amount) {
                touch(node);
                node.dryFlow += amount;
            }

            @Override
            int runFlowLeft(SummaryRun run) {
                Integer used = runs.get(run.key());
                return Math.max(0, realRunFlowLeft(run) - (used == null ? 0 : used));
            }

            @Override
            void useRunFlow(SummaryRun run, int amount) {
                runs.merge(run.key(), amount, Integer::sum);
            }

            @Override
            void fill(PipeNode node, int amount, Stats stats) {
                touch(node);
                node.dryAdded += amount;
                node.dryFilled = true;
            }

            @Override
            int tankSpace(TankValve valve) {
                TankStorage tank = valve.getTank();
                Integer used = tank == null ? null : tanks.get(tank);
                return Math.max(0, valve.getSpaceFor(fluid) - (used == null ? 0 : used));
            }

            @Override
            void deliver(TankValve valve, int amount, Stats stats) {
                tanks.merge(valve.getTank(), amount, Integer::sum);
                stats.deliveredTotal += amount;
            }

            @Override
            void breakPipe(PipeNode node, Stats stats) {
                broken.add(node);
            }

            @Override
            int frontier(Route route) {
                Integer frontier = frontiers.get(route);
                return frontier == null ? route.frontier : frontier;
            }

            @Override
            void setFrontier(Route route, int frontier) {
                frontiers.put(route, frontier);
            }
        }
    }

    private static final class Stats {
        final Set<PipeNode> updated = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
        final Map<TankValve, Integer> delivered = new LinkedHashMap<>();
        final List<PipeNode> broken = new ArrayList<>();
        int pipeFill;
        int deliveredTotal;
        int lost;
    }

    // ---------------------------------------------------------------- internals

    private Map<Long, PipeNode> pipes(PipeLayer layer) {
        return layer == PipeLayer.BASE ? basePipes : undergroundPipes;
    }

    private boolean isBaseLayerTaken(long key) {
        return basePipes.containsKey(key) || valves.containsKey(key) || pumps.containsKey(key);
    }

    /** Pipes linked to {@code node}: same layer sides (9-4) and the other layer on the tile (N16-4). */
    private List<PipeNode> linkedPipes(PipeNode node) {
        List<PipeNode> result = new ArrayList<>(5);
        for (int code = 0; code <= PipeNode.HINT_VERTICAL; code++) {
            PipeNode next = node.around[code];
            if (next != null && linked(node, next, code)) {
                result.add(next);
            }
        }
        return result;
    }

    /**
     * Adds a pipe to the cell index and links it with the pipes around it (its neighbours on its
     * layer and the other layer's pipe on its tile), so stepping never looks a cell up (technical).
     */
    private void putCell(PipeNode node) {
        int x = node.getTileX();
        int y = node.getTileY();
        pipes(node.getLayer()).put(key(x, y), node);
        for (Direction d : DIRS) {
            PipeNode next = getPipe(x + d.dx, y + d.dy, node.getLayer());
            node.around[d.ordinal()] = next;
            if (next != null) {
                next.around[d.opposite().ordinal()] = node;
            }
        }
        PipeNode other = getPipe(x, y, node.getLayer().other());
        node.around[PipeNode.HINT_VERTICAL] = other;
        if (other != null) {
            other.around[PipeNode.HINT_VERTICAL] = node;
        }
        changed();
    }

    /** Takes a pipe out of the cell index and unlinks it from the pipes around it. */
    private PipeNode takeCell(int x, int y, PipeLayer layer) {
        PipeNode node = pipes(layer).remove(key(x, y));
        if (node == null) {
            return null;
        }
        for (int code = 0; code < DIRS.length; code++) {
            PipeNode next = node.around[code];
            if (next != null && next.around[DIRS[code].opposite().ordinal()] == node) {
                next.around[DIRS[code].opposite().ordinal()] = null;
            }
            node.around[code] = null;
        }
        PipeNode other = node.around[PipeNode.HINT_VERTICAL];
        if (other != null && other.around[PipeNode.HINT_VERTICAL] == node) {
            other.around[PipeNode.HINT_VERTICAL] = null;
        }
        node.around[PipeNode.HINT_VERTICAL] = null;
        changed();
        return node;
    }

    /** Something the routes depend on changed: the routes stepped so far are stepped again (technical). */
    private void changed() {
        changes++;
    }

    public static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyY(long key) {
        return (int) key;
    }

}
