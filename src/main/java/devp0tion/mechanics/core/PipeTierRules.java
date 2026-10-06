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
 *     (N14-2); pumps pushing through the same pipes share it (N18-1).</li>
 *     <li>Maximum temperature, transportable kinds and state: judged when fluid reaches a new pipe,
 *     against the lowest tier of the network it joins (N12-4, N14-1).</li>
 * </ul>
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
     * <p>Decided: iron 운송량 = 80, the throughput of four fire pumps (20 per cycle x 4, N19-8).
     * <p>TODO(design): every other value is undecided and only a provisional placeholder, not a
     * curve: the other tiers' transport amount is iron's 80 ({@link #PROVISIONAL_TRANSPORT}); the
     * maximum temperature has no limit ({@link #NO_TEMPERATURE_LIMIT}, the fluid temperatures are
     * undecided too, 12-4); every tier carries every fluid kind; the state is liquid (gases are
     * TODO, 12-3). With these placeholders no pipe ever breaks in the game (N12-4); tests use
     * their own rules.
     */
    enum Row {

        //             transport amount        max temperature        kinds   state
        COPPER(        PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        IRON(          80,                     NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        GOLD(          PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        DEMONIC(       PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        IVY(           PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        TUNGSTEN(      PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        GLACIAL(       PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        MYCELIUM(      PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        ANCIENTFOSSIL( PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        NIGHTSTEEL(    PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID),
        SPIDERITE(     PROVISIONAL_TRANSPORT,  NO_TEMPERATURE_LIMIT,  ALL,    State.LIQUID);

        // Sources: iron transport amount N19-8. Everything else: TODO(design), see the enum comment.

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

    /** TODO(design): provisional transport amount of every tier but iron: iron's value (N19-8). */
    int PROVISIONAL_TRANSPORT = 80;

    /** TODO(design): provisional "no maximum temperature" (fluid temperatures are undecided, 12-4). */
    int NO_TEMPERATURE_LIMIT = Integer.MAX_VALUE;

    /** TODO(design): provisional "every fluid kind". */
    Set<FluidType> ALL = Collections.unmodifiableSet(EnumSet.allOf(FluidType.class));

}
