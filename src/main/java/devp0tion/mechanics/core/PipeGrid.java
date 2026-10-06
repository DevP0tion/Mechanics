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
import java.util.Set;

/**
 * The pipe network manager of one level: every pipe, tank valve and pump, the networks the fluid
 * forms, and the push logic that moves fluid (N7-1).
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
 *     pump's other sources (N16-3, {@link Check#DIFFERENT_SOURCE_FLUID}).</li>
 * </ul>
 * Pipes may always be placed and linked: where two fluids meet, the face is simply not used, a
 * dead end (N13-2). Only the pump's sources are checked for mixing (N16-3, N17-1).
 *
 * <h2>Networks (N13-1, N18-2, N18-3)</h2>
 * A network ({@link PipeNetwork}) is the set of pipes the fluid has actually reached, linked and
 * holding the same fluid, with the pumps pushing that fluid into them. An empty pipe belongs to no
 * network. A new pump starts a network of its own; when its fluid reaches a pipe of another network
 * of the same fluid, the networks merge. Changes rebuild the networks around them, as before.
 * Route caching is per network: each network keeps the routes of its pumps with the tiles they
 * cross, and a change on one of those tiles drops only those routes.
 *
 * <h2>Pushing (N7, N12, N14, N18-1)</h2>
 * <ul>
 *     <li>A pump's destinations are the valves it reaches through pipes that are empty or hold its
 *     fluid, whose tank has room (the pump's own source tanks excluded), each by its shortest pipe
 *     path (the pump's first pipe = 1).</li>
 *     <li>The pushed amount is split equally; what does not divide evenly goes one unit each to the
 *     nearest destinations (equal distance: smaller tile y, then x). A share a destination cannot
 *     take is split again among the others (N7-2).</li>
 *     <li>Each share fills the pipes along its path, then enters the tank. Only the frontier (the
 *     first pipe not yet full) is written: each path remembers it, and full pipes are never written
 *     again (N7-1).</li>
 *     <li>Each pipe holds its own tier's transport amount (N12-3) and lets at most that much flow
 *     through it per cycle window of {@link #CYCLE_TICKS} ticks, so the lowest amount on a path caps
 *     what moves along it per pump cycle (N14-2); pumps pushing through the same pipes add up and
 *     share it (N18-1).</li>
 *     <li>When the fluid reaches a new pipe, the tier conditions are judged against the lowest tier
 *     of the network it comes from and that pipe (N12-4, N14-1). If they fail, only that pipe breaks:
 *     it is removed (the game removes its object without a drop), nothing else breaks, and only the
 *     share headed into it is lost; the rest of the push and the fuel go on normally.</li>
 *     <li>No destination, or every destination full: the pump stops (N7-4). Valves accept input
 *     automatically; valve output is TODO (N7-3). There is no draining (N16-5).</li>
 * </ul>
 * TODO(design): pumps sharing a capped path share it in push order within the cycle window (the
 * first to push takes what it needs); whether it should be split evenly is not decided (N18-1).
 *
 * <h2>Unloaded regions (N14-3, N15-1)</h2>
 * Pipes of unloaded regions stay in the grid as read-only mirrors ({@link #unloadPipe}). A path
 * may pass through them only when they are full of its fluid, so an unloaded region whose pipes on
 * the path are all full is skipped and the check goes on in the next region, also across several
 * unloaded regions in a row; any other unloaded pipe is a dead end. Unloaded pipes are never
 * written. Pumps and valves of unloaded regions are not in the grid (they do not run).
 */
