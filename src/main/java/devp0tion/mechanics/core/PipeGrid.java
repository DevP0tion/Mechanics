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
 * <h2>Cell hints (N22-3, N24-2, N25-1, N25-4, N25-6)</h2>
 * Instead of a route cache per pump, each pipe keeps, per destination valve, the direction of the
 * next step toward it ({@link PipeNode#getHint}), no distance. A pump steps along them from its
 * output cells; the step count, when needed, is counted while stepping.
 * <ul>
 *     <li>A destination's hints come from a search back from its valve over the pipes the fluid can
 *     pass (empty, or holding one fluid along the way), loaded ones only. Ties of equal length go
 *     by the search order (north, east, south, west, then the other layer).</li>
 *     <li>A structure change marks only the destinations whose hints are at the changed cell or
 *     next to it (and a valve there); they are searched again when they are used next: at the next
 *     pump cycle, before the pump reads its destinations (N25-4). A new valve is searched at the
 *     next pump cycle; a loaded valve whose pipes already hold its saved hints is not (no full
 *     recompute on the first cycle, N25-1).
 *     TODO(confirm): a stale destination is searched at the next pump cycle of any pump, since a
 *     pump cannot step toward a destination it does not know yet (a new valve, a new connection).</li>
 *     <li>Hints are repaired when used: stepping reads every cell, so a missing pipe, a cut link or a
 *     loop sends the destination to a new search, and a cell of another fluid is a dead end for that
 *     push (N13-2).</li>
 *     <li>When a region loads, only destinations whose saved hints disagree with the pipes around
 *     the loaded pipe are marked (hints saved at different times, N25-6).</li>
 *     <li>TODO(confirm): the hint codes are the four directions plus one for the other layer on the
 *     same tile (basic to underground pipe, underground pipe to the valve on its tile), so a code
 *     takes 3 bits rather than 2 (N24-2).</li>
 *     <li>TODO(confirm): N25-1 "hints are built as the fluid fills empty pipes" is read as: the
 *     search back from the valve covers empty pipes too, and the fluid then fills along those hints
 *     at the movement speed. Fluid never fills dead-end branches (no destination there, N7-4).</li>
 * </ul>
 *
 * <h2>Pushing (N7, N12, N14, N18-1, N23-1, N24-3, N25-3, N26-4)</h2>
 * <ul>
 *     <li>A pump's destinations are the valves its output cells have hints for, reached through
 *     pipes that are empty or hold its fluid, whose tank has room (the pump's own source tanks
 *     excluded), each along its hints from the nearest output cell (the pump's first pipe = 1).</li>
 *     <li>Distribution (N24-3): the pushed amount is split where the destinations' paths part, by
 *     the number of destinations behind each outgoing face; at each junction, what does not divide
 *     evenly goes one unit per destination, destinations taken by face in the fixed order north,
 *     east, south, west (N25-5, N27-1). TODO(confirm): the other layer (vertical) face comes after
 *     west. A share a destination cannot take is split again among the others.</li>
 *     <li>Each share fills the pipes along its path, then enters the tank. Only the frontier (the
 *     first pipe not yet full) is written; full pipes are never written again (N7-1).</li>
 *     <li>Fill speed (N25-1~N25-3): an empty pipe is reached no sooner than its tier's movement
 *     speed after the pipe before it on the path ({@link PipeTierRules#getFillTicksPerBlock}); full
 *     stretches pass within the cycle.</li>
 *     <li>Each pipe lets at most its transport amount through per cycle window of
 *     {@link #CYCLE_TICKS} ticks, counted while stepping each share from the output cell to its end
 *     (N14-2, N23-1). Pumps pushing through the same pipe add up and share it (N18-1): among pumps
 *     whose cycles run in the same tick, the pump nearer to the shared pipe (fewer steps) goes
 *     first, equal ones by connection order (N26-4). TODO(confirm): pumps whose cycles run at
 *     different ticks of a window take the cap in time order.</li>
 *     <li>When the fluid reaches a new pipe, the tier conditions are judged against the lowest tier
 *     of the network it comes from and that pipe (N12-4, N14-1); if they fail, only that pipe breaks
 *     and only the share headed into it is lost.</li>
 *     <li>No destination, or every destination full: the pump stops (N7-4).</li>
 * </ul>
 *
 * <h2>Unloaded regions: the network summary (N14-3, N15-1, N23-2)</h2>
 * The engine holds no mirror of unloaded pipes. Instead, each pump's paths of its last normal cycle
 * are summarized per destination ({@link RouteSummary}): the pump (source position), the valve (last
 * output position) and the regions passed through, as runs of path cells per region with their
 * count, lowest transport amount and tier, and whether they were full. While a summary is intact a
 * path may skip an unloaded region whose run was full of its fluid, also several in a row; any other
 * unloaded cell is a dead end. Runs in loaded regions are refreshed every cycle (a region that loads
 * while the network runs is updated while it stays loaded).
 * TODO(confirm): "intact (무결)" is read as: no structure change has touched the destination's
 * hints (the destinations a change marks, see above) since the summary was last recorded.
 *
 * <h2>Compatibility mode (N26-1)</h2>
 * {@link #setCompatMode} switches to the old rules, for comparison tests only: the remainder of a
 * split goes one unit each to the nearest destinations over all of them (N7-2), empty pipes fill
 * without the speed limit, and pumps take shared caps in the order they run (connection order).
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

    private static final Listener NO_LISTENER = new Listener() {
        @Override
        public void onLinksChanged(int tileX, int tileY, Part part) {
        }
    };

    private final PipeTierRules tierRules;
    private Listener listener = NO_LISTENER;
    private boolean compatMode;
    private TileLoadedLookup loadedLookup;

    // World: the cell index and the references to the components (N22-2).
    private final Map<Long, PipeNode> basePipes = new HashMap<>();
    private final Map<Long, PipeNode> undergroundPipes = new HashMap<>();
    private final Map<Long, TankValve> valves = new HashMap<>();
    private final Map<TankValve, Long> valvePositions = new IdentityHashMap<>();
    /** Pumps in connection order (N26-4). */
    private final Map<Long, Pump> pumps = new LinkedHashMap<>();
    private final Map<Pump, Long> connectionIndex = new IdentityHashMap<>();
    private long nextConnection;
    private final Set<PipeNetwork> networks = new LinkedHashSet<>();
    /** Tiles of parts that left because their region unloaded, while no {@link #loadedLookup} is set. */
    private final Set<Long> unloadedTiles = new HashSet<>();
    private long tick;

    // Hints (derived data, saved by the pipes' holders, N25-6).
    private final Map<Long, Set<PipeNode>> hintCells = new HashMap<>();
    private final Set<Long> staleDestinations = new LinkedHashSet<>();
    private final Set<Long> checkDestinations = new LinkedHashSet<>();
    private final Map<Long, Long> searchedAt = new HashMap<>();

    // Network summaries (N23-2): pump tile -> destination -> summary.
    private final Map<Long, Map<Long, RouteSummary>> summaries = new LinkedHashMap<>();
    private final Map<Long, Set<Long>> summaryPumpsByDestination = new HashMap<>();
    private final Map<RunKey, long[]> runFlow = new HashMap<>();

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
     * The comparison-only compatibility mode (N26-1): the old rules (remainder to the nearest over
     * all destinations, N7-2; empty pipes fill without the speed limit; pumps take shared caps in
     * the order they run). Never set by the game.
     */
    public void setCompatMode(boolean compatMode) {
        this.compatMode = compatMode;
    }

    public boolean isCompatMode() {
        return compatMode;
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
    @Override
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
     *     <li>Sources: a liquid tile source not judged yet is judged with the loaded cells (N20-7).</li>
     *     <li>Timers: lit logs burn down (N18-4), cycle counters advance (N6-1, N3-3), wire state is
     *     the pump's {@code enabled} (N11-3).</li>
     *     <li>Clicks queued since the last tick (manual pumps).</li>
     *     <li>Push: the pumps due now, ordered for the shared caps (N26-4; connection order in the
     *     compatibility mode). Hints are repaired as the pumps use them (N25-4).</li>
     * </ol>
     *
     * @return the results of the cycles that ran, by pump tile
     */
    public Map<Long, PumpResult> runTick() {
        tick();
        List<Pump> due = new ArrayList<>();
        Set<Pump> clicked = Collections.newSetFromMap(new IdentityHashMap<Pump, Boolean>());
        for (Pump pump : new ArrayList<>(pumps.values())) {
            FluidSource tile = pump.getTileSource();
            if (tile instanceof LiquidTileSource && ((LiquidTileSource) tile).getJudgment() == null) {
                ((LiquidTileSource) tile).judgeArea();
            }
            if (pump.advanceTimers() == null) {
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
     * The compatibility mode keeps connection order (the old engine: the pump that runs first).
     */
    List<Pump> orderForCaps(List<Pump> due) {
        List<Pump> byConnection = new ArrayList<>(due);
        byConnection.sort(Comparator.comparingLong(this::connectionOf));
        if (compatMode || byConnection.size() < 2) {
            return byConnection;
        }
        List<Map<Object, Integer>> footprints = new ArrayList<>();
        for (Pump pump : byConnection) {
            footprints.add(footprint(pump));
        }
        int n = byConnection.size();
        List<List<Integer>> after = new ArrayList<>();
        int[] before = new int[n];
        for (int i = 0; i < n; i++) {
            after.add(new ArrayList<Integer>());
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Map<Object, Integer> a = footprints.get(i);
                Map<Object, Integer> b = footprints.get(j);
                int bestSum = Integer.MAX_VALUE;
                int da = 0;
                int db = 0;
                for (Map.Entry<Object, Integer> entry : a.entrySet()) {
                    Integer other = b.get(entry.getKey());
                    if (other != null && entry.getValue() + other < bestSum) {
                        bestSum = entry.getValue() + other;
                        da = entry.getValue();
                        db = other;
                    }
                }
                if (bestSum == Integer.MAX_VALUE) {
                    continue;
                }
                // i is connected first: it goes first unless j is nearer to the shared pipe.
                if (db < da) {
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
        return result;
    }

    /** The cells a pump's paths would cross now, with the fewest steps to each (N26-4). */
    private Map<Object, Integer> footprint(Pump pump) {
        Map<Object, Integer> result = new HashMap<>();
        FluidType fluid = pump.cycleFluid();
        if (fluid == null) {
            return result;
        }
        for (Route route : routesFor(pump, fluid)) {
            for (int i = 0; i < route.path.length; i++) {
                Object id = route.element(i);
                Integer steps = result.get(id);
                int here = route.stepsTo[i];
                if (steps == null || here < steps) {
                    result.put(id, here);
                }
            }
        }
        return result;
    }

    private long connectionOf(Pump pump) {
        Long index = connectionIndex.get(pump);
        return index == null ? Long.MAX_VALUE : index;
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
        for (Direction d : Direction.values()) {
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

    /** Valves the pipe is linked to (2-3, 9-9, 13-4, 13-5). */
    public List<TankValve> getLinkedValves(PipeNode node) {
        List<TankValve> result = new ArrayList<>();
        if (node.getLayer() == PipeLayer.BASE) {
            for (Direction d : Direction.values()) {
                TankValve valve = valves.get(key(node.getTileX() + d.dx, node.getTileY() + d.dy));
                if (valve != null && node.isSideOpen(d) && valve.isSideOpen(d.opposite())) {
                    result.add(valve);
                }
            }
        } else {
            TankValve valve = valves.get(key(node.getTileX(), node.getTileY()));
            if (valve != null && node.isVerticalOpen() && valve.isVerticalOpen()) {
                result.add(valve);
            }
        }
        return result;
    }

    /** The basic pipes a pump pushes into (9-3, 9-9, 13-4), in {@link Direction} order. */
    public List<PipeNode> getPumpEntries(Pump pump) {
        List<PipeNode> result = new ArrayList<>();
        for (Direction d : Direction.values()) {
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
     * The valves a pump pulls through: linked to its sides (11-1 ②, N16-3), in pull order, and not
     * switched off by a wire signal (N27-4).
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

    /** Whether a pump and the valve on its side {@code direction} are linked (N16-3). */
    @Override
    public boolean isPumpValveLinked(Pump pump, Direction direction) {
        TankValve valve = valves.get(key(pump.getTileX() + direction.dx, pump.getTileY() + direction.dy));
        return valve != null && pump.isSideOpen(direction) && valve.isSideOpen(direction.opposite());
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
        pipes(layer).put(key(x, y), node);
        onStructureChanged(x, y);
        return node;
    }

    /** {@link #loadPipe(int, int, PipeLayer, MineralTier, int, FluidType, int, boolean, long[], byte[])} without hints. */
    public PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                             boolean loaded) {
        return loadPipe(x, y, layer, tier, links, fluid, amount, loaded, null, null);
    }

    /**
     * Adds a pipe with saved state, its region being loaded, or updates the pipe there with it (a
     * pipe of the same tier keeps its object, its network is regrouped). Saved hints are kept
     * (N25-6); where they disagree with the pipes around, the destinations concerned are searched
     * again when used. {@code loaded} false (the old engine's mirror of an unloaded region) adds
     * nothing: this engine holds no mirror (N23-2); it unloads the pipe there, if any.
     *
     * @return the pipe, or {@code null} when {@code loaded} is false
     */
    public PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                             boolean loaded, long[] hintDests, byte[] hintCodes) {
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
            }
            if (existing.getLinks() == LinkFlags.sanitize(links) && existing.getFluid() == contents
                    && existing.getAmount() == contentsAmount) {
                return existing;
            }
            // The same pipe with other saved state: update it in place and regroup its network.
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
            markAffected(x, y, false);
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
        node.setHints(hintDests, hintCodes);
        unloadedTiles.remove(key);
        pipes(layer).put(key, node);
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
        PipeNode node = pipes(layer).remove(key(x, y));
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
        PipeNode node = pipes(layer).remove(key(x, y));
        if (node == null) {
            return null;
        }
        node.removed = true;
        unindexHints(node);
        PipeNetwork network = node.network;
        if (network != null) {
            network.nodes.remove(node);
            node.network = null;
            rebuild(Collections.singletonList(network));
        }
        onStructureChanged(x, y);
        // The removed pipe's own hints mark their destinations too.
        markDestinations(node, true);
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
     * to it: those start cut, so the pumps keep their sources (N13-3, N16-3).
     */
    public void placeValve(int x, int y, TankValve valve) {
        addValve(x, y, valve);
        boolean changed = false;
        for (Direction d : Direction.values()) {
            if (pumps.containsKey(key(x + d.dx, y + d.dy)) && valve.isSideOpen(d)) {
                valve.setSideOpen(d, false);
                changed = true;
            }
        }
        onStructureChanged(x, y);
        if (changed) {
            listener.onLinksChanged(x, y, Part.VALVE);
        }
    }

    /**
     * Adds a valve with its saved link flags (its region was loaded). Its saved hints are in its
     * pipes; it is searched only when none of its linked pipes holds a hint for it (N25-1).
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
    }

    /** The valve was removed: pumps next to it lose it as a source, and it is no destination any more. */
    public TankValve removeValve(int x, int y) {
        TankValve valve = valves.remove(key(x, y));
        if (valve != null) {
            valvePositions.remove(valve);
            for (Direction d : Direction.values()) {
                Pump pump = pumps.get(key(x + d.dx, y + d.dy));
                if (pump != null) {
                    pump.removeSourceSlot(Pump.SourceSlot.valve(d.opposite()));
                }
            }
            long dest = key(x, y);
            onStructureChanged(x, y);
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
            valvePositions.remove(valve);
            if (loadedLookup == null) {
                unloadedTiles.add(key(x, y));
            }
        }
        return valve;
    }

    // ---------------------------------------------------------------- pumps

    /**
     * Placement check of a pump (N17-1): the base layer must be free, and the sources it would
     * connect (the liquid tile under it and the valves next to it whose side toward it is not cut)
     * must hold one fluid or be empty.
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
        for (Direction d : Direction.values()) {
            TankValve valve = valves.get(key(x + d.dx, y + d.dy));
            if (valve != null && valve.isSideOpen(d.opposite())) {
                fluids.add(valve.getStoredFluid());
            }
        }
        return Pump.canConnectSources(fluids) ? Check.OK : Check.DIFFERENT_SOURCE_FLUID;
    }

    /**
     * Places a new pump. It connects its sources in this order: the liquid tile under it (when the
     * game set a {@link Pump#setTileSource tile source}), then the valves next to it whose links are
     * open, north, east, south, west (a technical order for sources connected at the same moment).
     * It starts a network of its own (N18-2).
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
        for (Direction d : Direction.values()) {
            TankValve valve = valves.get(key(x + d.dx, y + d.dy));
            if (valve != null && pump.isSideOpen(d) && valve.isSideOpen(d.opposite())) {
                slots.add(Pump.SourceSlot.valve(d));
            }
        }
        pump.setSourceSlots(slots);
        // A new pump: an old summary at its tile belonged to another pump.
        dropSummariesOf(key(x, y));
        addPump(x, y, pump);
    }

    /**
     * Adds a pump with its saved state (its region was loaded). Saved valve sources behind its own
     * cut sides are dropped; a slot whose valve cut the link is skipped while it is cut
     * ({@link Pump#getSources}), and linking it again makes it the last source (N19-1).
     */
    public void loadPump(int x, int y, Pump pump) {
        Objects.requireNonNull(pump, "pump");
        pump.dropCutSourceSlots();
        addPump(x, y, pump);
    }

    private void addPump(int x, int y, Pump pump) {
        long key = key(x, y);
        if (isBaseLayerTaken(key) || pump.host != null) {
            throw new IllegalStateException("Cannot place pump at " + x + "," + y);
        }
        unloadedTiles.remove(key);
        pumps.put(key, pump);
        connectionIndex.put(pump, nextConnection++);
        pump.place(this, x, y);
        PipeNetwork own = new PipeNetwork();
        networks.add(own);
        own.addPump(pump);
        // A loaded pump that pushed before joins the network its fluid reached (N18-2).
        rebuild(Collections.singletonList(own));
    }

    /**
     * Removes a pump (picked up, or its region unloaded). Its summary stays, kept by its tile, for
     * when its region loads again (N23-2).
     */
    public Pump removePump(int x, int y) {
        Pump pump = pumps.remove(key(x, y));
        if (pump != null) {
            pump.host = null;
            connectionIndex.remove(pump);
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
        Pump pump = part == Part.PUMP ? pumps.get(key(x, y)) : other != null && other.part == Part.PUMP ? pumps.get(key(nx, ny)) : null;
        boolean pumpValve = other != null && (part == Part.PUMP && other.part == Part.VALVE
                || part == Part.VALVE && other.part == Part.PUMP);
        Direction pumpSide = part == Part.PUMP ? direction : direction.opposite();
        if (other == null) {
            own.set(!own.isOpen());
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
        listener.onLinksChanged(x, y, own.part);
        if (other != null) {
            listener.onLinksChanged(nx, ny, other.part);
        }
        return Check.OK;
    }

    /**
     * Wrench right-click on the middle of a tile (12-8, 13-5, N16-4): toggles the vertical link
     * between the basic pipe or valve there and the underground pipe there. With only one of them,
     * only its own flag flips. Nothing changes on a tile that is not loaded ({@link Check#NOT_LOADED}).
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
                : valve != null ? new End(Part.VALVE, () -> valve.isVerticalOpen(), valve::setVerticalOpen) : null;
        End bottom = under == null ? null : new End(Part.UNDERGROUND_PIPE, () -> under.isVerticalOpen(), under::setVerticalOpen);
        if (top == null && bottom == null) {
            return Check.NOTHING_THERE;
        }
        if (top == null || bottom == null) {
            End only = top != null ? top : bottom;
            only.set(!only.isOpen());
        } else {
            boolean open = !(top.isOpen() && bottom.isOpen());
            top.set(open);
            bottom.set(open);
        }
        afterLinkChange(x, y, x, y);
        if (top != null) {
            listener.onLinksChanged(x, y, top.part);
        }
        if (bottom != null) {
            listener.onLinksChanged(x, y, bottom.part);
        }
        return Check.OK;
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
                TankValve valve = valves.get(key);
                return valve == null ? null : new End(part, () -> valve.isSideOpen(d), open -> valve.setSideOpen(d, open));
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
        Set<PipeNetwork> affected = new LinkedHashSet<>();
        collectNetworks(x1, y1, affected);
        collectNetworks(x2, y2, affected);
        if (!affected.isEmpty()) {
            rebuild(affected);
        }
        onStructureChanged(x1, y1);
        if (x1 != x2 || y1 != y2) {
            onStructureChanged(x2, y2);
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
                for (Direction d : Direction.values()) {
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

    /** A structure change at the tile (N22-4): marks the destinations it affects (N25-4) and drops their summaries. */
    private void onStructureChanged(int x, int y) {
        markAffected(x, y, true);
    }

    /**
     * Marks the destinations whose hints are at the tile (both layers) or next to it, and the
     * valves there, for a new search when used (N25-4). A structure change also drops the summaries
     * of the paths to them: the network is no longer the one last seen running normally (N23-2).
     */
    private void markAffected(int x, int y, boolean structure) {
        Set<Long> dests = new LinkedHashSet<>();
        collectAt(x, y, dests);
        for (Direction d : Direction.values()) {
            collectAt(x + d.dx, y + d.dy, dests);
        }
        staleDestinations.addAll(dests);
        if (structure) {
            for (long dest : dests) {
                dropSummariesTo(dest);
            }
        }
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
        if (valves.containsKey(key)) {
            out.add(key);
        }
    }

    private void markDestinations(PipeNode node, boolean structure) {
        for (int i = 0; i < node.getHintCount(); i++) {
            long dest = node.getHintDestination(i);
            staleDestinations.add(dest);
            if (structure) {
                dropSummariesTo(dest);
            }
        }
    }

    /**
     * A pipe was loaded with its saved hints (N25-6). Hints saved at different times can disagree:
     * a destination a linked pipe next to it has a hint for and it has none, or its hint pointing at
     * a linked pipe that has none, is searched again when used.
     */
    private void onCellLoaded(PipeNode node) {
        for (PipeNode next : linkedPipes(node)) {
            int code = codeToward(node, next);
            for (int i = 0; i < next.getHintCount(); i++) {
                long dest = next.getHintDestination(i);
                if (node.getHint(dest) < 0) {
                    staleDestinations.add(dest);
                }
            }
            for (int i = 0; i < node.getHintCount(); i++) {
                long dest = node.getHintDestination(i);
                if (node.getHintCode(i) == code && next.getHint(dest) < 0) {
                    staleDestinations.add(dest);
                }
            }
        }
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
        if (node.setHint(dest, code)) {
            cellsOf(dest).add(node);
        }
    }

    /** Drops every loaded pipe's hint toward a destination that is gone. */
    private void dropHints(long dest) {
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
        for (Direction d : Direction.values()) {
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
        searchedAt.put(dest, tick);
        TankValve valve = valves.get(dest);
        if (valve == null) {
            return;
        }
        int vx = keyX(dest);
        int vy = keyY(dest);
        Map<PipeNode, FluidType> seen = new IdentityHashMap<>();
        ArrayDeque<PipeNode> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            PipeNode node = basePipes.get(key(vx + d.dx, vy + d.dy));
            if (node != null && valve.isSideOpen(d) && node.isSideOpen(d.opposite()) && !seen.containsKey(node)) {
                seen.put(node, node.getFluid());
                setHint(node, dest, d.opposite().ordinal());
                queue.add(node);
            }
        }
        PipeNode under = undergroundPipes.get(dest);
        if (under != null && valve.isVerticalOpen() && under.isVerticalOpen() && !seen.containsKey(under)) {
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
        for (Direction d : Direction.values()) {
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

        Route(TankValve valve, long dest, Object[] path, int[] faces, int[] stepsTo, int distance) {
            this.valve = valve;
            this.dest = dest;
            this.valveX = keyX(dest);
            this.valveY = keyY(dest);
            this.path = path;
            this.faces = faces;
            this.stepsTo = stepsTo;
            this.distance = distance;
        }

        /** The identity of element {@code i} for grouping at junctions, or of the valve past the end. */
        Object element(int i) {
            if (i < path.length) {
                return path[i] instanceof SummaryRun ? ((SummaryRun) path[i]).key() : path[i];
            }
            return valve;
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
        Map<Long, Route> best = new LinkedHashMap<>();
        Set<Long> dests = new LinkedHashSet<>();
        List<PipeNode> outputs = new ArrayList<>();
        List<Direction> sides = new ArrayList<>();
        for (Direction d : Direction.values()) {
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
            if (valve == null) {
                // Its region is not loaded: no destination now.
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
        List<Object> path = new ArrayList<>();
        List<Integer> faces = new ArrayList<>();
        List<Integer> steps = new ArrayList<>();
        int limit = basePipes.size() + undergroundPipes.size() + 1;
        PipeNode cell = output;
        path.add(cell);
        faces.add(side.ordinal());
        int count = 1;
        steps.add(count);
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
                    if (!(cell.isVerticalOpen() && valve.isVerticalOpen())) {
                        return new Walk(WALK_REPAIR);
                    }
                    faces.add(PipeNode.HINT_VERTICAL);
                    break;
                }
            } else {
                Direction d = Direction.values()[code];
                if (cell.getLayer() == PipeLayer.BASE && key(x + d.dx, y + d.dy) == dest) {
                    if (!(cell.isSideOpen(d) && valve.isSideOpen(d.opposite()))) {
                        return new Walk(WALK_REPAIR);
                    }
                    faces.add(code);
                    break;
                }
            }
            PipeLayer layer = code == PipeNode.HINT_VERTICAL ? cell.getLayer().other() : cell.getLayer();
            int nx = code == PipeNode.HINT_VERTICAL ? x : x + Direction.values()[code].dx;
            int ny = code == PipeNode.HINT_VERTICAL ? y : y + Direction.values()[code].dy;
            PipeNode next = getPipe(nx, ny, layer);
            if (next == null) {
                if (isTileLoaded(nx, ny)) {
                    return new Walk(WALK_REPAIR);
                }
                // An unloaded stretch: only the summary of the last normal cycle can skip it (N23-2).
                if (code != PipeNode.HINT_VERTICAL && !cell.isSideOpen(Direction.values()[code])) {
                    return new Walk(WALK_REPAIR);
                }
                if (summary == null) {
                    summary = summaryOf(pump, dest);
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
                    path.add(skipped);
                    faces.add(last == null ? code : faceBetween(last.lastX, last.lastY, skipped.firstX, skipped.firstY));
                    count += skipped.count;
                    steps.add(count);
                    last = skipped;
                    run++;
                }
                if (run >= summary.runs.length || last == null) {
                    return new Walk(WALK_DEAD_END);
                }
                SummaryRun resume = summary.runs[run];
                next = getPipe(resume.firstX, resume.firstY, resume.firstLayer);
                if (next == null || !traversable(next, fluid)) {
                    return new Walk(WALK_DEAD_END);
                }
                faces.add(last.lastLayer != next.getLayer() && last.lastX == next.getTileX() && last.lastY == next.getTileY()
                        ? PipeNode.HINT_VERTICAL : faceBetween(last.lastX, last.lastY, next.getTileX(), next.getTileY()));
            } else {
                if (!linked(cell, next, code)) {
                    return new Walk(WALK_REPAIR);
                }
                if (!traversable(next, fluid)) {
                    return new Walk(WALK_DEAD_END);
                }
                faces.add(code);
            }
            path.add(next);
            count++;
            steps.add(count);
            cell = next;
            if (path.size() > limit) {
                return new Walk(WALK_REPAIR);
            }
        }
        int[] faceArray = new int[faces.size()];
        for (int i = 0; i < faceArray.length; i++) {
            faceArray[i] = faces.get(i);
        }
        int[] stepArray = new int[steps.size()];
        for (int i = 0; i < stepArray.length; i++) {
            stepArray[i] = steps.get(i);
        }
        Walk result = new Walk(WALK_OK);
        result.route = new Route(valve, dest, path.toArray(), faceArray, stepArray, count);
        return result;
    }

    private static boolean linked(PipeNode from, PipeNode to, int code) {
        if (code == PipeNode.HINT_VERTICAL) {
            return from.isVerticalOpen() && to.isVerticalOpen();
        }
        Direction d = Direction.values()[code];
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
         * The lowest transport amount of its cells (the cap, N14-2).
         * TODO(confirm): with the lowest tier and {@link #full}, derived data the summary needs to keep
         * the cap (N14-2), the tier judgment (N12-4) and "only full stretches are skipped" (N14-3)
         * across unloaded regions.
         */
        public final int capacity;
        /** The lowest tier of its cells (N12-4). */
        public final MineralTier lowestTier;
        /** Whether every cell was full of {@link #fluid} (only a full stretch may be skipped, N14-3, N15-1). */
        public final boolean full;
        public final FluidType fluid;

        public SummaryRun(long region, int firstX, int firstY, PipeLayer firstLayer, int lastX, int lastY,
                          PipeLayer lastLayer, int count, int capacity, MineralTier lowestTier, boolean full,
                          FluidType fluid) {
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
     * One pump's path to one destination in its last normal cycle (N23-2): the source (the pump's
     * tile), the last output position (the valve's tile) and the regions passed through, as runs in
     * path order.
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
     * Records a pump's path after a push (N23-2): runs of loaded cells as they are now, skipped
     * runs as they were (a region that loads while the network runs is updated while it is loaded).
     */
    private void recordSummary(Pump pump, Route route, FluidType fluid) {
        List<SummaryRun> runs = new ArrayList<>();
        PipeNode first = null;
        PipeNode last = null;
        long region = 0;
        int count = 0;
        int capacity = 0;
        MineralTier lowest = null;
        boolean full = true;
        for (Object element : route.path) {
            if (element instanceof SummaryRun) {
                if (first != null) {
                    runs.add(new SummaryRun(region, first.getTileX(), first.getTileY(), first.getLayer(), last.getTileX(),
                            last.getTileY(), last.getLayer(), count, capacity, lowest, full, fluid));
                    first = null;
                }
                runs.add((SummaryRun) element);
                continue;
            }
            PipeNode node = (PipeNode) element;
            long here = TileBuckets.bucketOf(node.getTileX(), node.getTileY());
            if (first != null && here != region) {
                runs.add(new SummaryRun(region, first.getTileX(), first.getTileY(), first.getLayer(), last.getTileX(),
                        last.getTileY(), last.getLayer(), count, capacity, lowest, full, fluid));
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
            runs.add(new SummaryRun(region, first.getTileX(), first.getTileY(), first.getLayer(), last.getTileX(),
                    last.getTileY(), last.getLayer(), count, capacity, lowest, full, fluid));
        }
        putSummary(key(pump.getTileX(), pump.getTileY()), route.dest,
                new RouteSummary(pump.getTileX(), pump.getTileY(), route.valveX, route.valveY,
                        runs.toArray(new SummaryRun[0])));
    }

    // ---------------------------------------------------------------- pushing

    /**
     * The destinations of one push from {@code pump} with {@code fluid}: valves reached along the
     * hints through pipes that can carry it, excluding the tanks the pump is linked to for pulling.
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
        return new GridPushPlan(pump, fluid, destinations);
    }

    /** One push: the destinations and the logic that moves fluid to them, as a dry run or for real. */
    final class GridPushPlan implements PushPlan {
        private final Pump pump;
        private final FluidType fluid;
        private final List<Route> destinations;

        GridPushPlan(Pump pump, FluidType fluid, List<Route> destinations) {
            this.pump = pump;
            this.fluid = fluid;
            this.destinations = destinations;
        }

        @Override
        public long simulate(int amount) {
            Stats stats = distribute(amount, new DryLedger());
            return (long) stats.pipeFill + stats.deliveredTotal + stats.lost;
        }

        @Override
        public PumpResult run(int amount) {
            Stats stats = distribute(amount, new RealLedger());
            for (Route route : destinations) {
                if (valves.get(route.dest) != route.valve) {
                    continue;
                }
                boolean intact = true;
                for (Object element : route.path) {
                    if (element instanceof PipeNode && ((PipeNode) element).removed) {
                        intact = false;
                        break;
                    }
                }
                if (intact) {
                    recordSummary(pump, route, fluid);
                }
            }
            return new PumpResult(PumpResult.Status.PUMPED, fluid, stats.pipeFill + stats.deliveredTotal, stats.pipeFill,
                    stats.updated.size(), stats.lost, stats.delivered, stats.broken);
        }

        /**
         * Shares over the destinations that have room, delivered nearest first; what one cannot take
         * is split again among the others (N7-2, N20-6).
         */
        private Stats distribute(int amount, Ledger ledger) {
            Stats stats = new Stats();
            List<Route> active = new ArrayList<>();
            for (Route route : destinations) {
                if (ledger.tankSpace(route.valve) > 0) {
                    active.add(route);
                }
            }
            int remaining = amount;
            while (remaining > 0 && !active.isEmpty()) {
                Map<Route, Integer> shares = compatMode ? nearestFirst(remaining, active) : splitAtJunctions(remaining, active);
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
                    if (saturated.contains(route) || ledger.tankSpace(route.valve) == 0) {
                        active.remove(i);
                    }
                }
                if (used == 0) {
                    break;
                }
            }
            return stats;
        }

        /** The old rule (compatibility mode, N7-2): equal shares, the remainder one unit each to the nearest. */
        private Map<Route, Integer> nearestFirst(int amount, List<Route> active) {
            Map<Route, Integer> shares = new IdentityHashMap<>();
            int count = active.size();
            int base = amount / count;
            int extra = amount % count;
            for (int i = 0; i < count; i++) {
                shares.put(active.get(i), base + (i < extra ? 1 : 0));
            }
            return shares;
        }

        /**
         * N24-3: the amount is split where the paths part, by the number of destinations behind each
         * outgoing face; the remainder of each split goes one unit per destination, faces taken
         * north, east, south, west (N25-5, N27-1), then the other layer (TODO(confirm)).
         */
        private Map<Route, Integer> splitAtJunctions(int amount, List<Route> active) {
            Map<Route, Integer> shares = new IdentityHashMap<>();
            split(amount, new ArrayList<>(active), 0, shares);
            return shares;
        }

        private void split(int amount, List<Route> group, int depth, Map<Route, Integer> out) {
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
            // Group the destinations by the element they step to: one group per outgoing face.
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
            faces.sort(Comparator.comparingInt(faceOf::get));
            int count = group.size();
            int base = amount / count;
            int extra = amount % count;
            for (Object face : faces) {
                List<Route> members = byNext.get(face);
                int bonus = Math.min(extra, members.size());
                extra -= bonus;
                split(base * members.size() + bonus, members, k + 1, out);
            }
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
                        if (!compatMode && !mayReach(route, frontier, node, ledger)) {
                            // The fluid front moves one block per step at the pipe's speed (N25-1~N25-3).
                            break;
                        }
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

        /** Whether the empty pipe at {@code index} may be reached now (N25-1~N25-3). */
        private boolean mayReach(Route route, int index, PipeNode node, Ledger ledger) {
            if (index == 0 || !(route.path[index - 1] instanceof PipeNode)) {
                return true;
            }
            long since = ledger.reachedTick((PipeNode) route.path[index - 1]);
            return tick - since >= tierRules.getFillTicksPerBlock(node.getTier());
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

            abstract long reachedTick(PipeNode node);

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
            long reachedTick(PipeNode node) {
                return node.reachedTick;
            }

            @Override
            void fill(PipeNode node, int amount, Stats stats) {
                boolean reached = node.isReached();
                node.insert(fluid, amount);
                stats.updated.add(node);
                if (!reached && node.isReached()) {
                    node.reachedTick = tick;
                    attachReached(node);
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
            private final Map<PipeNode, Integer> added = new IdentityHashMap<>();
            private final Map<PipeNode, Integer> flow = new IdentityHashMap<>();
            private final Map<RunKey, Integer> runs = new HashMap<>();
            private final Map<TankStorage, Integer> tanks = new IdentityHashMap<>();
            private final Set<PipeNode> broken = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
            private final Map<Route, Integer> frontiers = new IdentityHashMap<>();

            @Override
            int amount(PipeNode node) {
                Integer extra = added.get(node);
                return node.getAmount() + (extra == null ? 0 : extra);
            }

            @Override
            FluidType fluid(PipeNode node) {
                return node.getFluid() != null ? node.getFluid() : added.containsKey(node) ? fluid : null;
            }

            @Override
            boolean removed(PipeNode node) {
                return node.removed || broken.contains(node);
            }

            @Override
            int flowLeft(PipeNode node) {
                Integer used = flow.get(node);
                return Math.max(0, node.flowLeft(window) - (used == null ? 0 : used));
            }

            @Override
            void useFlow(PipeNode node, int amount) {
                flow.merge(node, amount, Integer::sum);
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
            long reachedTick(PipeNode node) {
                return node.getAmount() == 0 && added.containsKey(node) ? tick : node.reachedTick;
            }

            @Override
            void fill(PipeNode node, int amount, Stats stats) {
                added.merge(node, amount, Integer::sum);
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
        for (Direction d : Direction.values()) {
            PipeNode neighbour = getPipe(node.getTileX() + d.dx, node.getTileY() + d.dy, node.getLayer());
            if (neighbour != null && node.isSideOpen(d) && neighbour.isSideOpen(d.opposite())) {
                result.add(neighbour);
            }
        }
        PipeNode other = getPipe(node.getTileX(), node.getTileY(), node.getLayer().other());
        if (other != null && node.isVerticalOpen() && other.isVerticalOpen()) {
            result.add(other);
        }
        return result;
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
