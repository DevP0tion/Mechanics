package devp0tion.mechanics.core;

/**
 * Pump tiers (11-3, 11-6) and their numbers (numbers.md table 3).
 *
 * <p>A pump pulls fluid from the liquid tile under it (infinite, 11-1, 11-2, no depth limit 11-9)
 * or from the tank of a tank valve it is attached to (11-1). The fluid restriction of
 * {@link #canPump(FluidType)} applies to both sources (12-6).
 *
 * <p>Every pump works in cycles: one cycle moves {@link #getUnitsPerCycle()} units. The manual
 * pump runs one cycle per click, at most once every {@link #getCycleTicks()} ticks; the log-fueled
 * pumps run one cycle every {@link #getCycleTicks()} ticks on their own.
 */
public enum PumpTier {

    //                                 units per  cycle   log burn
    //                                 cycle      ticks   ticks
    /** Tier 1: operated by hand, pumps on every click (11-3). No fuel (N1-2). */
    MANUAL(1, Power.HAND_CLICK,        20,        20,     Values.NONE),
    /** Tier 2: burns any log (11-3, 11-7). Same output as the manual pump, automatically (N6-1, N6-2). */
    FIRE(2, Power.LOG_FUEL,            20,        20,     100),
    /** Tier 3: burns the same fuel as the fire pump (11-8). Twice the fire pump per cycle (N6-3, N6-4). */
    ADVANCED_FIRE(3, Power.LOG_FUEL,   40,        20,     100),
    /**
     * Tier 4 and up: runs on electricity (11-3).
     * TODO(design): the electric pump and the electricity system are TODO (11-3); no values exist.
     */
    ELECTRIC(4, Power.ELECTRICITY, Values.UNDECIDED, Values.UNDECIDED, Values.NONE);

    // Sources:
    //   MANUAL: 20 units per click (N3-2), clicks at most every 20 ticks = 1 s (N3-3)
    //   FIRE: 20 units every 20 ticks (N6-1); one log runs it 5 s = 100 ticks (N6-2)
    //   ADVANCED_FIRE: 40 units every 20 ticks (N6-3); one log runs it 5 s = 100 ticks (N6-4)

    /** How a pump tier is powered. */
    public enum Power {
        HAND_CLICK,
        LOG_FUEL,
        ELECTRICITY
    }

    /** Fuel ingredient of the log-fueled pumps: any log (11-7, 11-8). */
    public static final String LOG_FUEL_INGREDIENT = "anylog";

    private final int tier;
    private final Power power;
    private final int unitsPerCycle;
    private final int cycleTicks;
    private final int logBurnTicks;

    PumpTier(int tier, Power power, int unitsPerCycle, int cycleTicks, int logBurnTicks) {
        this.tier = tier;
        this.power = power;
        this.unitsPerCycle = unitsPerCycle;
        this.cycleTicks = cycleTicks;
        this.logBurnTicks = logBurnTicks;
    }

    /** Tier number (11-3). */
    public int getTier() {
        return tier;
    }

    public Power getPower() {
        return power;
    }

    /** True if this pump burns logs as fuel (fire and advanced fire pumps, 11-7, 11-8). */
    public boolean usesLogFuel() {
        return power == Power.LOG_FUEL;
    }

    /** Pumps from tier 2 up can be switched on and off by wire (11-3). */
    public boolean isWireControllable() {
        return tier >= FIRE.tier;
    }

    /** False while this tier's numbers are undecided (the electric pump, 11-3). */
    public boolean hasDecidedValues() {
        return unitsPerCycle != Values.UNDECIDED;
    }

    /**
     * Fluid units moved per cycle (per click for the manual pump).
     *
     * @throws UnsupportedOperationException for {@link #ELECTRIC} (undecided)
     */
    public int getUnitsPerCycle() {
        requireDecided();
        return unitsPerCycle;
    }

    /**
     * Ticks per cycle: the automatic interval of the log-fueled pumps, the click cooldown of the
     * manual pump.
     *
     * @throws UnsupportedOperationException for {@link #ELECTRIC} (undecided)
     */
    public int getCycleTicks() {
        requireDecided();
        return cycleTicks;
    }

    /**
     * Ticks of operation one log gives (N6-2, N6-4).
     *
     * @throws IllegalStateException if this tier does not burn logs
     */
    public int getLogBurnTicks() {
        if (!usesLogFuel()) {
            throw new IllegalStateException(this + " does not burn logs");
        }
        return logBurnTicks;
    }

    /**
     * Fluid units one log moves when the pump runs without stopping:
     * 100 for the fire pump, 200 for the advanced fire pump (N6-2, N6-4).
     */
    public int getUnitsPerLog() {
        return getLogBurnTicks() / getCycleTicks() * getUnitsPerCycle();
    }

    /**
     * Whether this pump can move the given fluid (12-1, 12-5). The same restriction applies when
     * pumping from a tank (12-6).
     * <ul>
     *     <li>Manual pump: water only (seawater and freshwater).</li>
     *     <li>Fire pump: up to lava (water and lava).</li>
     *     <li>Advanced fire pump and up: every fluid.</li>
     * </ul>
     */
    public boolean canPump(FluidType fluid) {
        if (fluid == null) {
            return false;
        }
        if (tier >= ADVANCED_FIRE.tier) {
            return true;
        }
        if (tier >= FIRE.tier) {
            return fluid.isWater() || fluid == FluidType.LAVA;
        }
        return fluid.isWater();
    }

    private void requireDecided() {
        if (!hasDecidedValues()) {
            throw new UnsupportedOperationException(
                    "TODO(design): " + this + " pump values are undecided (11-3, numbers.md table 3)");
        }
    }

    // Marker values for the constant table above (nested: enum constant arguments cannot
    // reference the enum's own static fields).
    private static final class Values {
        /** Not applicable (no fuel). */
        static final int NONE = 0;
        /** Not decided yet. */
        static final int UNDECIDED = -1;
    }

}