public final class PipeGrid {

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
        DIFFERENT_SOURCE_FLUID
    }

    /** The parts that carry link flags. */
    public enum Part {
        BASIC_PIPE,
        UNDERGROUND_PIPE,
        VALVE,
        PUMP
    }

    /** Told about every link flag change, so the game can save and sync it. */
    public interface Listener {
        void onLinksChanged(int tileX, int tileY, Part part);
    }

    /** The cycle window of the transport cap: one pump cycle, 20 ticks (N6-1, N14-2). */
    public static final int CYCLE_TICKS = 20;

    private static final Listener NO_LISTENER = (x, y, part) -> {
    };

    private final PipeTierRules tierRules;
    private Listener listener = NO_LISTENER;
    private final Map<Long, PipeNode> basePipes = new HashMap<>();
    private final Map<Long, PipeNode> undergroundPipes = new HashMap<>();
    private final Map<Long, TankValve> valves = new HashMap<>();
    private final Map<TankValve, Long> valvePositions = new IdentityHashMap<>();
    private final Map<Long, Pump> pumps = new HashMap<>();
    private final Set<PipeNetwork> networks = new LinkedHashSet<>();
    private long tick;

    public PipeGrid(PipeTierRules tierRules) {
        this.tierRules = Objects.requireNonNull(tierRules, "tierRules");
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? NO_LISTENER : listener;
    }

    public PipeTierRules getTierRules() {
        return tierRules;
    }

    /** One game tick: advances the clock of the cycle windows (N14-2). */
    public void tick() {
        tick++;
    }

    public long getTick() {
        return tick;
    }

    long window() {
        return Math.floorDiv(tick, CYCLE_TICKS);
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

    /** Every pipe, loaded or not. */
    public List<PipeNode> getPipes() {
        List<PipeNode> result = new ArrayList<>(basePipes.size() + undergroundPipes.size());
        result.addAll(basePipes.values());
        result.addAll(undergroundPipes.values());
        return result;
    }

    /** The network of the pipe at the tile, or {@code null} if there is no pipe or it is empty (N13-1). */
    public PipeNetwork getNetwork(int x, int y, PipeLayer layer) {
        PipeNode node = getPipe(x, y, layer);
        return node == null ? null : node.network;
    }

    public List<PipeNetwork> getNetworks() {
        return Collections.unmodifiableList(new ArrayList<>(networks));
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

    /** The valves linked to a pump's sides: its tank sources (11-1 ②, N16-3), in pull order. */
    public List<TankValve> getSourceValves(Pump pump) {
        List<TankValve> result = new ArrayList<>();
        for (Pump.SourceSlot slot : pump.getSourceSlots()) {
            if (slot.direction != null) {
                TankValve valve = valves.get(key(pump.getTileX() + slot.direction.dx, pump.getTileY() + slot.direction.dy));
                if (valve != null) {
                    result.add(valve);
                }
            }
        }
        return result;
    }

    /** Whether a pump and the valve on its side {@code direction} are linked (N16-3). */
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
        pipes(layer).put(key(x, y), node);
        onStructureChanged(x, y);
        return node;
    }

    /**
     * Adds a pipe with saved state: its region was loaded ({@code loaded}) or the level's mirror of
     * an unloaded region was read (not {@code loaded}). A mirror of the same pipe is reused.
     */
    public PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                             boolean loaded) {
        Objects.requireNonNull(tier, "tier");
        long key = key(x, y);
        FluidType contents = amount > 0 ? fluid : null;
        int contentsAmount = contents == null ? 0 : amount;
        PipeNode existing = pipes(layer).get(key);
        if (existing != null && existing.getTier() == tier) {
            if (existing.getLinks() == LinkFlags.sanitize(links) && existing.getFluid() == contents
                    && existing.getAmount() == contentsAmount) {
                if (existing.loaded != loaded) {
                    existing.loaded = loaded;
                    onStructureChanged(x, y);
                }
                return existing;
            }
            // The same pipe with other saved state: update it in place and regroup its network.
            PipeNetwork network = existing.network;
            if (network != null) {
                network.nodes.remove(existing);
                existing.network = null;
            }
            existing.setLinks(links);
            existing.setContents(contents, contentsAmount);
            existing.loaded = loaded;
            if (network != null) {
                rebuild(Collections.singletonList(network));
            }
            if (existing.isReached()) {
                attachReached(existing);
            }
            onStructureChanged(x, y);
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
        node.loaded = loaded;
        pipes(layer).put(key, node);
        if (node.isReached()) {
            attachReached(node);
        }
        onStructureChanged(x, y);
        return node;
    }

    /** The pipe's region unloaded: it stays as a read-only mirror of its state (N14-3). */
    public PipeNode unloadPipe(int x, int y, PipeLayer layer) {
        PipeNode node = getPipe(x, y, layer);
        if (node != null && node.loaded) {
            node.loaded = false;
            onStructureChanged(x, y);
        }
        return node;
    }

    /**
     * Removes a pipe (picked up, broken, or its mirror dropped). Its fluid is lost (N12-1); the
     * network splits as needed and every remaining pipe keeps its fluid.
     *
     * @return the removed pipe, or {@code null} if there was none
     */
    public PipeNode removePipe(int x, int y, PipeLayer layer) {
        PipeNode node = pipes(layer).remove(key(x, y));
        if (node == null) {
            return null;
        }
        node.removed = true;
        PipeNetwork network = node.network;
        if (network != null) {
            network.nodes.remove(node);
            node.network = null;
            rebuild(Collections.singletonList(network));
        }
        onStructureChanged(x, y);
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
     * Places a new valve. Its links start open, except toward pumps already next to it: those start
     * cut, so the pumps keep their sources (N13-3, N16-3).
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
        if (changed) {
            listener.onLinksChanged(x, y, Part.VALVE);
        }
    }

    /** Adds a valve with its saved link flags (its region was loaded). */
    public void loadValve(int x, int y, TankValve valve) {
        addValve(x, y, valve);
    }

    private void addValve(int x, int y, TankValve valve) {
        Objects.requireNonNull(valve, "valve");
        long key = key(x, y);
        if (isBaseLayerTaken(key) || valvePositions.containsKey(valve)) {
            throw new IllegalStateException("Cannot place valve at " + x + "," + y);
        }
        valves.put(key, valve);
        valvePositions.put(valve, key);
        onStructureChanged(x, y);
    }

    /** The valve was removed: pumps next to it lose it as a source. */
    public TankValve removeValve(int x, int y) {
        TankValve valve = unloadValve(x, y);
        if (valve != null) {
            for (Direction d : Direction.values()) {
                Pump pump = pumps.get(key(x + d.dx, y + d.dy));
                if (pump != null) {
                    pump.removeSourceSlot(Pump.SourceSlot.valve(d.opposite()));
                }
            }
        }
        return valve;
    }

    /** The valve's region unloaded: it leaves the grid, the pumps keep it as a source. */
    public TankValve unloadValve(int x, int y) {
        TankValve valve = valves.remove(key(x, y));
        if (valve != null) {
            valvePositions.remove(valve);
            onStructureChanged(x, y);
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
        addPump(x, y, pump);
    }

    /** Adds a pump with its saved state (its region was loaded). */
    public void loadPump(int x, int y, Pump pump) {
        Objects.requireNonNull(pump, "pump");
        addPump(x, y, pump);
    }

    private void addPump(int x, int y, Pump pump) {
        long key = key(x, y);
        if (isBaseLayerTaken(key) || pump.grid != null) {
            throw new IllegalStateException("Cannot place pump at " + x + "," + y);
        }
        pumps.put(key, pump);
        pump.place(this, x, y);
        PipeNetwork own = new PipeNetwork();
        networks.add(own);
        own.addPump(pump);
        // A loaded pump that pushed before joins the network its fluid reached (N18-2).
        rebuild(Collections.singletonList(own));
        onStructureChanged(x, y);
    }

    /** Removes a pump (picked up, or its region unloaded). */
    public Pump removePump(int x, int y) {
        Pump pump = pumps.remove(key(x, y));
        if (pump != null) {
            pump.grid = null;
            PipeNetwork network = pump.network;
            if (network != null) {
                network.pumps.remove(pump);
                pump.network = null;
                rebuild(Collections.singletonList(network));
            }
            onStructureChanged(x, y);
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
     */
    public Check toggleSide(int x, int y, Part part, Direction direction) {
        End own = end(x, y, part, direction);
        if (own == null) {
            return Check.NOTHING_THERE;
        }
        int nx = x + direction.dx;
        int ny = y + direction.dy;
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
     * only its own flag flips.
     */
    public Check toggleVertical(int x, int y) {
        long key = key(x, y);
        PipeNode base = basePipes.get(key);
        TankValve valve = valves.get(key);
        PipeNode under = undergroundPipes.get(key);
        End top = base != null ? new End(Part.BASIC_PIPE, () -> base.isVerticalOpen(), base::setVerticalOpen)
                : valve != null ? new End(Part.VALVE, () -> valve.isVerticalOpen(), valve::setVerticalOpen) : null;
        End bottom = under == null ? null
                : new End(Part.UNDERGROUND_PIPE, () -> under.isVerticalOpen(), under::setVerticalOpen);
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
        return pump.grid == this && pumps.get(key(pump.getTileX(), pump.getTileY())) == pump;
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
                // Cached routes depend on tiles, not on the network: they move with their pump.
                target.routeCache.putAll(network.routeCache);
            }
            target.addNode(node);
        }
        onReached(node);
    }

    /**
     * Rebuilds networks after a change (N18-3): every member of {@code dissolve} (plus any network a
     * member turns out to be joined to) is regrouped by its current links.
     */
    private void rebuild(Collection<PipeNetwork> dissolve) {
        Set<PipeNetwork> old = Collections.newSetFromMap(new IdentityHashMap<PipeNetwork, Boolean>());
        List<Object> members = new ArrayList<>();
        Map<Pump, PumpRoutes> caches = new IdentityHashMap<>();
        for (PipeNetwork network : dissolve) {
            dissolveInto(network, old, members, caches);
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
                    Pump pump = (Pump) member;
                    network.addPump(pump);
                    // Cached routes depend on tiles, not on the network: they move with their pump.
                    PumpRoutes cached = caches.get(pump);
                    if (cached != null) {
                        network.routeCache.put(pump, cached);
                    }
                }
                for (Object next : edges(member)) {
                    PipeNetwork nextNetwork = networkOf(next);
                    if (nextNetwork != null && nextNetwork != network && !old.contains(nextNetwork)) {
                        // Joined to a network that was not dissolved yet: regroup it too.
                        dissolveInto(nextNetwork, old, members, caches);
                    }
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
    }

    private void dissolveInto(PipeNetwork network, Set<PipeNetwork> old, List<Object> members,
                              Map<Pump, PumpRoutes> caches) {
        if (!old.add(network)) {
            return;
        }
        caches.putAll(network.routeCache);
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
    void onPumpPushing(Pump pump, FluidType fluid) {
        if (pump.getLastPushedFluid() != fluid) {
            pump.setLastPushedFluid(fluid);
            if (pump.network != null) {
                rebuild(Collections.singletonList(pump.network));
            }
        }
    }

    // ---------------------------------------------------------------- route caches

    /** The routes of one pump for one fluid, with the tiles they depend on (N18-2). */
    static final class PumpRoutes {
        final FluidType fluid;
        final List<Route> routes;
        final Set<Long> footprint;

        PumpRoutes(FluidType fluid, List<Route> routes, Set<Long> footprint) {
            this.fluid = fluid;
            this.routes = routes;
            this.footprint = footprint;
        }
    }

    /** Something changed on a tile: the cached routes crossing it or next to it are dropped. */
    private void onStructureChanged(int x, int y) {
        long key = key(x, y);
        for (PipeNetwork network : networks) {
            network.routeCache.values().removeIf(routes -> routes.footprint.contains(key));
        }
    }

    /** A pipe was reached: routes of other fluids crossing it are dropped (it is a dead end for them now). */
    private void onReached(PipeNode node) {
        long key = key(node.getTileX(), node.getTileY());
        FluidType fluid = node.getFluid();
        for (PipeNetwork network : networks) {
            network.routeCache.values().removeIf(routes -> routes.fluid != fluid && routes.footprint.contains(key));
        }
    }

    /** Whether {@code fluid} may pass the pipe: not removed, empty or holding it, and readable. */
    private static boolean traversable(PipeNode node, FluidType fluid) {
        if (node.removed) {
            return false;
        }
        FluidType held = node.getFluid();
        if (held != null && held != fluid) {
            return false;
        }
        // An unloaded pipe is a dead end unless it is full: then it is skipped (N14-3, N15-1).
        return node.loaded || held == fluid && node.isFull();
    }

    /** Shortest routes from the pump to every reachable valve for {@code fluid}, cached per network. */
    List<Route> routesFor(Pump pump, FluidType fluid) {
        PipeNetwork network = pump.network;
        PumpRoutes cached = network == null ? null : network.routeCache.get(pump);
        if (cached != null && cached.fluid == fluid) {
            return cached.routes;
        }
        Set<Long> footprint = new HashSet<>();
        addAround(footprint, pump.getTileX(), pump.getTileY());
        Map<PipeNode, PipeNode> parent = new IdentityHashMap<>();
        ArrayDeque<PipeNode> queue = new ArrayDeque<>();
        for (PipeNode entry : getPumpEntries(pump)) {
            if (traversable(entry, fluid) && !parent.containsKey(entry)) {
                parent.put(entry, null);
                queue.add(entry);
            }
        }
        Map<TankValve, PipeNode> attach = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            PipeNode node = queue.poll();
            addAround(footprint, node.getTileX(), node.getTileY());
            for (TankValve valve : getLinkedValves(node)) {
                if (!attach.containsKey(valve)) {
                    attach.put(valve, node);
                }
            }
            for (PipeNode next : linkedPipes(node)) {
                if (!parent.containsKey(next) && traversable(next, fluid)) {
                    parent.put(next, node);
                    queue.add(next);
                }
            }
        }
        List<Route> routes = new ArrayList<>();
        for (Map.Entry<TankValve, PipeNode> entry : attach.entrySet()) {
            List<PipeNode> path = new ArrayList<>();
            for (PipeNode n = entry.getValue(); n != null; n = parent.get(n)) {
                path.add(n);
            }
            Collections.reverse(path);
            long valveKey = valvePositions.get(entry.getKey());
            routes.add(new Route(entry.getKey(), keyX(valveKey), keyY(valveKey), path.toArray(new PipeNode[0])));
        }
        routes.sort(Route.ORDER);
        if (network != null) {
            network.routeCache.put(pump, new PumpRoutes(fluid, routes, footprint));
        }
        return routes;
    }

    private static void addAround(Set<Long> footprint, int x, int y) {
        footprint.add(key(x, y));
        for (Direction d : Direction.values()) {
            footprint.add(key(x + d.dx, y + d.dy));
        }
    }

    /** Pipe path distance from the pump to a valve for {@code fluid} (for tests and the game), or -1. */
    public int getPathDistance(Pump pump, TankValve valve, FluidType fluid) {
        for (Route route : routesFor(pump, fluid)) {
            if (route.valve == valve) {
                return route.nodes.length;
            }
        }
        return -1;
    }

    /** The shortest path from a pump to one valve. */
    static final class Route {
        static final Comparator<Route> ORDER = (a, b) -> {
            if (a.nodes.length != b.nodes.length) {
                return Integer.compare(a.nodes.length, b.nodes.length);
            }
            if (a.valveY != b.valveY) {
                return Integer.compare(a.valveY, b.valveY);
            }
            return Integer.compare(a.valveX, b.valveX);
        };

        final TankValve valve;
        final int valveX;
        final int valveY;
        /** From the pipe next to the pump to the pipe linked to the valve; length = path distance. */
        final PipeNode[] nodes;
        /** Pipes before this index are known to be full: the frontier (N7-1). */
        int frontier;

        Route(TankValve valve, int valveX, int valveY, PipeNode[] nodes) {
            this.valve = valve;
            this.valveX = valveX;
            this.valveY = valveY;
            this.nodes = nodes;
        }
    }

    // ---------------------------------------------------------------- pushing

    /**
     * The destinations of one push from {@code pump} with {@code fluid}: valves reached through
     * pipes that can carry it, excluding the tanks the pump pulls from.
     */
    PushPlan planPush(Pump pump, FluidType fluid) {
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
        return new PushPlan(pump, fluid, destinations);
    }

    /** One push: the destinations and the logic that moves fluid to them, as a dry run or for real. */
    final class PushPlan {
        private final Pump pump;
        private final FluidType fluid;
        private final List<Route> destinations;

        PushPlan(Pump pump, FluidType fluid, List<Route> destinations) {
            this.pump = pump;
            this.fluid = fluid;
            this.destinations = destinations;
        }

        /** What a push of up to {@code amount} would use now (moved plus lost), without changing anything. */
        long simulate(int amount) {
            Ledger ledger = new DryLedger();
            Stats stats = distribute(amount, ledger);
            return (long) stats.pipeFill + stats.deliveredTotal + stats.lost;
        }

        /** Pushes {@code amount} (N7-2). */
        PumpResult run(int amount) {
            Stats stats = distribute(amount, new RealLedger());
            return new PumpResult(PumpResult.Status.PUMPED, fluid, stats.pipeFill + stats.deliveredTotal, stats.pipeFill,
                    stats.updated.size(), stats.lost, stats.delivered, stats.broken);
        }

        /** Equal shares, the remainder to the nearest, what one destination cannot take to the rest (N7-2). */
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
                int count = active.size();
                int base = remaining / count;
                int extra = remaining % count;
                int used = 0;
                List<Route> saturated = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int share = base + (i < extra ? 1 : 0);
                    if (share == 0) {
                        continue;
                    }
                    Route route = active.get(i);
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

        /**
         * Moves one share along a route: the frontier pipes first, then the tank. Returns what the
         * share used: filled, delivered, and lost into a pipe that broke (N14-1).
         */
        private int deliver(Route route, int share, Ledger ledger, Stats stats) {
            PipeNode[] nodes = route.nodes;
            int used = 0;
            while (share > 0) {
                int frontier = advance(route, ledger);
                if (frontier < 0) {
                    break;
                }
                if (frontier < nodes.length) {
                    PipeNode node = nodes[frontier];
                    if (!node.loaded) {
                        // An unloaded pipe that is not full: a dead end (N14-3).
                        break;
                    }
                    if (ledger.amount(node) == 0) {
                        MineralTier lowest = lowestBefore(route, frontier, ledger);
                        lowest = lowest == null ? node.getTier() : MineralTier.lowest(lowest, node.getTier());
                        if (!tierRules.canCarry(lowest, fluid)) {
                            // Only the newly reached pipe breaks, only this share is lost (N12-4, N12-5, N14-1).
                            ledger.breakPipe(node, stats);
                            stats.lost += share;
                            used += share;
                            share = 0;
                            break;
                        }
                    }
                    int put = Math.min(Math.min(share, node.getCapacity() - ledger.amount(node)),
                            flowLeft(nodes, frontier + 1, ledger));
                    if (put <= 0) {
                        break;
                    }
                    ledger.fill(node, put, stats);
                    useFlow(nodes, frontier + 1, put, ledger);
                    stats.pipeFill += put;
                    used += put;
                    share -= put;
                    if (ledger.amount(node) < node.getCapacity()) {
                        // Limited by the flow cap: the rest of the share cannot pass this cycle.
                        break;
                    }
                } else {
                    int take = Math.min(Math.min(share, ledger.tankSpace(route.valve)), flowLeft(nodes, nodes.length, ledger));
                    if (take <= 0) {
                        break;
                    }
                    ledger.deliver(route.valve, take, stats);
                    useFlow(nodes, nodes.length, take, ledger);
                    used += take;
                    share -= take;
                    break;
                }
            }
            return used;
        }

        /**
         * Moves the route's frontier past full pipes of this fluid (reads only). Returns the
         * frontier, {@code nodes.length} when every pipe is full, or -1 when the path is blocked: a
         * pipe was removed or holds another fluid (a dead end, N13-2).
         */
        private int advance(Route route, Ledger ledger) {
            PipeNode[] nodes = route.nodes;
            int frontier = ledger.frontier(route);
            while (frontier < nodes.length) {
                PipeNode node = nodes[frontier];
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

        /** The lowest tier of the network the fluid comes from into {@code nodes[index]} (N12-4). */
        private MineralTier lowestBefore(Route route, int index, Ledger ledger) {
            if (index == 0) {
                PipeNetwork network = pump.network;
                return network != null && pump.getLastPushedFluid() == fluid ? network.lowestTier : null;
            }
            PipeNode previous = route.nodes[index - 1];
            if (previous.network != null) {
                return previous.network.lowestTier;
            }
            // Dry run: the previous pipes are only reached in the ledger.
            MineralTier lowest = lowestBefore(route, 0, ledger);
            for (int i = 0; i < index; i++) {
                lowest = lowest == null ? route.nodes[i].getTier() : MineralTier.lowest(lowest, route.nodes[i].getTier());
            }
            return lowest;
        }

        private int flowLeft(PipeNode[] nodes, int count, Ledger ledger) {
            int left = Integer.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                left = Math.min(left, ledger.flowLeft(nodes[i]));
            }
            return left;
        }

        private void useFlow(PipeNode[] nodes, int count, int amount, Ledger ledger) {
            for (int i = 0; i < count; i++) {
                ledger.useFlow(nodes[i], amount);
            }
        }

        /** Real or dry state of the pipes, tanks and flow caps the push reads and writes. */
        private abstract class Ledger {
            abstract int amount(PipeNode node);

            abstract FluidType fluid(PipeNode node);

            abstract boolean removed(PipeNode node);

            abstract int flowLeft(PipeNode node);

            abstract void useFlow(PipeNode node, int amount);

            abstract void fill(PipeNode node, int amount, Stats stats);

            abstract int tankSpace(TankValve valve);

            abstract void deliver(TankValve valve, int amount, Stats stats);

            abstract void breakPipe(PipeNode node, Stats stats);

            abstract int frontier(Route route);

            abstract void setFrontier(Route route, int frontier);
        }

        private final class RealLedger extends Ledger {
            private final long window = window();

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
            void fill(PipeNode node, int amount, Stats stats) {
                boolean reached = node.isReached();
                node.insert(fluid, amount);
                stats.updated.add(node);
                if (!reached && node.isReached()) {
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
            private final long window = window();
            private final Map<PipeNode, Integer> added = new IdentityHashMap<>();
            private final Map<PipeNode, Integer> flow = new IdentityHashMap<>();
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
