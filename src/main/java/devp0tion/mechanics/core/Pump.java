package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A pump: the only thing that moves fluid (N7-1). It pulls from its sources and pushes through the
 * pipe networks it is linked to ({@link PipeGrid}).
 *
 * <p>As a {@link LiquidStorage} the pump holds the fluid of the cycle in progress; its capacity is
 * one cycle's amount. It only pulls what its destinations can take, so the storage is normally
 * empty between cycles.
 *
 * <h2>Sources (11-1, N16-3, N17-1~N17-3, N19-1, N19-3)</h2>
 * The pump keeps its connected sources in the order they were connected ({@link #getSourceSlots()}):
 * the liquid tile under it ({@link LiquidTileSource}, registered when it is placed on liquid) and the
 * tanks of tank valves linked to its sides. It pulls from the first one that is not empty and moves
 * on to the next when that one is empty (N19-1). The connected sources hold the same fluid or are
 * empty when they are connected ({@link #canConnectSources}); a source that was empty when connected
 * and fills with another fluid later stays connected (N17-3), and the pump then pushes the fluid of
 * the first non-empty source, which never enters pipes holding another fluid.
 * A valve placed next to a pump later starts with its link cut, so the pump keeps its sources
 * (N13-3, N16-3); the wrench links and cuts valves ({@link PipeGrid#toggleSide}).
 *
 * <h2>One cycle</h2>
 * <ol>
 *     <li>The fluid is the one left in the pump, else the first non-empty source's. The tier must
 *     be allowed to move it (12-1, 12-5, 12-6), else nothing happens.</li>
 *     <li>No destination, or every destination full: the pump stops and pulls nothing (N7-4).</li>
 *     <li>A log-fueled pump needs a lit log: a lit log burns for its whole timer (100 ticks) whether
 *     or not the pump moves anything (N18-4); a new log is only lit by a cycle that can run (the
 *     N7-4 conditions hold).</li>
 *     <li>Pull up to one cycle's amount (N3-2, N6-1, N6-3) and push it ({@link PipeGrid}).</li>
 * </ol>
 * The manual pump runs a cycle per {@link #click}, at most every 20 ticks (N3-3); the log-fueled
 * pumps run one every 20 ticks from {@link #tick} (N6-1, N6-3); a cycle that cannot run is tried
 * again at the next interval. The game calls {@link #tick} once per game tick for every pump.
 * A pump's speed does not depend on how many sources it has (N18-1).
 *
 * <p>Wire (11-3, N11-3): from tier 2 up a wire signal switches the pump off; the game maps the
 * signal to {@link #setEnabled}.
 * <p>TODO(design): the manual pump's click cooldown is per pump (N3-3 as implemented since round
 * 2); whether it should be per player is held for the user.
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
    private FluidType lastPushedFluid;
    private int x;
    private int y;
    PipeGrid grid;
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
        if (grid == null || !grid.isPumpValveLinked(this, slot.direction)) {
            // No valve there now (its region is unloaded), or a stale saved slot of a cut link: a
            // valve is a source only while it is linked (N16-3).
            return null;
        }
        return grid.getValve(x + slot.direction.dx, y + slot.direction.dy).getTank();
    }

    /** The tanks the pump pulls from: never destinations of its own push. */
    List<TankStorage> getSourceTanks() {
        List<TankStorage> result = new ArrayList<>();
        for (FluidSource source : getSources()) {
            if (source instanceof TankStorage) {
                result.add((TankStorage) source);
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

    /** The fluid of the cycle: what is left in the pump, else the first non-empty source's (N19-1). */
    private FluidType cycleFluid() {
        if (getFluid() != null) {
            return getFluid();
        }
        for (FluidSource source : getSources()) {
            FluidType fluid = source.getSourceFluid();
            if (fluid != null && source.getAvailable(fluid) > 0) {
                return fluid;
            }
        }
        return null;
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

    void place(PipeGrid grid, int x, int y) {
        this.grid = grid;
        this.x = x;
        this.y = y;
    }

    // ------------------------------------------------------------------ running

    /**
     * One game tick. A lit log burns down every tick (N18-4). Log-fueled pumps run a cycle every
     * {@link PumpTier#getCycleTicks()} ticks while enabled; for the manual pump this only counts down
     * the click cooldown.
     */
    public PumpResult tick() {
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
        if (ticksSinceCycle < tier.getCycleTicks()) {
            return PumpResult.WAITING;
        }
        ticksSinceCycle = 0;
        return runCycle();
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

    private PumpResult runCycle() {
        if (grid == null) {
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
            return PumpResult.NO_SOURCE;
        }
        if (!tier.canPump(fluid)) {
            return PumpResult.FLUID_NOT_ALLOWED;
        }
        PipeGrid.PushPlan plan = grid.planPush(this, fluid);
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
        grid.onPumpPushing(this, fluid);
        PumpResult result = plan.run(getAmount());
        extract(fluid, Math.min(getAmount(), result.getMoved() + result.getLost()));
        return result;
    }

    @Override
    public String toString() {
        return "Pump[" + tier + " " + x + "," + y + ", sources " + sourceSlots + ", " + super.toString() + "]";
    }

}
