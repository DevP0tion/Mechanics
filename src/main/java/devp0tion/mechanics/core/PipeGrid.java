package devp0tion.mechanics.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The pipe network manager of one level: every pipe, tank valve and pump, the networks the pipes
 * form, and the push logic that moves fluid (N7-1).
 *
 * <h2>Links</h2>
 * <ul>
 *     <li>Pipes of the same layer on adjacent tiles link automatically (9-4) unless the wrench cut
 *     the link ({@link #toggleSide}, 12-8).</li>
 *     <li>The basic and the underground pipe on the same tile link (9-5) unless cut with
 *     {@link #toggleVertical} (12-8).</li>
 *     <li>A basic pipe delivers into tank valves on adjacent tiles (2-3); an underground pipe only
 *     into the valve on its own tile (9-9). Both links can be cut (13-4, 13-5).</li>
 *     <li>A pump pushes only into adjacent basic pipes, never underground pipes (9-3, 9-9); the
 *     pipe's side toward the pump can be cut (13-4). A valve next to a pump is the pump's source
 *     (11-1 ②, 11-5), never a destination.</li>
 * </ul>
 *
 * <h2>Networks</h2>
 * A network is a group of linked pipes ({@link PipeNetwork}): one fluid (12-7), the lowest tier's
 * conditions (9-10), and a cell capacity of that tier's transport amount (N7-3, from
 * {@link PipeTierRules}). Fluid is stored per pipe, so when a pipe is removed and its network
 * splits, every remaining pipe simply keeps its own amount: the pieces together hold exactly what
 * the old network held minus the removed pipe, whose contents are returned to the caller
 * ({@link #removePipe}). Nothing is created or destroyed. A placement or wrench link that would
 * join two networks holding different fluids is refused ({@link Check#WOULD_MIX_FLUIDS}).
 * When a lower-tier pipe joins, pipes holding more than the new cell capacity keep the excess and
 * take nothing more.
 *
 * <p>TODO(design): three cases the decisions do not cover: (1) what happens to the fluid of a
 * removed pipe (returned to the caller here); (2) refusing a link between networks of different
 * fluids (12-7 only says one fluid per network); (3) the excess in pipes when a lower tier joins.
 *
 * <h2>Pushing (N7-1, N7-2)</h2>
 * Each pump has destinations: the enabled valves its networks reach that have room for the fluid,
 * with the shortest pipe path to each (pipe cells counted, the pump's first pipe = 1). The pushed
 * amount is split equally between them; what does not divide evenly goes one unit each to the
 * nearest destinations (equal distance: smaller tile y, then x). Each share first fills the
 * not-yet-full pipes along its path, then enters the tank; a share a full tank cannot take is
 * split again among the others. Pipes that are already full are never written again: each path
 * keeps a frontier index, so only the last, partly filled pipe is updated, and once a path is full
 * a push writes no pipe at all. Paths are cached per pump and rebuilt only when pipes, valves,
 * pumps or links change.
 *
 * <p>TODO(game): region loading (N7-1, D5) is not handled yet. The intended integration: the game
 * keeps one grid per level and mirrors its pipe, valve and pump objects into it as regions load
 * and unload (the objects save their own contents and flags), so pumps only push through loaded
 * pipes.
 *
 * <p>TODO(design): the pipe tier conditions other than the transport amount (temperature, fluid
 * kinds, state, 9-2) are undecided and not checked.
 */
public final class PipeGrid {

    /** Result of a placement or wrench check. */
    public enum Check {
        OK,
        /** The layer is already taken on that tile (one object per layer, D1). */
        OCCUPIED,
        /** No pipe there to act on. */
        NOTHING_THERE,
        /** The link would join two networks holding different fluids (12-7). */
        WOULD_MIX_FLUIDS
    }

    private final PipeTierRules tierRules;
    private final Map<Long, PipeNode> basePipes = new HashMap<>();
    private final Map<Long, PipeNode> undergroundPipes = new HashMap<>();
    private final Map<Long, TankValve> valves = new HashMap<>();
    private final Map<Long, Pump> pumps = new HashMap<>();
    private final Map<TankValve, Long> valvePositions = new IdentityHashMap<>();
    private final Set<PipeNetwork> networks = new LinkedHashSet<>();
    private final Map<Pump, PumpRoutes> routeCache = new IdentityHashMap<>();
    private int topologyVersion;

    public PipeGrid(PipeTierRules tierRules) {
        this.tierRules = Objects.requireNonNull(tierRules, "tierRules");
    }

    // ---------------------------------------------------------------- queries

    public PipeNode getPipe(int x, int y, PipeLayer layer) {
        return pipes(layer).get(key(x, y));
    }

    public TankValve getValve(int x, int y) {
        return valves.get(key(x, y));
    }

    public Pump getPump(int x, int y) {
        return pumps.get(key(x, y));
    }

    /** The network of the pipe at the tile, or {@code null} if there is no pipe. */
    public PipeNetwork getNetwork(int x, int y, PipeLayer layer) {
        PipeNode node = getPipe(x, y, layer);
        return node == null ? null : node.network;
    }

    public List<PipeNetwork> getNetworks() {
        return Collections.unmodifiableList(new ArrayList<>(networks));
    }

    /** Whether two pipes are linked (both facing flags open, 9-4, 9-5, 12-8). */
    public boolean areLinked(PipeNode a, PipeNode b) {
        return linkedPipes(a).contains(b);
    }

    /** Valves the pipe delivers into (2-3, 9-9, 13-4, 13-5). */
    public List<TankValve> getLinkedValves(PipeNode node) {
        List<TankValve> result = new ArrayList<>();
        if (node.getLayer() == PipeLayer.BASE) {
            for (Direction d : Direction.values()) {
                TankValve valve = valves.get(key(node.getTileX() + d.dx, node.getTileY() + d.dy));
                if (valve != null && node.isSideOpen(d)) {
                    result.add(valve);
                }
            }
        } else {
            TankValve valve = valves.get(key(node.getTileX(), node.getTileY()));
            if (valve != null && node.isVerticalOpen()) {
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
            if (node != null && node.isSideOpen(d.opposite())) {
                result.add(node);
            }
        }
        return result;
    }

    /** Valves directly attached to a pump: the tanks it can pull from (11-1 ②, 11-5). */
    public List<TankValve> getAttachedValves(Pump pump) {
        List<TankValve> result = new ArrayList<>();
        for (Direction d : Direction.values()) {
            TankValve valve = valves.get(key(pump.getTileX() + d.dx, pump.getTileY() + d.dy));
            if (valve != null) {
                result.add(valve);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- pipes

    /** Whether a pipe can be placed (all its links start open, 9-4, 9-5). */
    public Check checkPipePlacement(int x, int y, PipeLayer layer) {
        long key = key(x, y);
        if (layer == PipeLayer.BASE ? isBaseLayerTaken(key) : undergroundPipes.containsKey(key)) {
            return Check.OCCUPIED;
        }
        List<PipeNetwork> joined = new ArrayList<>();
        for (Direction d : Direction.values()) {
            PipeNode neighbour = getPipe(x + d.dx, y + d.dy, layer);
            if (neighbour != null && neighbour.isSideOpen(d.opposite())) {
                joined.add(neighbour.network);
            }
        }
        PipeNode other = getPipe(x, y, layer.other());
        if (other != null && other.isVerticalOpen()) {
            joined.add(other.network);
        }
        return mixesFluids(joined) ? Check.WOULD_MIX_FLUIDS : Check.OK;
    }

    /**
     * Places a pipe with every link open (9-4, 9-5).
     *
     * @throws IllegalStateException if {@link #checkPipePlacement} is not {@link Check#OK}
     */
    public PipeNode placePipe(int x, int y, PipeLayer layer, MineralTier tier) {
        Objects.requireNonNull(tier, "tier");
        Check check = checkPipePlacement(x, y, layer);
        if (check != Check.OK) {
            throw new IllegalStateException("Cannot place " + layer + " pipe at " + x + "," + y + ": " + check);
        }
        PipeNode node = new PipeNode(x, y, layer, tier);
        pipes(layer).put(key(x, y), node);
        rebuildNetworks(Collections.singletonList(node), null);
        return node;
    }

    /**
     * Removes a pipe. The network splits as needed; every remaining pipe keeps its fluid.
     *
     * @return the removed pipe with the fluid it held (the caller decides what happens to it), or
     * {@code null} if there was none
     */
    public PipeNode removePipe(int x, int y, PipeLayer layer) {
        PipeNode node = getPipe(x, y, layer);
        if (node == null) {
            return null;
        }
        List<PipeNode> seeds = linkedPipes(node);
        PipeNetwork old = node.network;
        pipes(layer).remove(key(x, y));
        node.network = null;
        rebuildNetworks(seeds, old);
        return node;
    }

    // ---------------------------------------------------------------- wrench

    /**
     * Wrench right-click on a pipe side (12-8, 13-4): toggles the link toward that direction (a
     * pipe, pump or valve there). Between two pipes both facing flags are set to the new state.
     */
    public Check toggleSide(int x, int y, PipeLayer layer, Direction direction) {
        PipeNode node = getPipe(x, y, layer);
        if (node == null) {
            return Check.NOTHING_THERE;
        }
        PipeNode neighbour = getPipe(x + direction.dx, y + direction.dy, layer);
        boolean linked = node.isSideOpen(direction)
                && (neighbour == null || neighbour.isSideOpen(direction.opposite()));
        boolean open = !linked;
        if (open && neighbour != null && mixesFluids(Arrays.asList(node.network, neighbour.network))) {
            return Check.WOULD_MIX_FLUIDS;
        }
        node.setSideOpen(direction, open);
        List<PipeNode> seeds = new ArrayList<>();
        seeds.add(node);
        if (neighbour != null) {
            neighbour.setSideOpen(direction.opposite(), open);
            seeds.add(neighbour);
        }
        rebuildNetworks(seeds, null);
        return Check.OK;
    }

    /**
     * Wrench middle right-click on a tile (12-8, 13-5): toggles the vertical link between the
     * basic and the underground pipe there, or between the underground pipe and the valve there.
     */
    public Check toggleVertical(int x, int y) {
        PipeNode base = getPipe(x, y, PipeLayer.BASE);
        PipeNode under = getPipe(x, y, PipeLayer.UNDERGROUND);
        if (base == null && under == null) {
            return Check.NOTHING_THERE;
        }
        boolean linked = (base == null || base.isVerticalOpen()) && (under == null || under.isVerticalOpen());
        boolean open = !linked;
        if (open && base != null && under != null
                && mixesFluids(Arrays.asList(base.network, under.network))) {
            return Check.WOULD_MIX_FLUIDS;
        }
        List<PipeNode> seeds = new ArrayList<>();
        if (base != null) {
            base.setVerticalOpen(open);
            seeds.add(base);
        }
        if (under != null) {
            under.setVerticalOpen(open);
            seeds.add(under);
        }
        rebuildNetworks(seeds, null);
        return Check.OK;
    }

    // ---------------------------------------------------------------- valves and pumps

    /** Places a tank valve (base layer). */
    public void placeValve(int x, int y, TankValve valve) {
        Objects.requireNonNull(valve, "valve");
        long key = key(x, y);
        if (isBaseLayerTaken(key) || valvePositions.containsKey(valve)) {
            throw new IllegalStateException("Cannot place valve at " + x + "," + y);
        }
        valves.put(key, valve);
        valvePositions.put(valve, key);
        topologyChanged();
    }

    public TankValve removeValve(int x, int y) {
        TankValve valve = valves.remove(key(x, y));
        if (valve != null) {
            valvePositions.remove(valve);
            topologyChanged();
        }
        return valve;
    }

    /** Places a pump (base layer). */
    public void placePump(int x, int y, Pump pump) {
        Objects.requireNonNull(pump, "pump");
        long key = key(x, y);
        if (isBaseLayerTaken(key) || pump.grid != null) {
            throw new IllegalStateException("Cannot place pump at " + x + "," + y);
        }
        pumps.put(key, pump);
        pump.place(this, x, y);
        topologyChanged();
    }

    public Pump removePump(int x, int y) {
        Pump pump = pumps.remove(key(x, y));
        if (pump != null) {
            pump.grid = null;
            routeCache.remove(pump);
            topologyChanged();
        }
        return pump;
    }

    // ---------------------------------------------------------------- pushing

    /**
     * The destinations of one push from {@code pump} with {@code fluid}: valves with room, reached
     * through networks that can carry the fluid, excluding the tank the pump pulls from.
     */
    PushPlan planPush(Pump pump, FluidType fluid) {
        TankStorage sourceTank = pump.getSource() instanceof TankStorage ? (TankStorage) pump.getSource() : null;
        Map<TankValve, Route> best = new LinkedHashMap<>();
        for (Route route : routesFor(pump)) {
            if (!route.network.canCarry(fluid)) {
                continue;
            }
            if (sourceTank != null && route.valve.getTank() == sourceTank) {
                continue;
            }
            if (route.valve.getSpaceFor(fluid) == 0) {
                continue;
            }
            Route current = best.get(route.valve);
            if (current == null || route.nodes.length < current.nodes.length) {
                best.put(route.valve, route);
            }
        }
        List<Route> destinations = new ArrayList<>(best.values());
        Collections.sort(destinations, Route.ORDER);
        return new PushPlan(fluid, destinations);
    }

    /** One push: the destinations and the logic that moves fluid to them. */
    static final class PushPlan {
        private final FluidType fluid;
        private final List<Route> destinations;

        PushPlan(FluidType fluid, List<Route> destinations) {
            this.fluid = fluid;
            this.destinations = destinations;
        }

        /** Units the destinations can take now: their tanks' room plus the room left in their paths. */
        long getAcceptable() {
            long total = 0;
            Set<PipeNode> counted = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
            for (Route route : destinations) {
                total += route.valve.getSpaceFor(fluid);
                route.skipFullPipes(fluid);
                for (int i = route.frontier; i < route.nodes.length; i++) {
                    if (counted.add(route.nodes[i])) {
                        total += route.nodes[i].getSpaceFor(fluid);
                    }
                }
            }
            return total;
        }

        /**
         * Pushes {@code amount}: equal shares, the remainder to the nearest destinations, shares a
         * full tank cannot take split again among the rest (N7-2).
         */
        PumpResult push(int amount) {
            List<Route> active = new ArrayList<>();
            for (Route route : destinations) {
                if (route.valve.getSpaceFor(fluid) > 0) {
                    active.add(route);
                }
            }
            PushStats stats = new PushStats();
            int remaining = amount;
            while (remaining > 0 && !active.isEmpty()) {
                int count = active.size();
                int base = remaining / count;
                int extra = remaining % count;
                int moved = 0;
                for (int i = 0; i < count; i++) {
                    int share = base + (i < extra ? 1 : 0);
                    if (share > 0) {
                        moved += deliver(active.get(i), share, stats);
                    }
                }
                remaining -= moved;
                for (int i = active.size() - 1; i >= 0; i--) {
                    if (active.get(i).valve.getSpaceFor(fluid) == 0) {
                        active.remove(i);
                    }
                }
                if (moved == 0) {
                    break;
                }
            }
            return new PumpResult(PumpResult.Status.PUMPED, fluid, amount - remaining, stats.pipeFill,
                    stats.updated.size(), stats.delivered);
        }

        /** Moves one share along a path: the frontier pipes first, then the tank. */
        private int deliver(Route route, int share, PushStats stats) {
            int used = 0;
            while (share > 0 && route.frontier < route.nodes.length) {
                PipeNode node = route.nodes[route.frontier];
                int space = node.getSpaceFor(fluid);
                if (space > 0) {
                    int put = node.insert(fluid, Math.min(space, share));
                    stats.updated.add(node);
                    stats.pipeFill += put;
                    share -= put;
                    used += put;
                }
                if (node.getSpaceFor(fluid) == 0) {
                    route.frontier++;
                }
            }
            if (share > 0) {
                int taken = route.valve.insert(fluid, share);
                if (taken > 0) {
                    Integer before = stats.delivered.get(route.valve);
                    stats.delivered.put(route.valve, (before == null ? 0 : before) + taken);
                    used += taken;
                }
            }
            return used;
        }
    }

    private static final class PushStats {
        final Set<PipeNode> updated = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
        final Map<TankValve, Integer> delivered = new LinkedHashMap<>();
        int pipeFill;
    }

    /** The shortest path from a pump to one valve through one network. */
    static final class Route {
        static final Comparator<Route> ORDER = new Comparator<Route>() {
            @Override
            public int compare(Route a, Route b) {
                if (a.nodes.length != b.nodes.length) {
                    return Integer.compare(a.nodes.length, b.nodes.length);
                }
                if (a.valveY != b.valveY) {
                    return Integer.compare(a.valveY, b.valveY);
                }
                return Integer.compare(a.valveX, b.valveX);
            }
        };

        final TankValve valve;
        final int valveX;
        final int valveY;
        final PipeNetwork network;
        /** From the pipe next to the pump to the pipe linked to the valve; length = path distance. */
        final PipeNode[] nodes;
        /** Pipes before this index are known to be full: the frontier (N7-1). */
        int frontier;

        Route(TankValve valve, int valveX, int valveY, PipeNetwork network, PipeNode[] nodes) {
            this.valve = valve;
            this.valveX = valveX;
            this.valveY = valveY;
            this.network = network;
            this.nodes = nodes;
        }

        /** Moves the frontier past pipes that are already full (reads only, no writes). */
        void skipFullPipes(FluidType fluid) {
            while (frontier < nodes.length && nodes[frontier].getSpaceFor(fluid) == 0) {
                frontier++;
            }
        }
    }

    private static final class PumpRoutes {
        final int version;
        final List<Route> routes;

        PumpRoutes(int version, List<Route> routes) {
            this.version = version;
            this.routes = routes;
        }
    }

    /** Pipe path distance from the pump to a valve (for tests and the game UI), or -1. */
    public int getPathDistance(Pump pump, TankValve valve) {
        int best = -1;
        for (Route route : routesFor(pump)) {
            if (route.valve == valve && (best < 0 || route.nodes.length < best)) {
                best = route.nodes.length;
            }
        }
        return best;
    }

    /** Shortest routes from the pump to every reachable valve, per network (breadth-first). */
    private List<Route> routesFor(Pump pump) {
        PumpRoutes cached = routeCache.get(pump);
        if (cached != null && cached.version == topologyVersion) {
            return cached.routes;
        }
        Map<PipeNode, PipeNode> parent = new IdentityHashMap<>();
        ArrayDeque<PipeNode> queue = new ArrayDeque<>();
        for (PipeNode entry : getPumpEntries(pump)) {
            if (!parent.containsKey(entry)) {
                parent.put(entry, null);
                queue.add(entry);
            }
        }
        // Per valve and network, the first (closest) pipe reached that is linked to the valve.
        Map<TankValve, Map<PipeNetwork, PipeNode>> attach = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            PipeNode node = queue.poll();
            for (TankValve valve : getLinkedValves(node)) {
                Map<PipeNetwork, PipeNode> perNetwork = attach.get(valve);
                if (perNetwork == null) {
                    perNetwork = new LinkedHashMap<>();
                    attach.put(valve, perNetwork);
                }
                if (!perNetwork.containsKey(node.network)) {
                    perNetwork.put(node.network, node);
                }
            }
            for (PipeNode next : linkedPipes(node)) {
                if (!parent.containsKey(next)) {
                    parent.put(next, node);
                    queue.add(next);
                }
            }
        }
        List<Route> routes = new ArrayList<>();
        for (Map.Entry<TankValve, Map<PipeNetwork, PipeNode>> entry : attach.entrySet()) {
            long valveKey = valvePositions.get(entry.getKey());
            for (Map.Entry<PipeNetwork, PipeNode> perNetwork : entry.getValue().entrySet()) {
                List<PipeNode> path = new ArrayList<>();
                for (PipeNode n = perNetwork.getValue(); n != null; n = parent.get(n)) {
                    path.add(n);
                }
                Collections.reverse(path);
                routes.add(new Route(entry.getKey(), keyX(valveKey), keyY(valveKey), perNetwork.getKey(),
                        path.toArray(new PipeNode[0])));
            }
        }
        routeCache.put(pump, new PumpRoutes(topologyVersion, routes));
        return routes;
    }

    // ---------------------------------------------------------------- internals

    private Map<Long, PipeNode> pipes(PipeLayer layer) {
        return layer == PipeLayer.BASE ? basePipes : undergroundPipes;
    }

    private boolean isBaseLayerTaken(long key) {
        return basePipes.containsKey(key) || valves.containsKey(key) || pumps.containsKey(key);
    }

    /** Pipes linked to {@code node} (9-4, 9-5, 12-8). */
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

    /** True if the networks hold two or more different fluids (12-7). */
    private static boolean mixesFluids(Collection<PipeNetwork> joined) {
        FluidType seen = null;
        for (PipeNetwork network : joined) {
            FluidType fluid = network == null ? null : network.getFluid();
            if (fluid != null) {
                if (seen != null && seen != fluid) {
                    return true;
                }
                seen = fluid;
            }
        }
        return false;
    }

    /**
     * Rebuilds the networks around changed pipes: every network containing a seed (plus
     * {@code removedFrom}, the network of a removed pipe) is replaced by the linked groups its
     * remaining pipes now form.
     */
    private void rebuildNetworks(List<PipeNode> seeds, PipeNetwork removedFrom) {
        Set<PipeNetwork> old = new LinkedHashSet<>();
        if (removedFrom != null) {
            old.add(removedFrom);
        }
        for (PipeNode seed : seeds) {
            if (seed.network != null) {
                old.add(seed.network);
            }
        }
        List<PipeNode> starts = new ArrayList<>(seeds);
        for (PipeNetwork network : old) {
            networks.remove(network);
            for (PipeNode node : network.getNodes()) {
                if (getPipe(node.getTileX(), node.getTileY(), node.getLayer()) == node) {
                    starts.add(node);
                }
                node.network = null;
            }
        }
        Set<PipeNode> visited = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
        for (PipeNode start : starts) {
            if (visited.contains(start) || getPipe(start.getTileX(), start.getTileY(), start.getLayer()) != start) {
                continue;
            }
            List<PipeNode> group = new ArrayList<>();
            ArrayDeque<PipeNode> queue = new ArrayDeque<>();
            visited.add(start);
            queue.add(start);
            MineralTier lowest = start.getTier();
            while (!queue.isEmpty()) {
                PipeNode node = queue.poll();
                group.add(node);
                lowest = MineralTier.lowest(lowest, node.getTier());
                for (PipeNode next : linkedPipes(node)) {
                    if (visited.add(next)) {
                        if (next.network != null && !old.contains(next.network)) {
                            // A link merged in a network that was not dissolved yet.
                            networks.remove(next.network);
                            old.add(next.network);
                        }
                        queue.add(next);
                    }
                }
            }
            int cellCapacity = tierRules.getTransportAmount(lowest);
            for (PipeNode node : group) {
                node.applyCellCapacity(cellCapacity);
            }
            PipeNetwork network = new PipeNetwork(group, lowest, cellCapacity);
            for (PipeNode node : group) {
                node.network = network;
            }
            networks.add(network);
        }
        topologyChanged();
    }

    private void topologyChanged() {
        topologyVersion++;
    }

    static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyY(long key) {
        return (int) key;
    }

}
