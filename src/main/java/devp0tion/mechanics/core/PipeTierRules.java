package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-tier pipe transport conditions (9-2, numbers.md table 2). Pipe tiers follow
 * {@link MineralTier} (9-11).
 *
 * <ul>
 *     <li>Transport amount (운송량): what one pipe cell of the tier holds (N7-3, N12-3), and the most
 *     that may flow through it per pump cycle, so the lowest amount on a path caps that path
 *     (N14-2); pumps pushing through the same pipes share it (N18-1). It is 40 x the tier's tank
 *     wall multiplier (N4-2), the capacity of one tank cell of the tier (N4-4), anchored at iron 80
 *     (N19-8, N32-2).</li>
 *     <li>Maximum temperature, transportable kinds and state: judged when fluid reaches a new pipe,
 *     against the lowest tier of the network it joins (N12-4, N14-1). The kinds follow the pump
 *     recipes' materials for now (N32-5); a fluid outside them breaks the newly reached pipe.</li>
 * </ul>
 * There is no movement speed (N32-1): filling empty pipes is limited only by the transport amount
 * as the per-cycle cap (N14-2). The cap equals a cell's capacity, so a pipe filled from empty uses
 * its whole cap and a path's fluid enters at most one empty pipe per cycle; a separate speed of one
 * pipe per cycle or more never bound. The per-tier speed (N25-2, N28-11), its unit of pipes per
 * cycle (N28-7), its mixed-tier rule (N28-10), what it held back in the pump (N28-9) and the
 * exemption of full stretches from it (N25-3) are discarded by N32-1; full stretches simply pass
 * within the cycle up to the cap.
 *
 * <p>The game's values are the one table {@link Row}; tests implement this interface with their
 * own values.
 */
public interface PipeTierRules {

    /** Fluid units one pipe cell of this tier holds, and the most that flows through it per cycle. */
    int getTransportAmount(MineralTier tier);

    /**
     * Whether a network whose lowest tier is {@code lowestTier} may carry {@code fluid} (temperature,
     * kind, state, N12-4). The default is the table's ({@link Row#carries}).
     */
    default boolean canCarry(MineralTier lowestTier, FluidType fluid) {
        return Row.of(lowestTier).carries(fluid);
    }

    /** The game's rules: the table {@link Row}. */
    PipeTierRules TABLE = Row::transportAmountOf;

    /** Pipe states (9-2). Gases are out of scope (12-3, TODO). */
    enum State {
        LIQUID
    }

    /**
     * The pipe tier table (numbers.md table 2), one row per tier.
     *
     * <p>Decided: the transport amount (운송량) follows the tank wall multipliers (N4-2), anchored at
     * iron 80, the throughput of four fire pumps (20 per cycle x 4, N19-8): 40 x the multiplier,
     * the capacity of one tank cell of the tier ({@link TankStructure#BASE_CAPACITY_PER_CELL} x
     * {@link MineralTier#getCapacityMultiplier}, N4-4) (N32-2).
     * <p>Provisional (N32-5, "for now"): the transportable kinds follow the highest material of the
     * pump recipes (11-4, N9-3), the fire pump counting as iron and the advanced fire pump as
     * demonic, so a tier carries what that pump moves (12-1, {@link PumpTier#canPump}): copper water
     * only ({@link #WATER}, the manual pump's fluids), iron and gold up to lava
     * ({@link #WATER_AND_LAVA}, the fire pump's), demonic and up every fluid, crude oil included
     * ({@link #ALL}, the advanced fire pump's). A fluid outside the kinds of the network's lowest
     * tier breaks the pipe it newly reaches (N12-4, N12-5, N14-1).
     * <p>TODO(design): the maximum temperature and the state are undecided and only provisional
     * placeholders, not a curve: the maximum temperature has no limit ({@link #NO_TEMPERATURE_LIMIT},
     * the fluid temperatures are undecided too, 12-4, N32-3); the state is liquid (gases are TODO,
     * 12-3).
     */
    enum Row {

        //             transport amount  max temperature        transportable kinds  state
        COPPER(          40,             NO_TEMPERATURE_LIMIT,  WATER,               State.LIQUID),
        IRON(            80,             NO_TEMPERATURE_LIMIT,  WATER_AND_LAVA,      State.LIQUID),
        GOLD(           120,             NO_TEMPERATURE_LIMIT,  WATER_AND_LAVA,      State.LIQUID),
        DEMONIC(        200,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID),
        IVY(            320,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID),
        TUNGSTEN(       480,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID),
        GLACIAL(        680,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID),
        MYCELIUM(       920,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID),
        ANCIENTFOSSIL( 1200,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID),
        NIGHTSTEEL(    1520,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID),
        SPIDERITE(     1880,             NO_TEMPERATURE_LIMIT,  ALL,                 State.LIQUID);

        // Sources: transport amount N32-2 (40 x the wall multiplier of N4-2, iron 80 of N19-8);
        // transportable kinds N32-5 (provisional: the pump recipes' highest material, fire pump =
        // iron, advanced fire pump = demonic).
        // Max temperature and state: TODO(design), see the enum comment (N32-3).

        private final int transportAmount;
        private final int maxTemperature;
        private final Set<FluidType> kinds;
        private final State state;

        Row(int transportAmount, int maxTemperature, Set<FluidType> kinds, State state) {
            this.transportAmount = transportAmount;
            this.maxTemperature = maxTemperature;
            this.kinds = kinds;
            this.state = state;
        }

        public static Row of(MineralTier tier) {
            return values()[tier.ordinal()];
        }

        static int transportAmountOf(MineralTier tier) {
            return of(tier).transportAmount;
        }

        public int getTransportAmount() {
            return transportAmount;
        }

        /** The maximum fluid temperature, or {@link #NO_TEMPERATURE_LIMIT}. */
        public int getMaxTemperature() {
            return maxTemperature;
        }

        public Set<FluidType> getKinds() {
            return kinds;
        }

        public State getState() {
            return state;
        }

        /**
         * Whether this tier's conditions allow the fluid: its kind is listed and its state matches.
         * The temperature check needs the fluid temperatures, which are undecided (12-4); it is
         * skipped while the limit is {@link #NO_TEMPERATURE_LIMIT}.
         */
        public boolean carries(FluidType fluid) {
            return fluid != null && kinds.contains(fluid) && state == State.LIQUID;
        }
    }

    // Placeholder markers of the table (interface fields are constants; declared after use is fine
    // for enum constructor arguments that are compile-time constants).

    /** TODO(design): provisional "no maximum temperature" (fluid temperatures are undecided, 12-4, N32-3). */
    int NO_TEMPERATURE_LIMIT = Integer.MAX_VALUE;

    /** Water only, seawater and freshwater: the manual pump's fluids, carried by copper (N32-5). */
    Set<FluidType> WATER = Collections.unmodifiableSet(EnumSet.of(FluidType.SEAWATER, FluidType.FRESHWATER));

    /** Water and lava: the fire pump's fluids, carried by iron and gold (N32-5). */
    Set<FluidType> WATER_AND_LAVA = Collections.unmodifiableSet(
            EnumSet.of(FluidType.SEAWATER, FluidType.FRESHWATER, FluidType.LAVA));

    /** Every fluid kind, crude oil included: the advanced fire pump's, carried from demonic up (N32-5). */
    Set<FluidType> ALL = Collections.unmodifiableSet(EnumSet.allOf(FluidType.class));

}
