package devp0tion.mechanics.core;

import java.util.Collections;
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
        /** Switched off by wire (11-3). */
        DISABLED,
        /** The pump has nothing to pull (no source, or the source is empty). No fuel used. */
        NO_SOURCE,
        /** The pump's tier cannot move the source's fluid (12-1, 12-5, 12-6). No fuel used. */
        FLUID_NOT_ALLOWED,
        /** No destination, or every destination is full: the pump stops and uses no fuel (N7-4). */
        NO_DESTINATION,
        /** A log-fueled pump with no burn time left and no log to burn. */
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
    private final Map<TankValve, Integer> delivered;

    private PumpResult(Status status) {
        this(status, null, 0, 0, 0, Collections.<TankValve, Integer>emptyMap());
    }

    PumpResult(Status status, FluidType fluid, int moved, int pipeFill, int pipesUpdated,
               Map<TankValve, Integer> delivered) {
        this.status = status;
        this.fluid = fluid;
        this.moved = moved;
        this.pipeFill = pipeFill;
        this.pipesUpdated = pipesUpdated;
        this.delivered = Collections.unmodifiableMap(delivered);
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

    /** Total units pushed out of the pump: {@link #getPipeFill()} plus everything delivered. */
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

    /** Units delivered per destination valve, nearest first. */
    public Map<TankValve, Integer> getDelivered() {
        return delivered;
    }

    /** Units delivered to {@code valve} (0 if none). */
    public int getDelivered(TankValve valve) {
        Integer amount = delivered.get(valve);
        return amount == null ? 0 : amount;
    }

    @Override
    public String toString() {
        return "PumpResult[" + status + (status == Status.PUMPED ? ", " + fluid + " " + moved + " (pipes " + pipeFill
                + ", updated " + pipesUpdated + "), delivered " + delivered.values() : "") + "]";
    }

}
