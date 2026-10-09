package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What one pump call ({@link Pump#tick} or {@link Pump#click}) did.
 */
public final class PumpResult {

    public enum Status {
        /** A cycle ran and moved fluid. */
        PUMPED,
        /** Not time for a cycle yet (cycle interval or click cooldown, N3-3, N6-1, N6-3). */
        WAITING,
        /** Switched off by wire (11-3, N11-3). */
        DISABLED,
        /**
         * The pump has nothing to pull (no source, the source is empty, or it holds another fluid than
         * the baseline and is dormant, N20-3); {@link #getDetail} says which. No log is lit.
         */
        NO_SOURCE,
        /**
         * The pump's tier cannot move the fluid: the non-empty source holds such a fluid (dormant,
         * N20-4), or the output cell does (12-1, 12-5, 12-6). No log is lit.
         */
        FLUID_NOT_ALLOWED,
        /**
         * No destination, or every destination is full: the pump stops and lights no log (N7-4, N36-56);
         * {@link #getDetail} says what is in front of it.
         */
        NO_DESTINATION,
        /** A log-fueled pump with no lit log and no log to light. */
        NO_FUEL
    }

    /**
     * Why a pump stopped (N36-44, N36-56), for {@link Status#NO_SOURCE} and {@link Status#NO_DESTINATION};
     * {@link #NONE} for every other status. The game's pump status text reads it.
     */
    public enum Detail {
        /** Nothing more to say (every status but {@link Status#NO_SOURCE} and {@link Status#NO_DESTINATION}). */
        NONE,
        /**
         * No source: no liquid under a ground pump (N36-15, N36-21), or no linked valve behind a valve
         * pump (N36-16), its region not loaded (N36-58) or its tank not recognized.
         */
        SOURCE_MISSING,
        /** The valve behind a valve pump is switched off by a wire signal (N27-4). */
        SOURCE_OFF,
        /** The source holds nothing to pull. */
        SOURCE_EMPTY,
        /** The source holds another fluid than the baseline: dormant (N20-3, N36-53). */
        SOURCE_OTHER_FLUID,
        /**
         * Nothing to push into: no basic pipe or linked valve in front (N36-1, N36-4), the front valve's
         * region not loaded (N36-50) or its tank not recognized, or a pipe in front that reaches no
         * destination.
         */
        DESTINATION_MISSING,
        /** Every destination is full (N7-4). */
        DESTINATION_FULL,
        /**
         * The pipe or the tank in front holds another fluid than the pump's (N36-46, N36-56): nothing
         * is pushed until it holds the same fluid or is empty (N36-27).
         */
        DESTINATION_OTHER_FLUID,
        /** The valve in front is switched off by a wire signal (N27-4, N36-56). */
        DESTINATION_OFF
    }

    static final PumpResult WAITING = new PumpResult(Status.WAITING, Detail.NONE);
    static final PumpResult DISABLED = new PumpResult(Status.DISABLED, Detail.NONE);
    static final PumpResult FLUID_NOT_ALLOWED = new PumpResult(Status.FLUID_NOT_ALLOWED, Detail.NONE);
    static final PumpResult NO_FUEL = new PumpResult(Status.NO_FUEL, Detail.NONE);
    private static final PumpResult[] NO_SOURCE = new PumpResult[Detail.values().length];
    private static final PumpResult[] NO_DESTINATION = new PumpResult[Detail.values().length];

    static {
        for (Detail detail : Detail.values()) {
            NO_SOURCE[detail.ordinal()] = new PumpResult(Status.NO_SOURCE, detail);
            NO_DESTINATION[detail.ordinal()] = new PumpResult(Status.NO_DESTINATION, detail);
        }
    }

    /** {@link Status#NO_SOURCE} with its detail. */
    static PumpResult noSource(Detail detail) {
        return NO_SOURCE[detail.ordinal()];
    }

    /** {@link Status#NO_DESTINATION} with its detail. */
    static PumpResult noDestination(Detail detail) {
        return NO_DESTINATION[detail.ordinal()];
    }

    private final Status status;
    private final Detail detail;
    private final FluidType fluid;
    private final int moved;
    private final int pipeFill;
    private final int pipesUpdated;
    private final int lost;
    private final Map<TankValve, Integer> delivered;
    private final List<PipeNode> broken;

    private PumpResult(Status status, Detail detail) {
        this(status, detail, null, 0, 0, 0, 0, Collections.<TankValve, Integer>emptyMap(),
                Collections.<PipeNode>emptyList());
    }

    PumpResult(Status status, FluidType fluid, int moved, int pipeFill, int pipesUpdated, int lost,
               Map<TankValve, Integer> delivered, List<PipeNode> broken) {
        this(status, Detail.NONE, fluid, moved, pipeFill, pipesUpdated, lost, delivered, broken);
    }

    private PumpResult(Status status, Detail detail, FluidType fluid, int moved, int pipeFill, int pipesUpdated,
                       int lost, Map<TankValve, Integer> delivered, List<PipeNode> broken) {
        this.status = status;
        this.detail = detail;
        this.fluid = fluid;
        this.moved = moved;
        this.pipeFill = pipeFill;
        this.pipesUpdated = pipesUpdated;
        this.lost = lost;
        this.delivered = Collections.unmodifiableMap(delivered);
        this.broken = Collections.unmodifiableList(broken);
    }

    public Status getStatus() {
        return status;
    }

    /** Why the pump stopped (N36-44): set for {@link Status#NO_SOURCE} and {@link Status#NO_DESTINATION}, else {@link Detail#NONE}. */
    public Detail getDetail() {
        return detail;
    }

    public boolean isPumped() {
        return status == Status.PUMPED;
    }

    /** The fluid moved, or {@code null}. */
    public FluidType getFluid() {
        return fluid;
    }

    /** Units that arrived somewhere: {@link #getPipeFill()} plus everything delivered. */
    public int getMoved() {
        return moved;
    }

    /** Units that went into filling pipes on the way. */
    public int getPipeFill() {
        return pipeFill;
    }

    /** Number of distinct pipes whose contents were written (only frontier pipes, N7-1). */
    public int getPipesUpdated() {
        return pipesUpdated;
    }

    /** Units lost with the shares headed into pipes that broke (N14-1). */
    public int getLost() {
        return lost;
    }

    /** Units delivered per destination valve, nearest first. */
    public Map<TankValve, Integer> getDelivered() {
        return delivered;
    }

    /** Units delivered to {@code valve} (0 if none). */
    public int getDelivered(TankValve valve) {
        Integer amount = delivered.get(valve);
        return amount == null ? 0 : amount;
    }

    /**
     * Pipes that broke this cycle (N12-4, N12-5, N14-1): already removed from the grid; the game
     * removes their objects without a drop.
     */
    public List<PipeNode> getBroken() {
        return broken;
    }

    @Override
    public String toString() {
        return "PumpResult[" + status + (detail == Detail.NONE ? "" : " " + detail) + (status == Status.PUMPED ? ", " + fluid + " " + moved + " (pipes " + pipeFill
                + ", updated " + pipesUpdated + ", lost " + lost + "), delivered " + delivered.values()
                + (broken.isEmpty() ? "" : ", broken " + broken) : "") + "]";
    }

}
