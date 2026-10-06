package devp0tion.mechanics.core;

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
        /** The border has no controller (4-3). */
        NO_CONTROLLER,
        /** The border has more than one controller (4-3). */
        MULTIPLE_CONTROLLERS,
        /** An interior cell holds something other than glass or nothing (5-5, 5-11). */
        INVALID_INTERIOR_OBJECT,
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
    private final MineralTier lowestWallTier;
    private final int capacity;
    private final int controllerX;
    private final int controllerY;
    private final int valveCount;

    private TankValidation(TankBounds bounds, Reason reason, InteriorCondition interiorCondition,
                           MineralTier lowestWallTier, int capacity, int controllerX, int controllerY, int valveCount) {
        this.bounds = bounds;
        this.reason = reason;
        this.interiorCondition = interiorCondition;
        this.lowestWallTier = lowestWallTier;
        this.capacity = capacity;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.valveCount = valveCount;
    }

    static TankValidation invalid(TankBounds bounds, Reason reason) {
        if (reason == Reason.VALID) {
            throw new IllegalArgumentException("Use valid(...) for valid tanks");
        }
        return new TankValidation(bounds, reason, null, null, 0, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
    }

    static TankValidation valid(TankBounds bounds, InteriorCondition interiorCondition, MineralTier lowestWallTier,
                                int capacity, int controllerX, int controllerY, int valveCount) {
        return new TankValidation(bounds, Reason.VALID, interiorCondition, lowestWallTier, capacity,
                controllerX, controllerY, valveCount);
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

    /** Lowest tier among the border's mineral walls, or {@code null} if invalid. */
    public MineralTier getLowestWallTier() {
        return lowestWallTier;
    }

    /** Capacity in fluid units ({@link FluidUnits}), or 0 if invalid. */
    public int getCapacity() {
        return capacity;
    }

    /** Controller tile X; only meaningful when valid. */
    public int getControllerX() {
        return controllerX;
    }

    /** Controller tile Y; only meaningful when valid. */
    public int getControllerY() {
        return controllerY;
    }

    /** Number of valves in the border; 0 if invalid. */
    public int getValveCount() {
        return valveCount;
    }

    @Override
    public String toString() {
        if (!isValid()) {
            return "TankValidation[" + reason + ", " + bounds + "]";
        }
        return "TankValidation[VALID, " + bounds + ", " + interiorCondition + ", lowest=" + lowestWallTier
                + ", capacity=" + capacity + ", valves=" + valveCount + "]";
    }

}
