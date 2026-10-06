package devp0tion.mechanics.core;

/**
 * Pump tiers (11-3, 11-6).
 *
 * <p>A pump pulls fluid from the liquid tile under it (infinite, 11-1, 11-2, no depth limit 11-9)
 * or from the tank of a tank valve it is attached to (11-1). The fluid restriction of
 * {@link #canPump(FluidType)} applies to both sources (12-6).
 */
public enum PumpTier {

    /** Tier 1: operated by hand, pumps on every click (11-3). */
    MANUAL(1, Power.HAND_CLICK),
    /** Tier 2: burns fuel (11-3), any log (11-7). */
    FIRE(2, Power.LOG_FUEL),
    /** Tier 3: burns fuel (11-3), same fuel as the fire pump (11-8). */
    ADVANCED_FIRE(3, Power.LOG_FUEL),
    /**
     * Tier 4 and up: runs on electricity (11-3).
     * TODO(design): the electric pump and the electricity system are TODO (11-3); no values exist.
     */
    ELECTRIC(4, Power.ELECTRICITY);

    /** How a pump tier is powered. */
    public enum Power {
        HAND_CLICK,
        LOG_FUEL,
        ELECTRICITY
    }

    /** Fuel ingredient of the log-fueled pumps: any log (11-7, 11-8). */
    public static final String LOG_FUEL_INGREDIENT = "anylog";

    /** Fluid units the manual pump moves per click: 20 = 2 buckets (N3-2). */
    public static final int MANUAL_UNITS_PER_CLICK = 20;

    /** Minimum interval between manual pump clicks: 20 ticks = 1 second (N3-3). */
    public static final int MANUAL_CLICK_COOLDOWN_TICKS = 20;

    // The manual pump uses no fuel (N1-2).
    // TODO(design): FIRE and ADVANCED_FIRE pump amount, speed (cycle) and fuel use are undecided
    //  (numbers.md table 3).

    private final int tier;
    private final Power power;

    PumpTier(int tier, Power power) {
        this.tier = tier;
        this.power = power;
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

}
