package devp0tion.mechanics.core;

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
 * <h2>Output and form (N36)</h2>
 * A pump has an output side, its front ({@link #front}: the engine's placement rotation, N36-10),
 * and a form ({@link PumpForm}, chosen with the item, N36-6, N36-11), both kept until it is picked up
 * (N36-7, N36-12; a cheat that turns a placed pump is followed, N36-36). It links only on its front
 * and, as a valve pump, on its back ({@link #accepts}): it pushes only into the basic pipe in front
 * of it (N36-1) or, without pipes, into the tank of the valve in front of it (N36-4); its other sides
 * link to nothing, like a wall (N36-2, N36-5, N36-20).
 *
 * <h2>Its source (11-1, N36-3, N36-6, N36-15, N36-16, N36-20, N19-3, N20-3~N20-5)</h2>
 * One source at most, by its form (N36-6; the several sources of N16-3 and N17-1 are replaced, N36-8):
 * a ground pump pulls from the liquid tile under it ({@link LiquidTileSource}; on land it gives nothing
 * until the tile is liquid, N36-15, N36-21, N36-28), a valve pump from the tank of the valve linked
 * behind it ({@link PipeGrid#isPumpValveLinked}, N36-3; none without such a valve, N36-16, or while
 * the valve's region is not loaded, N36-58). The source of the other form is not linked, like a wall
 * (N36-20). A pump is placed wherever the base layer is free: no source, or a source of another fluid,
 * refuses nothing (N36-15, N36-16, N36-53).
 * <p>The pump pulls only the baseline fluid (N20-3). The baseline is the fluid in the basic pipe in
 * front of it, its output cell ({@link PipeGrid#getOutputFluids}), read per cell and never from the
 * network (N20-5); with an empty output cell, a valve in front or nothing there, it is the source's
 * fluid (N36-46, N36-53). A source holding another fluid, or a fluid the pump's tier cannot move
 * (12-6, N20-4), is dormant: it stays linked and nothing is pulled from it (N20-3; N36-19, N36-24 and
 * N36-26 as N36-53 sums them up).
 * A valve placed behind a valve pump later is its source at once (N36-17, replacing the cut start of
 * N16-3, N36-22), unless the pump's flag toward it was cut (N16-4, N36-23). A valve switched off by a
 * wire signal is no source while it is off: nothing is pulled through it (N27-4). A valve that is a
 * plain wall (N33-1) is no source while it is one and the source again once it is a valve (N35-1).
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

    private final PumpTier tier;
    /** The output side (N36-1): the engine's placement rotation (N36-10). */
    private Direction direction = Direction.NORTH;
    private PumpForm form = PumpForm.GROUND;
    private FluidSource tileSource;
    private FuelSupply fuel;
    private boolean enabled = true;
    private int burnTicksLeft;
    private int ticksSinceCycle;
    private int links = LinkFlags.ALL_OPEN;
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

    // ------------------------------------------------------------------ direction and form (N36)

    /** The output side (N36-1): the side it pushes out of, the engine's placement rotation (N36-10). */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Sets the output side before the pump is added to a grid (the game reads it from the object's
     * rotation); a placed pump turns through {@link PipeGrid#setPumpDirection} (N36-36).
     */
    public void setDirection(Direction direction) {
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    /** The form (N36-6): where it pulls from. */
    public PumpForm getForm() {
        return form;
    }

    /**
     * Sets the form before the pump is added to a grid (the game reads it from the item it was
     * placed with, N36-11); a placed pump changes it through {@link PipeGrid#setPumpForm}.
     */
    public void setForm(PumpForm form) {
        this.form = Objects.requireNonNull(form, "form");
    }

    /** The side it pushes out of (N36-1). */
    public Direction front() {
        return direction;
    }

    /** The side opposite its output: a valve pump pulls from the valve there (N36-3). */
    public Direction back() {
        return direction.opposite();
    }

    /**
     * Whether the pump links to a part of kind {@code neighbour} on its side {@code side}: in front a
     * basic pipe, its output cell (N36-1), or a tank valve, a destination without pipes (N36-4);
     * behind, for a valve pump only, a tank valve, its source (N36-3). Nothing else, like a wall: a
     * basic pipe on any other side (N36-2), a valve on either side (N36-5) or behind a ground pump
     * (N36-20).
     */
    public boolean accepts(Direction side, PipeGrid.Part neighbour) {
        if (side == front()) {
            return neighbour == PipeGrid.Part.BASIC_PIPE || neighbour == PipeGrid.Part.VALVE;
        }
        return side == back() && form == PumpForm.VALVE && neighbour == PipeGrid.Part.VALVE;
    }

    // ------------------------------------------------------------------ sources

    /** The liquid tile source (the game gives every ground pump one, N36-21), or {@code null}. */
    public FluidSource getTileSource() {
        return tileSource;
    }

    /** Sets the liquid tile under the pump (the game's {@link LiquidTileSource}); a valve pump ignores it (N36-20). */
    public void setTileSource(FluidSource tileSource) {
        this.tileSource = tileSource;
    }

    /**
     * The pump's source, at most one (N36-6): a ground pump's liquid tile, or the tank of the valve
     * linked behind a valve pump while that valve is on (N36-3, N27-4). A valve without a recognized
     * tank gives nothing.
     */
    public List<FluidSource> getSources() {
        if (form == PumpForm.GROUND) {
            return tileSource == null ? Collections.<FluidSource>emptyList() : Collections.singletonList(tileSource);
        }
        TankValve valve = backValve();
        // A valve switched off by a wire signal blocks pulling too: no source while it is off (N27-4).
        TankStorage tank = valve == null || !valve.isEnabled() ? null : valve.getTank();
        return tank == null ? Collections.<FluidSource>emptyList() : Collections.<FluidSource>singletonList(tank);
    }

    /**
     * The valve linked behind a valve pump (N36-3), or {@code null}: none there, its region not loaded
     * (N36-58), its link cut (N16-4, N36-23), a plain wall (N33-1, N35-1), or a ground pump (N36-20).
     */
    private TankValve backValve() {
        if (form != PumpForm.VALVE || host == null || !host.isPumpValveLinked(this, back())) {
            return null;
        }
        return host.getValve(x + back().dx, y + back().dy);
    }

    /**
     * The tank of the valve linked behind a valve pump: never a destination of its own push (N36-51).
     * A valve switched off by a wire signal (no source while it is off, N27-4) still counts here.
     */
    List<TankStorage> getSourceTanks() {
        TankValve valve = backValve();
        TankStorage tank = valve == null ? null : valve.getTank();
        return tank == null ? Collections.<TankStorage>emptyList() : Collections.singletonList(tank);
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
     * output cell, or, while it is empty or the pump has none (a valve in front, or nothing), the
     * source's fluid when it is not empty and the tier can move it (N20-4, N36-46, N36-53). A source of
     * any other fluid is dormant (N20-3). {@code null} when the source can give nothing.
     * Fluid left in the pump goes first (N27-3): when it has nowhere to go, the pump stays stopped
     * ({@link PumpResult.Status#NO_DESTINATION}) until a place for it appears.
     */
    FluidType cycleFluid() {
        if (getFluid() != null) {
            return getFluid();
        }
        Set<FluidType> outputs = host.getOutputFluids(this);
        if (!outputs.isEmpty()) {
            return outputs.iterator().next();
        }
        // No output cell, or an empty one: the first fluid that enters becomes the baseline (N20-5).
        return movableSourceFluid();
    }

    /** The fluid of the source when it is not empty and the tier can move it (N20-4), else {@code null}. */
    private FluidType movableSourceFluid() {
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

    /** Pulls up to {@code want} of {@code fluid} from the source. */
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
        // The liquid tile source searches its tiles once per cycle (N19-3, technical); a valve pump
        // does not use it (N36-20).
        LiquidTileSource tiles = form == PumpForm.GROUND && tileSource instanceof LiquidTileSource
                ? (LiquidTileSource) tileSource : null;
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
            // Nothing to pull: the source is missing or empty, or holds a fluid the tier cannot move (N20-4).
            return anySourceHasFluid() ? PumpResult.FLUID_NOT_ALLOWED : PumpResult.noSource(sourceDetail(null));
        }
        if (!tier.canPump(fluid)) {
            // The output cells hold a fluid the tier cannot move: every source is dormant (N20-4, N20-5).
            return PumpResult.FLUID_NOT_ALLOWED;
        }
        PushPlan plan = host.planPush(this, fluid);
        long acceptable = plan.simulate(getCapacity());
        if (acceptable == 0) {
            return PumpResult.noDestination(host.destinationDetail(this, fluid));
        }
        if (available(fluid) == 0) {
            return PumpResult.noSource(sourceDetail(fluid));
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

    /**
     * Why the source gives nothing (N36-44): {@code baseline} is the cycle's fluid, or {@code null}
     * when none could be chosen. A ground pump without a liquid tile under it, or a valve pump without
     * a linked valve behind it (or one whose tank is not recognized), has none (N36-15, N36-16,
     * N36-58); a valve switched off by a wire signal is off (N27-4); a source of another fluid than
     * the baseline is dormant (N20-3, N36-53); else it is empty.
     */
    private PumpResult.Detail sourceDetail(FluidType baseline) {
        FluidType held;
        if (form == PumpForm.GROUND) {
            held = storedFluidOf(tileSource);
            if (held == null) {
                return PumpResult.Detail.SOURCE_MISSING;
            }
        } else {
            TankValve valve = backValve();
            if (valve == null) {
                return PumpResult.Detail.SOURCE_MISSING;
            }
            if (!valve.isEnabled()) {
                return PumpResult.Detail.SOURCE_OFF;
            }
            TankStorage tank = valve.getTank();
            if (tank == null || !tank.isActive()) {
                return PumpResult.Detail.SOURCE_MISSING;
            }
            held = tank.getFluid();
            if (held == null) {
                return PumpResult.Detail.SOURCE_EMPTY;
            }
        }
        return baseline != null && held != baseline ? PumpResult.Detail.SOURCE_OTHER_FLUID
                : PumpResult.Detail.SOURCE_EMPTY;
    }

    @Override
    public String toString() {
        return "Pump[" + tier + " " + x + "," + y + " " + direction + " " + form + ", " + super.toString() + "]";
    }

}
