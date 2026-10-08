package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A pump: the only thing that moves fluid (N7-1). It pulls from its sources and pushes through the
 * pipe networks it is linked to ({@link PipeGrid}).
 *
 * <p>As a {@link LiquidStorage} the pump holds the fluid of the cycle in progress; its capacity is
 * one cycle's amount. It only pulls what its destinations can take, so the storage is normally
 * empty between cycles.
 *
 * <h2>Sources (11-1, N16-3, N17-1~N17-3, N19-1, N19-3, N20-3~N20-5)</h2>
 * The pump keeps its connected sources in the order they were connected ({@link #getSourceSlots()}):
 * the liquid tile under it ({@link LiquidTileSource}, registered when it is placed on liquid) and the
 * tanks of tank valves linked to its sides. It pulls from the first one that is not empty and moves
 * on to the next when that one is empty (N19-1). The connected sources hold the same fluid or are
 * empty when they are connected ({@link #canConnectSources}); a source that was empty when connected
 * and fills with another fluid later stays connected (N17-3).
 * <p>The pump pulls only the baseline fluid and sums only the sources holding it (N17-2, N20-3). The
 * baseline is the fluid in the pipe cell the pump pushes into, its output cell
 * ({@link PipeGrid#getOutputFluids}), read per cell and never from the network (N20-5). When the
 * output cells are empty, the pump takes the sources in pull order (N19-1) and the first fluid that
 * enters becomes the baseline. A source holding another fluid, or a fluid the pump's tier cannot move
 * (12-6, N20-4), is dormant: it stays connected, nothing is pulled from it, and it never stops the
 * pump, which pulls from the sources it can use.
 * A valve placed next to a pump later starts with its link cut, so the pump keeps its sources
 * (N13-3, N16-3); the wrench links and cuts valves ({@link PipeGrid#toggleSide}). A valve switched
 * off by a wire signal is no source while it is off: nothing is pulled through it (N27-4).
 * A valve that becomes a plain wall (N33-1) leaves the sources, and comes back as the last one when
 * it is a valve again (N33-16, {@link PipeGrid#setValvePlainWall}, {@link #getPlainWallSides}),
 * whatever its tank holds: while that is another fluid than the baseline it is dormant like any
 * other source (N33-21).
 *
 * <h2>One cycle</h2>
 * <ol>
 *     <li>The fluid is the one left in the pump, else the baseline (N20-5). The tier must be allowed
 *     to move it (12-1, 12-5, 12-6), else nothing happens.</li>
 *     <li>No destination at all, or nothing can move (every tank and every connected pipe full):
 *     the pump stops and pulls nothing (N7-4 as N28-6 reads it). There is no fill speed holding
 *     fluid back in the pump (N28-9 discarded by N32-1).</li>
 *     <li>A log-fueled pump needs a lit log: a lit log burns for its whole timer (100 ticks) whether
 *     or not the pump moves anything (N18-4); a new log is only lit by a cycle that can run (the
 *     N7-4 conditions hold).</li>
 *     <li>Pull up to one cycle's amount (N3-2, N6-1, N6-3) and push it ({@link PipeGrid}).</li>
 * </ol>
 * The manual pump runs a cycle per {@link #click}, at most every 20 ticks (N3-3), in the engine's
 * next tick (N28-21); the log-fueled pumps run one every 20 ticks, on the engine's global push tick
 * (N6-1, N6-3, N28-16; {@link #advanceTimers}); a cycle that cannot run is tried again at the next
 * one. The pipe engine's systems drive every pump ({@link PipeGrid#runTick}, N22-5).
 * A pump's speed does not depend on how many sources it has (N18-1).
 *
 * <p>Wire (11-3, N11-3): from tier 2 up a wire signal switches the pump off; the game maps the
 * signal to {@link #setEnabled}.
 * <p>The manual pump's click cooldown is per pump: one cycle every 20 ticks however many players
 * click (N3-3, N31-3).
 */
public class Pump extends LiquidStorage {

    /** Where a log-fueled pump gets its logs; the game implements it over the pump's inventory. */
    public interface FuelSupply {
        /** Removes one log ({@link PumpTier#LOG_FUEL_INGREDIENT}); false if there is none. */
        boolean consumeLog();
    }

    /** One connected source: the liquid tile under the pump, or the valve on one side. */
    public static final class SourceSlot {
        /** The liquid tile under the pump (11-1 ①). */
        public static final SourceSlot TILE = new SourceSlot(null);
        private static final SourceSlot[] SIDES = {
                new SourceSlot(Direction.NORTH), new SourceSlot(Direction.EAST),
                new SourceSlot(Direction.SOUTH), new SourceSlot(Direction.WEST)};

        /** The side of the valve, or {@code null} for the tile. */
        public final Direction direction;

        private SourceSlot(Direction direction) {
            this.direction = direction;
        }

        /** The valve on the given side (11-1 ②). */
        public static SourceSlot valve(Direction direction) {
            return SIDES[direction.ordinal()];
        }

        /** For saving: -1 for the tile, else the direction ordinal. */
        public int code() {
            return direction == null ? -1 : direction.ordinal();
        }

        /** The slot of a saved code, or {@code null} for an unknown code. */
        public static SourceSlot fromCode(int code) {
            if (code == -1) {
                return TILE;
            }
            return code >= 0 && code < SIDES.length ? SIDES[code] : null;
        }

        @Override
        public String toString() {
            return direction == null ? "TILE" : direction.toString();
        }
    }

    private final PumpTier tier;
    private final List<SourceSlot> sourceSlots = new ArrayList<>();
    private FluidSource tileSource;
    private FuelSupply fuel;
    private boolean enabled = true;
    private int burnTicksLeft;
    private int ticksSinceCycle;
    private int links = LinkFlags.ALL_OPEN;
    /** The sides whose valve the pump last saw as a plain wall (N33-16, {@link #getPlainWallSides}). */
    private int plainWallSides;
    private FluidType lastPushedFluid;
    /** The level-wide install number (N28-17): the placement order; -1 until the engine gives one. */
    private long installNumber = -1;
    private int x;
    private int y;
    PumpHost host;
    PipeNetwork network;

    /**
     * @throws UnsupportedOperationException for {@link PumpTier#ELECTRIC}, whose values are undecided
     */
    public Pump(PumpTier tier) {
        super(Objects.requireNonNull(tier, "tier").getUnitsPerCycle());
        this.tier = tier;
        this.ticksSinceCycle = tier.getCycleTicks();
    }

    public PumpTier getTier() {
        return tier;
    }

    // ------------------------------------------------------------------ sources

    /**
     * Placement check (N17-1, replacing N11-4): a pump may be placed when every source that would be
     * connected holds the same fluid or is empty, however many there are. Rejected when different
     * fluids would mix.
     *
     * @param sourceFluids the fluids of the sources the pump would connect ({@code null} = empty)
     */
    public static boolean canConnectSources(Collection<FluidType> sourceFluids) {
        FluidType seen = null;
        for (FluidType fluid : sourceFluids) {
            if (fluid == null) {
                continue;
            }
            if (seen != null && seen != fluid) {
                return false;
            }
            seen = fluid;
        }
        return true;
    }

    /** The liquid tile source (registered by the game when the pump is placed on liquid), or {@code null}. */
    public FluidSource getTileSource() {
        return tileSource;
    }

    /** Sets the object behind the {@link SourceSlot#TILE} slot (the game's {@link LiquidTileSource}). */
    public void setTileSource(FluidSource tileSource) {
        this.tileSource = tileSource;
    }

    /** The connected sources, first connected first (N19-1). */
    public List<SourceSlot> getSourceSlots() {
        return Collections.unmodifiableList(sourceSlots);
    }

    /** Restores the saved source order (before the pump is added to a grid). */
    public void setSourceSlots(Collection<SourceSlot> slots) {
        sourceSlots.clear();
        for (SourceSlot slot : slots) {
            if (slot != null && !sourceSlots.contains(slot)) {
                sourceSlots.add(slot);
            }
        }
    }

    /** Appends a source (connected now: last in the pull order, also when a stale entry was left). */
    void addSourceSlot(SourceSlot slot) {
        sourceSlots.remove(slot);
        sourceSlots.add(slot);
    }

    /** Drops the valve slots behind its own cut sides: a cut link is no source (N16-3). */
    void dropCutSourceSlots() {
        for (int i = sourceSlots.size() - 1; i >= 0; i--) {
            Direction direction = sourceSlots.get(i).direction;
            if (direction != null && !isSideOpen(direction)) {
                sourceSlots.remove(i);
            }
        }
    }

    void removeSourceSlot(SourceSlot slot) {
        sourceSlots.remove(slot);
    }

    /**
     * The sides whose valve the pump last saw as a plain wall (N33-1, N33-16), as {@link LinkFlags}
     * side bits. That valve left its sources; once the pump sees it as a valve again it comes back
     * as the last one ({@link PipeGrid#setValvePlainWall}). Saved, so a valve that stopped or started
     * being a plain wall while the pump was not loaded is followed when the pump loads again.
     */
    public int getPlainWallSides() {
        return plainWallSides;
    }

    /** Restores the saved plain wall sides (before the pump is added to a grid). */
    public void setPlainWallSides(int sides) {
        plainWallSides = sides & LinkFlags.SIDES;
    }

    boolean sawPlainWall(Direction direction) {
        return LinkFlags.isSideOpen(plainWallSides, direction);
    }

    void setSawPlainWall(Direction direction, boolean plainWall) {
        plainWallSides = LinkFlags.withSide(plainWallSides, direction, plainWall);
    }

    /**
     * The connected sources in pull order (N19-1): the liquid tile source and the tanks of the
     * linked valves. A valve without a recognized tank gives nothing.
     */
    public List<FluidSource> getSources() {
        List<FluidSource> result = new ArrayList<>();
        for (SourceSlot slot : sourceSlots) {
            FluidSource source = resolve(slot);
            if (source != null) {
                result.add(source);
            }
        }
        return result;
    }

    private FluidSource resolve(SourceSlot slot) {
        if (slot.direction == null) {
            return tileSource;
        }
        TankValve valve = linkedValve(slot);
        if (valve == null || !valve.isEnabled()) {
            // A valve switched off by a wire signal blocks pulling too: no source while it is off,
            // like a dormant tank (N27-4).
            return null;
        }
        return valve.getTank();
    }

    /**
     * The valve behind a valve slot while it is linked, or {@code null}: no valve there now (its
     * region is unloaded), or a stale saved slot of a cut link; a valve is a source only while it is
     * linked (N16-3).
     */
    private TankValve linkedValve(SourceSlot slot) {
        if (slot.direction == null || host == null || !host.isPumpValveLinked(this, slot.direction)) {
            return null;
        }
        return host.getValve(x + slot.direction.dx, y + slot.direction.dy);
    }

    /**
     * The tanks of the valves linked to the pump: never destinations of its own push. A valve
     * switched off by a wire signal (no source while it is off, N27-4) still counts here, so the
     * pump does not push into the tank it is linked to for pulling.
     */
    List<TankStorage> getSourceTanks() {
        List<TankStorage> result = new ArrayList<>();
        for (SourceSlot slot : sourceSlots) {
            TankValve valve = linkedValve(slot);
            TankStorage tank = valve == null ? null : valve.getTank();
            if (tank != null) {
                result.add(tank);
            }
        }
        return result;
    }

    /** The fluids stored in the connected sources (for the N16-3 and N17-1 checks); empty ones left out. */
    List<FluidType> getSourceFluids(SourceSlot except) {
        List<FluidType> result = new ArrayList<>();
        for (SourceSlot slot : sourceSlots) {
            if (slot == except) {
                continue;
            }
            FluidType fluid = storedFluidOf(resolve(slot));
            if (fluid != null) {
                result.add(fluid);
            }
        }
        return result;
    }

    /** The fluid a source holds, also while it gives nothing (an inactive tank). */
    static FluidType storedFluidOf(FluidSource source) {
        if (source == null) {
            return null;
        }
        if (source instanceof LiquidStorage) {
            return ((LiquidStorage) source).getFluid();
        }
        if (source instanceof LiquidTileSource) {
            LiquidTileSource tile = (LiquidTileSource) source;
            return tile.getBuffered() > 0 ? tile.getBufferedFluid() : tile.getTileFluid();
        }
        return source.getSourceFluid();
    }

    /**
     * The fluid of the cycle: what is left in the pump, else the baseline (N20-5): the fluid in the
     * output cells, or, while they are empty, the first source in pull order (N19-1) that is not
     * empty and holds a fluid the tier can move (N20-4). Sources of any other fluid are dormant
     * (N20-3). {@code null} when no source can give anything.
     * <ul>
     *     <li>Fluid left in the pump goes first (N27-3): when it has nowhere to go, the pump stays
     *     stopped ({@link PumpResult.Status#NO_DESTINATION}) until a place for it appears.</li>
     *     <li>Output cells holding different fluids (N27-2): the fluid the pump last pushed stays the
     *     baseline while at least one output cell holds it; when none does any more (or the pump
     *     never pushed), the pump chooses as with empty output cells. The fluid only enters the
     *     output cells that are empty or hold it.</li>
     * </ul>
     */
    FluidType cycleFluid() {
        if (getFluid() != null) {
            return getFluid();
        }
        Set<FluidType> outputs = host.getOutputFluids(this);
        if (outputs.size() == 1) {
            return outputs.iterator().next();
        }
        if (outputs.size() > 1 && lastPushedFluid != null && outputs.contains(lastPushedFluid)) {
            return lastPushedFluid;
        }
        // Empty output cells: the first fluid that enters becomes the baseline (N20-5); output
        // cells of different fluids none of which the pump last pushed: the same choice (N27-2).
        return firstMovableSourceFluid();
    }

    /** The fluid of the first non-empty source the tier can move, in pull order (N19-1, N20-4). */
    private FluidType firstMovableSourceFluid() {
        for (FluidSource source : getSources()) {
            FluidType fluid = source.getSourceFluid();
            if (fluid != null && tier.canPump(fluid) && source.getAvailable(fluid) > 0) {
                return fluid;
            }
        }
        return null;
    }

    /** Whether any source has something to give, also a fluid the tier cannot move. */
    private boolean anySourceHasFluid() {
        for (FluidSource source : getSources()) {
            FluidType fluid = source.getSourceFluid();
            if (fluid != null && source.getAvailable(fluid) > 0) {
                return true;
            }
        }
        return false;
    }

    private long available(FluidType fluid) {
        long total = getAmount();
        for (FluidSource source : getSources()) {
            total += source.getAvailable(fluid);
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return total;
    }

    /** Pulls up to {@code want} of {@code fluid}, first source first (N19-1). */
    private void pull(FluidType fluid, int want) {
        for (FluidSource source : getSources()) {
            if (want <= 0) {
                return;
            }
            int available = source.getAvailable(fluid);
            if (available <= 0) {
                continue;
            }
            int pulled = source.extract(fluid, Math.min(want, available));
            insert(fluid, pulled);
            want -= pulled;
        }
    }

    // ------------------------------------------------------------------ links and state

    /** The link flags of its sides ({@link LinkFlags}; the vertical bit is unused, pumps never link
     * to underground pipes, 9-9). */
    public int getLinks() {
        return links;
    }

    /** Restores saved link flags (before the pump is added to a grid). */
    public void setLinks(int links) {
        this.links = LinkFlags.sanitize(links);
    }

    public boolean isSideOpen(Direction direction) {
        return LinkFlags.isSideOpen(links, direction);
    }

    void setSideOpen(Direction direction, boolean open) {
        links = LinkFlags.withSide(links, direction, open);
    }

    /** The fluid the pump last pushed: the fluid of its network (N18-2), or {@code null}. */
    public FluidType getLastPushedFluid() {
        return lastPushedFluid;
    }

    /** Restores the saved value (before the pump is added to a grid). */
    public void setLastPushedFluid(FluidType fluid) {
        this.lastPushedFluid = fluid;
    }

    /** The pump's network (N18-2); a new pump has one of its own. */
    public PipeNetwork getNetwork() {
        return network;
    }

    public void setFuelSupply(FuelSupply fuel) {
        this.fuel = fuel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Wire control (11-3, N11-3), for tier 2 and up.
     *
     * @throws IllegalStateException for the manual pump, which has no wire control
     */
    public void setEnabled(boolean enabled) {
        if (!tier.isWireControllable()) {
            throw new IllegalStateException(tier + " pump has no wire control (11-3)");
        }
        this.enabled = enabled;
    }

    /** Burn time left of the lit log, in ticks (0: no log is burning). */
    public int getBurnTicksLeft() {
        return burnTicksLeft;
    }

    /** Restores the burn time when loading. */
    public void setBurnTicksLeft(int ticks) {
        burnTicksLeft = requireNonNegative(ticks, "ticks");
    }

    /** Tile position while placed in a grid. */
    public int getTileX() {
        return x;
    }

    public int getTileY() {
        return y;
    }

    void place(PumpHost host, int x, int y) {
        this.host = host;
        this.x = x;
        this.y = y;
    }

    // ------------------------------------------------------------------ running

    /**
     * The timer part of one game tick (the pipe engine's timer system, N22-5): the lit log burns down
     * (N18-4) and the click cooldown counts (N3-3). A log-fueled pump's cycle is due only on the
     * engine's global push tick, {@code cycleTick} (N28-16). Returns {@code null} when a cycle is due
     * now (the engine runs it, {@link #runCycle}), else what the tick did instead.
     */
    PumpResult advanceTimers(boolean cycleTick) {
        if (burnTicksLeft > 0) {
            burnTicksLeft--;
        }
        if (ticksSinceCycle < tier.getCycleTicks()) {
            ticksSinceCycle++;
        }
        if (tier.getPower() == PumpTier.Power.HAND_CLICK) {
            return PumpResult.WAITING;
        }
        if (!enabled) {
            return PumpResult.DISABLED;
        }
        if (!cycleTick) {
            return PumpResult.WAITING;
        }
        ticksSinceCycle = 0;
        return null;
    }

    /**
     * The install number (N28-17): the level-wide placement order, the tie-break of the cap order
     * (N26-4). It stays when links change or networks merge; saved with the pump. -1: none yet.
     */
    public long getInstallNumber() {
        return installNumber;
    }

    /** Restores a saved install number (before the pump is added to a grid), or the engine gives one. */
    public void setInstallNumber(long installNumber) {
        this.installNumber = installNumber;
    }

    /**
     * A click on the manual pump (11-3): one cycle, at most once per click cooldown (N3-3). The game
     * calls it on the server only.
     *
     * @throws IllegalStateException for pumps that are not operated by hand
     */
    public PumpResult click() {
        if (tier.getPower() != PumpTier.Power.HAND_CLICK) {
            throw new IllegalStateException(tier + " pump is not operated by hand");
        }
        if (ticksSinceCycle < tier.getCycleTicks()) {
            return PumpResult.WAITING;
        }
        PumpResult result = runCycle();
        if (result.isPumped()) {
            ticksSinceCycle = 0;
        }
        return result;
    }

    /** One cycle (due from {@link #advanceTimers} or a click). */
    PumpResult runCycle() {
        if (host == null) {
            throw new IllegalStateException("Pump is not placed in a grid");
        }
        // The liquid tile source searches its tiles once per cycle (N19-3, technical).
        LiquidTileSource tiles = tileSource instanceof LiquidTileSource ? (LiquidTileSource) tileSource : null;
        if (tiles != null) {
            tiles.beginCycle();
        }
        try {
            return runCycleSteps();
        } finally {
            if (tiles != null) {
                tiles.endCycle();
            }
        }
    }

    private PumpResult runCycleSteps() {
        FluidType fluid = cycleFluid();
        if (fluid == null) {
            // Nothing to pull: every source is empty, or holds a fluid the tier cannot move (N20-4).
            return anySourceHasFluid() ? PumpResult.FLUID_NOT_ALLOWED : PumpResult.NO_SOURCE;
        }
        if (!tier.canPump(fluid)) {
            // The output cells hold a fluid the tier cannot move: every source is dormant (N20-4, N20-5).
            return PumpResult.FLUID_NOT_ALLOWED;
        }
        PushPlan plan = host.planPush(this, fluid);
        long acceptable = plan.simulate(getCapacity());
        if (acceptable == 0) {
            return PumpResult.NO_DESTINATION;
        }
        if (available(fluid) == 0) {
            return PumpResult.NO_SOURCE;
        }
        if (tier.usesLogFuel() && burnTicksLeft == 0) {
            // A new log is lit only when the pump can run (N7-4, N18-4).
            if (fuel == null || !fuel.consumeLog()) {
                return PumpResult.NO_FUEL;
            }
            burnTicksLeft = tier.getLogBurnTicks();
        }
        int want = (int) Math.min(getCapacity(), acceptable) - getAmount();
        if (want > 0) {
            pull(fluid, want);
        }
        host.onPumpPushing(this, fluid);
        PumpResult result = plan.run(getAmount());
        extract(fluid, Math.min(getAmount(), result.getMoved() + result.getLost()));
        return result;
    }

    @Override
    public String toString() {
        return "Pump[" + tier + " " + x + "," + y + ", sources " + sourceSlots + ", " + super.toString() + "]";
    }

}
