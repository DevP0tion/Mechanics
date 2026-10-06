package devp0tion.mechanics.core;

import java.util.Objects;

/**
 * One tile as seen by the tank validator ({@link TankStructure}).
 *
 * <ul>
 *     <li>The base layer object ({@link CellKind}).</li>
 *     <li>Mineral walls and valves carry a {@link MineralTier} (N13-5 ②: a valve has the tier of the
 *     mineral wall it was crafted from).</li>
 *     <li>Ownership (N13-3): a controller carries the tank it keeps ({@link #getKeptTank()}), a valve
 *     the controller it remembers belonging to ({@link #getValveOwner()}).</li>
 *     <li>Interior checks (N15-4): whether another object layer holds something
 *     ({@link #hasOtherLayerObject()}), whether the floor is a liquid tile ({@link #isLiquidFloor()})
 *     and whether it is the tank floor tile ({@link #isTankFloor()}). Only interior cells look at
 *     these; the border only looks at the base layer object.</li>
 * </ul>
 *
 * <p>TODO(design): the tank floor tile element itself (name and special function) is undecided
 * (5-6, 8-8). Callers supply {@code tankFloor} directly until it exists.
 */
public final class TankCell {

    private final CellKind kind;
    private final MineralTier mineral;
    private final TankBounds keptTank;
    private final GridPos valveOwner;
    private final boolean tankFloor;
    private final boolean liquidFloor;
    private final boolean otherLayerObject;

    private TankCell(CellKind kind, MineralTier mineral, TankBounds keptTank, GridPos valveOwner,
                     boolean tankFloor, boolean liquidFloor, boolean otherLayerObject) {
        this.kind = kind;
        this.mineral = mineral;
        this.keptTank = keptTank;
        this.valveOwner = valveOwner;
        this.tankFloor = tankFloor;
        this.liquidFloor = liquidFloor;
        this.otherLayerObject = otherLayerObject;
    }

    /** A mineral wall of the given tier. */
    public static TankCell mineralWall(MineralTier tier) {
        return new TankCell(CellKind.MINERAL_WALL, Objects.requireNonNull(tier, "tier"), null, null,
                false, false, false);
    }

    /** A valve of the given tier (N13-5 ②) that belongs to no tank yet. */
    public static TankCell valve(MineralTier tier) {
        return valve(tier, null);
    }

    /**
     * A valve of the given tier (N13-5 ②) that remembers belonging to the controller at
     * {@code owner}, or to none when {@code owner} is {@code null} (N13-3).
     */
    public static TankCell valve(MineralTier tier, GridPos owner) {
        return new TankCell(CellKind.VALVE, Objects.requireNonNull(tier, "tier"), null, owner,
                false, false, false);
    }

    /** A controller that keeps no tank yet. */
    public static TankCell controller() {
        return controller(null);
    }

    /** A controller that keeps {@code keptTank}, or no tank when {@code null} (N13-3). */
    public static TankCell controller(TankBounds keptTank) {
        return new TankCell(CellKind.CONTROLLER, null, keptTank, null, false, false, false);
    }

    /**
     * A cell of any kind except {@link CellKind#MINERAL_WALL} and {@link CellKind#VALVE}, which carry
     * a tier ({@link #mineralWall}, {@link #valve}). A controller made here keeps no tank.
     */
    public static TankCell of(CellKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (kind == CellKind.MINERAL_WALL) {
            throw new IllegalArgumentException("Use TankCell.mineralWall(tier) for mineral walls");
        }
        if (kind == CellKind.VALVE) {
            throw new IllegalArgumentException("Use TankCell.valve(tier) for valves");
        }
        return new TankCell(kind, null, null, null, false, false, false);
    }

    /** A copy of this cell with the given tank floor state. */
    public TankCell withTankFloor(boolean tankFloor) {
        return tankFloor == this.tankFloor ? this
                : new TankCell(kind, mineral, keptTank, valveOwner, tankFloor, liquidFloor, otherLayerObject);
    }

    /** A copy of this cell with the given liquid floor state (N15-4). */
    public TankCell withLiquidFloor(boolean liquidFloor) {
        return liquidFloor == this.liquidFloor ? this
                : new TankCell(kind, mineral, keptTank, valveOwner, tankFloor, liquidFloor, otherLayerObject);
    }

    /** A copy of this cell with the given "object on another layer" state (N15-4). */
    public TankCell withOtherLayerObject(boolean otherLayerObject) {
        return otherLayerObject == this.otherLayerObject ? this
                : new TankCell(kind, mineral, keptTank, valveOwner, tankFloor, liquidFloor, otherLayerObject);
    }

    public CellKind getKind() {
        return kind;
    }

    /**
     * The tier of a mineral wall or a valve (N13-5 ②), or {@code null} for any other cell. The
     * controller has no tier of its own: it counts as the highest ({@link TankStructure#CONTROLLER_TIER}).
     */
    public MineralTier getMineral() {
        return mineral;
    }

    /** For a controller: the tank it keeps (N13-3), or {@code null}. Always {@code null} otherwise. */
    public TankBounds getKeptTank() {
        return keptTank;
    }

    /**
     * For a valve: the controller it remembers belonging to (N13-3), or {@code null}. Whether that
     * controller still owns it is decided by {@link TankStructure#effectiveValveOwner}. Always
     * {@code null} for other cells.
     */
    public GridPos getValveOwner() {
        return valveOwner;
    }

    /** Whether the floor tile under this cell is the tank floor tile. */
    public boolean isTankFloor() {
        return tankFloor;
    }

    /** Whether the floor tile under this cell is a liquid tile (N15-4). */
    public boolean isLiquidFloor() {
        return liquidFloor;
    }

    /**
     * Whether an object layer other than the base layer holds something that counts for the interior
     * checks (N15-4). The underground pipe layer never counts; the game adapter leaves it out.
     */
    public boolean hasOtherLayerObject() {
        return otherLayerObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TankCell)) {
            return false;
        }
        TankCell other = (TankCell) o;
        return kind == other.kind && mineral == other.mineral && Objects.equals(keptTank, other.keptTank)
                && Objects.equals(valveOwner, other.valveOwner) && tankFloor == other.tankFloor
                && liquidFloor == other.liquidFloor && otherLayerObject == other.otherLayerObject;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, mineral, keptTank, valveOwner, tankFloor, liquidFloor, otherLayerObject);
    }

    @Override
    public String toString() {
        return kind + (mineral != null ? "(" + mineral + ")" : "")
                + (keptTank != null ? "[keeps " + keptTank + "]" : "")
                + (valveOwner != null ? "[owner " + valveOwner + "]" : "")
                + (tankFloor ? "+tankFloor" : "") + (liquidFloor ? "+liquidFloor" : "")
                + (otherLayerObject ? "+otherLayer" : "");
    }

}
