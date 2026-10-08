package devp0tion.mechanics.core;

import java.util.Objects;

/**
 * One tile as seen by the tank validator ({@link TankStructure}).
 *
 * <ul>
 *     <li>The base layer object ({@link CellKind}).</li>
 *     <li>Mineral walls and valves carry a {@link MineralTier} (N13-5 ②: a valve has the tier of the
 *     mineral wall it was crafted from).</li>
 *     <li>Ownership (N13-3): a controller carries the tank it keeps ({@link #getKeptTank()}). A
 *     valve carries no owner: it may be in the border of two tanks, which are both recognized
 *     (N33-1, {@link TankValveRole}).</li>
 *     <li>Interior checks (N15-4): whether another object layer holds something
 *     ({@link #hasOtherLayerObject()}), whether the floor is a liquid tile ({@link #isLiquidFloor()})
 *     and whether it is the tank floor tile ({@link #isTankFloor()}). Only interior cells look at
 *     these; the border only looks at the base layer object.</li>
 *     <li>Natural growth (N20-1, N23-4, N29-5): whether the base layer object is of the game's grass
 *     kind, placed by a player or not ({@link #isNaturalGrowth()}), which the judgment of an active
 *     tank breaks instead of letting it invalidate the tank ({@link TankJudgment}).</li>
 * </ul>
 *
 * <p>TODO(design): the tank floor tile element itself (name and special function) is undecided
 * (5-6, 8-8). Callers supply {@code tankFloor} directly until it exists.
 */
public final class TankCell {

    private final CellKind kind;
    private final MineralTier mineral;
    private final TankBounds keptTank;
    private final boolean tankFloor;
    private final boolean liquidFloor;
    private final boolean otherLayerObject;
    private final boolean naturalGrowth;

    private TankCell(CellKind kind, MineralTier mineral, TankBounds keptTank,
                     boolean tankFloor, boolean liquidFloor, boolean otherLayerObject) {
        this(kind, mineral, keptTank, tankFloor, liquidFloor, otherLayerObject, false);
    }

    private TankCell(CellKind kind, MineralTier mineral, TankBounds keptTank,
                     boolean tankFloor, boolean liquidFloor, boolean otherLayerObject, boolean naturalGrowth) {
        this.kind = kind;
        this.mineral = mineral;
        this.keptTank = keptTank;
        this.tankFloor = tankFloor;
        this.liquidFloor = liquidFloor;
        this.otherLayerObject = otherLayerObject;
        this.naturalGrowth = naturalGrowth;
    }

    /** A mineral wall of the given tier. */
    public static TankCell mineralWall(MineralTier tier) {
        return new TankCell(CellKind.MINERAL_WALL, Objects.requireNonNull(tier, "tier"), null,
                false, false, false);
    }

    /** A valve of the given tier (N13-5 ②). */
    public static TankCell valve(MineralTier tier) {
        return new TankCell(CellKind.VALVE, Objects.requireNonNull(tier, "tier"), null,
                false, false, false);
    }

    /** A controller that keeps no tank yet. */
    public static TankCell controller() {
        return controller(null);
    }

    /** A controller that keeps {@code keptTank}, or no tank when {@code null} (N13-3). */
    public static TankCell controller(TankBounds keptTank) {
        return new TankCell(CellKind.CONTROLLER, null, keptTank, false, false, false);
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
        return new TankCell(kind, null, null, false, false, false);
    }

    /** A copy of this cell with the given tank floor state. */
    public TankCell withTankFloor(boolean tankFloor) {
        return tankFloor == this.tankFloor ? this
                : new TankCell(kind, mineral, keptTank, tankFloor, liquidFloor, otherLayerObject, naturalGrowth);
    }

    /** A copy of this cell with the given liquid floor state (N15-4). */
    public TankCell withLiquidFloor(boolean liquidFloor) {
        return liquidFloor == this.liquidFloor ? this
                : new TankCell(kind, mineral, keptTank, tankFloor, liquidFloor, otherLayerObject, naturalGrowth);
    }

    /** A copy of this cell with the given "object on another layer" state (N15-4). */
    public TankCell withOtherLayerObject(boolean otherLayerObject) {
        return otherLayerObject == this.otherLayerObject ? this
                : new TankCell(kind, mineral, keptTank, tankFloor, liquidFloor, otherLayerObject, naturalGrowth);
    }

    /**
     * A copy of this cell with the given natural growth state (N20-1, N23-4): whether the base
     * layer object is natural growth (N29-5: of the grass kind). Only meaningful for
     * {@link CellKind#OTHER}: any other kind is never natural growth, and the flag is ignored there.
     */
    public TankCell withNaturalGrowth(boolean naturalGrowth) {
        boolean value = naturalGrowth && kind == CellKind.OTHER;
        return value == this.naturalGrowth ? this
                : new TankCell(kind, mineral, keptTank, tankFloor, liquidFloor, otherLayerObject, value);
    }

    /**
     * This cell with its natural growth broken (N23-4): an empty base layer, the floor and the other
     * layers as they are. A cell without natural growth is returned unchanged.
     */
    public TankCell withNaturalGrowthBroken() {
        return naturalGrowth ? new TankCell(CellKind.EMPTY, null, null, tankFloor, liquidFloor, otherLayerObject, false)
                : this;
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

    /**
     * Whether the base layer object is natural growth (N20-1, N29-5: grass and the like, flowers,
     * snow piles, cobwebs, placed by a player or not). The game adapter decides what counts.
     */
    public boolean isNaturalGrowth() {
        return naturalGrowth;
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
                && tankFloor == other.tankFloor
                && liquidFloor == other.liquidFloor && otherLayerObject == other.otherLayerObject
                && naturalGrowth == other.naturalGrowth;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, mineral, keptTank, tankFloor, liquidFloor, otherLayerObject, naturalGrowth);
    }

    @Override
    public String toString() {
        return kind + (mineral != null ? "(" + mineral + ")" : "")
                + (keptTank != null ? "[keeps " + keptTank + "]" : "")
                + (tankFloor ? "+tankFloor" : "") + (liquidFloor ? "+liquidFloor" : "")
                + (otherLayerObject ? "+otherLayer" : "") + (naturalGrowth ? "+naturalGrowth" : "");
    }

}
