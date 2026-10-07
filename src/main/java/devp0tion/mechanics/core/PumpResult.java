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
         * The pump has nothing to pull (no source, every source is empty, or the only non-empty ones
         * hold another fluid than the baseline and are dormant, N20-3). No log is lit.
         */
        NO_SOURCE,
        /**
         * The pump's tier cannot move the fluid: every non-empty source holds such a fluid (dormant,
         * N20-4), or the output cells do (12-1, 12-5, 12-6). No log is lit.
         */
        FLUID_NOT_ALLOWED,
        /** No destination, or every destination is full: the pump stops and lights no log (N7-4). */
        NO_DESTINATION,
        /** A log-fueled pump with no lit log and no log to light. */
        NO_FUEL
    }

    static final PumpResult WAITING = new PumpResult(Status.WAITING);
    static final PumpResult DISABLED = new PumpResult(Status.DISABLED);
    static final PumpResult NO_SOURCE = new PumpResult(Status.NO_SOURCE);
    static final PumpResult FLUID_NOT_ALLOWED = new PumpResult(Status.FLUID_NOT_ALLOWED);
    static final PumpResult NO_DESTINATION = new PumpResult(Status.NO_DESTINATION);
    static final PumpResult NO_FUEL = new PumpResult(Status.NO_FUEL);

    private final Status status;
    private final FluidType fluid;
    private final int moved;
    private final int pipeFill;
    private final int pipesUpdated;
    private final int lost;
    private final Map<TankValve, Integer> delivered;
    private final List<PipeNode> broken;

    private PumpResult(Status status) {
        this(status, null, 0, 0, 0, 0, Collections.<TankValve, Integer>emptyMap(), Collections.<PipeNode>emptyList());
    }

    PumpResult(Status status, FluidType fluid, int moved, int pipeFill, int pipesUpdated, int lost,
               Map<TankValve, Integer> delivered, List<PipeNode> broken) {
        this.status = status;
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
        return "PumpResult[" + status + (status == Status.PUMPED ? ", " + fluid + " " + moved + " (pipes " + pipeFill
                + ", updated " + pipesUpdated + ", lost " + lost + "), delivered " + delivered.values()
                + (broken.isEmpty() ? "" : ", broken " + broken) : "") + "]";
    }

}
