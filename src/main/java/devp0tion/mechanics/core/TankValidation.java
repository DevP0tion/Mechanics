package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of checking one tank candidate rectangle with {@link TankStructure#validate}.
 */
public final class TankValidation {

    /** Why a candidate is (or is not) a multiblock tank. */
    public enum Reason {
        VALID,
        /** Interior smaller than 1x1 on an axis (4-1). */
        TOO_SMALL,
        /** Interior larger than 5x5 on an axis (4-2, 7-1). */
        TOO_LARGE,
        /** A border cell is not a mineral wall, controller or valve (5-7, 5-13). */
        INVALID_BORDER_CELL,
        /** A valve is on a corner (4-5). */
        VALVE_ON_CORNER,
        /**
         * The border has no controller of its own (4-3). A controller that keeps another tank does
         * not count (N13-3).
         */
        NO_CONTROLLER,
        /** The border has more than one controller of its own (4-3). */
        MULTIPLE_CONTROLLERS,
        /**
         * An interior cell holds something other than glass or nothing, on the base layer or on any
         * other object layer except the underground pipe layer (5-5, 5-11, N15-4).
         */
        INVALID_INTERIOR_OBJECT,
        /** An interior cell's floor is a liquid tile (N15-4). */
        LIQUID_FLOOR,
        /** Glass and empty cells are mixed without a full tank floor (5-5). */
        MIXED_INTERIOR
    }

    /** Which interior condition of 5-5 a valid tank meets. Checked in this order. */
    public enum InteriorCondition {
        /** (1) Every interior cell is glass. */
        ALL_GLASS,
        /** (2) The interior is completely empty. */
        ALL_EMPTY,
        /** (3) Every interior floor is the tank floor tile, with only glass on it (5-11). */
        TANK_FLOOR
    }

    private final TankBounds bounds;
    private final Reason reason;
    private final InteriorCondition interiorCondition;
    private final MineralTier lowestTier;
    private final int capacity;
    private final GridPos controller;
    private final List<GridPos> valves;

    private TankValidation(TankBounds bounds, Reason reason, InteriorCondition interiorCondition,
                           MineralTier lowestTier, int capacity, GridPos controller, List<GridPos> valves) {
        this.bounds = bounds;
        this.reason = reason;
        this.interiorCondition = interiorCondition;
        this.lowestTier = lowestTier;
        this.capacity = capacity;
        this.controller = controller;
        this.valves = valves;
    }

    static TankValidation invalid(TankBounds bounds, Reason reason) {
        if (reason == Reason.VALID) {
            throw new IllegalArgumentException("Use valid(...) for valid tanks");
        }
        return new TankValidation(bounds, reason, null, null, 0, null, Collections.<GridPos>emptyList());
    }

    static TankValidation valid(TankBounds bounds, InteriorCondition interiorCondition, MineralTier lowestTier,
                                int capacity, GridPos controller, List<GridPos> valves) {
        return new TankValidation(bounds, Reason.VALID, interiorCondition, lowestTier, capacity, controller,
                Collections.unmodifiableList(new ArrayList<>(valves)));
    }

    public boolean isValid() {
        return reason == Reason.VALID;
    }

    public TankBounds getBounds() {
        return bounds;
    }

    public Reason getReason() {
        return reason;
    }

    /** The interior condition met, or {@code null} if invalid. */
    public InteriorCondition getInteriorCondition() {
        return interiorCondition;
    }

    /**
     * The tier whose multiplier sets the capacity: the lowest among the border's mineral walls and
     * valves, one for the whole tank; the controller counts as the highest tier (N13-5). {@code null}
     * if invalid.
     */
    public MineralTier getLowestTier() {
        return lowestTier;
    }

    /** Capacity in fluid units ({@link FluidUnits}), or 0 if invalid. */
    public int getCapacity() {
        return capacity;
    }

    /** The tank's controller tile, or {@code null} if invalid. */
    public GridPos getController() {
        return controller;
    }

    /** Controller tile X; only meaningful when valid. */
    public int getControllerX() {
        return controller == null ? Integer.MIN_VALUE : controller.x;
    }

    /** Controller tile Y; only meaningful when valid. */
    public int getControllerY() {
        return controller == null ? Integer.MIN_VALUE : controller.y;
    }

    /**
     * The tiles of the tank's valves (all border valves), in reading order. Empty if invalid. A valve
     * in a wall shared with another recognized tank counts as a plain wall for both, though it is
     * listed here and its tier counts (N33-1, {@link TankValveRole}).
     */
    public List<GridPos> getValves() {
        return valves;
    }

    /** Number of valves in the border; 0 if invalid. */
    public int getValveCount() {
        return valves.size();
    }

    @Override
    public String toString() {
        if (!isValid()) {
            return "TankValidation[" + reason + ", " + bounds + "]";
        }
        return "TankValidation[VALID, " + bounds + ", " + interiorCondition + ", controller=" + controller
                + ", lowest=" + lowestTier + ", capacity=" + capacity + ", valves=" + valves.size() + "]";
    }

}
