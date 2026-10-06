package devp0tion.mechanics.core;

import java.util.Objects;

/**
 * A pump: the only thing that moves fluid (N7-1). It pulls from its source and pushes through the
 * pipe networks it is linked to ({@link PipeGrid}).
 *
 * <p>As a {@link LiquidStorage} the pump holds the fluid of the cycle in progress; its capacity is
 * one cycle's amount. It only pulls what its destinations can take, so the storage is normally
 * empty between cycles.
 *
 * <p>One cycle:
 * <ol>
 *     <li>The fluid is the source's (or what is left in the pump). The tier must be allowed to move
 *     it (12-1, 12-5, 12-6), else nothing happens.</li>
 *     <li>No destination, or every destination full: the pump stops, pulls nothing and uses no
 *     fuel (N7-4).</li>
 *     <li>A log-fueled pump needs one cycle of burn time; it burns a new log when it runs out
 *     (11-7, 11-8). Burn time is only used by cycles that move fluid: one log runs 100 ticks
 *     = 5 cycles (N6-2, N6-4).</li>
 *     <li>Pull up to one cycle's amount (N3-2, N6-1, N6-3) and push it ({@link PipeGrid}).</li>
 * </ol>
 * The manual pump runs a cycle per {@link #click}, at most every 20 ticks (N3-3); the log-fueled
 * pumps run one every 20 ticks from {@link #tick} (N6-1, N6-3); a cycle that cannot run is tried
 * again at the next interval. The game calls {@link #tick} once per game tick for every pump.
 *
 * <p>TODO(design): a pump standing on a liquid tile and attached to a valve (or attached to two
 * valves) has two sources; which one it uses is undecided. The game picks one for
 * {@link #setSource}.
 * <p>TODO(design): which wire signal state switches a pump off is undecided (11-3); the game maps
 * the signal to {@link #setEnabled}.
 */
public class Pump extends LiquidStorage {

    /** Where a log-fueled pump gets its logs; the game implements it over the pump's inventory. */
    public interface FuelSupply {
        /** Removes one log ({@link PumpTier#LOG_FUEL_INGREDIENT}); false if there is none. */
        boolean consumeLog();
    }

    private final PumpTier tier;
    private FluidSource source;
    private FuelSupply fuel;
    private boolean enabled = true;
    private int burnTicksLeft;
    private int ticksSinceCycle;
    private int x;
    private int y;
    PipeGrid grid;

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

    public FluidSource getSource() {
        return source;
    }

    /** The liquid tile under the pump, or the tank of the valve it is attached to (11-1). */
    public void setSource(FluidSource source) {
        this.source = source;
    }

    public void setFuelSupply(FuelSupply fuel) {
        this.fuel = fuel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Wire control (11-3), for tier 2 and up.
     *
     * @throws IllegalStateException for the manual pump, which has no wire control
     */
    public void setEnabled(boolean enabled) {
        if (!tier.isWireControllable()) {
            throw new IllegalStateException(tier + " pump has no wire control (11-3)");
        }
        this.enabled = enabled;
    }

    /** Burn time left from logs already burned, in ticks. */
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

    /**
     * One game tick. Log-fueled pumps run a cycle every {@link PumpTier#getCycleTicks()} ticks
     * while enabled; for the manual pump this only counts down the click cooldown.
     */
    public PumpResult tick() {
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
     * A click on the manual pump (11-3): one cycle, at most once per click cooldown (N3-3).
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
        FluidType fluid = getFluid() != null ? getFluid() : source == null ? null : source.getSourceFluid();
        if (fluid == null) {
            return PumpResult.NO_SOURCE;
        }
        if (!tier.canPump(fluid)) {
            return PumpResult.FLUID_NOT_ALLOWED;
        }
        PipeGrid.PushPlan plan = grid.planPush(this, fluid);
        long acceptable = plan.getAcceptable();
        if (acceptable == 0) {
            return PumpResult.NO_DESTINATION;
        }
        long available = (long) getAmount() + (source == null ? 0 : source.getAvailable(fluid));
        if (available == 0) {
            return PumpResult.NO_SOURCE;
        }
        if (tier.usesLogFuel() && !ensureBurnTime()) {
            return PumpResult.NO_FUEL;
        }
        int want = (int) Math.min(getCapacity(), acceptable) - getAmount();
        if (want > 0 && source != null) {
            int pulled = source.extract(fluid, Math.min(want, source.getAvailable(fluid)));
            insert(fluid, pulled);
        }
        PumpResult result = plan.push(getAmount());
        extract(fluid, result.getMoved());
        if (tier.usesLogFuel()) {
            burnTicksLeft -= tier.getCycleTicks();
        }
        return result;
    }

    /** Makes sure one cycle of burn time is available, burning logs as needed. */
    private boolean ensureBurnTime() {
        while (burnTicksLeft < tier.getCycleTicks()) {
            if (fuel == null || !fuel.consumeLog()) {
                return false;
            }
            burnTicksLeft += tier.getLogBurnTicks();
        }
        return true;
    }

}
