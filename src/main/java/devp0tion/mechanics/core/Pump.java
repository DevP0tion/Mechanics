package devp0tion.mechanics.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

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
 * <p>Sources (11-1, N11-4, N13-3 ②): the liquid tile under the pump, or the tank of a valve
 * attached to it. Placing a pump where it would have two or more sources is rejected
 * ({@link #canPlace}); when a second source appears later (a valve attached next to it), the pump
 * keeps its original source ({@link #updateSource}).
 * <p>TODO(design) N16-3: a valve attached next to a pump that already has a source starts with its
 * link to the pump disconnected, and several connected sources of the same fluid will be allowed
 * through the wrench (pipe round). Neither exists yet: the pump uses one source, chosen by
 * {@link #updateSource} from the sources the game reports as connected.
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

    /**
     * Placement check (N11-4): a pump may not be placed where it would have two or more sources
     * (on a liquid tile and attached to a valve, or attached to two valves).
     *
     * @param sourceCount the sources the pump would have at that spot (the liquid tile under it and
     *                    the tanks of the valves attached to it)
     */
    public static boolean canPlace(int sourceCount) {
        return sourceCount < 2;
    }

    /**
     * Updates the source from the sources connected to the pump now (the liquid tile under it and
     * the tanks of the valves attached to it; equal sources count once). First come, first served
     * (N13-3 ②):
     * <ul>
     *     <li>The current source is still there: the pump keeps it, even when another source has
     *     appeared since (a valve attached next to it later).</li>
     *     <li>Otherwise, exactly one source: the pump takes it. None: the pump has no source.</li>
     * </ul>
     * TODO(design) N16-3: the current source is gone (or the pump had none) and two or more sources
     * are connected, so neither came first (for example two attached valves whose tanks become valid
     * at once). Pulling from several connected sources of the same fluid comes with the wrench links
     * (pipe round); until then the pump has no source until only one is left.
     *
     * @return the source in use afterwards, or {@code null}
     */
    public FluidSource updateSource(Collection<? extends FluidSource> available) {
        if (source != null && available.contains(source)) {
            return source;
        }
        Set<FluidSource> distinct = new LinkedHashSet<FluidSource>(available);
        distinct.remove(null);
        source = distinct.size() == 1 ? distinct.iterator().next() : null;
        return source;
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
