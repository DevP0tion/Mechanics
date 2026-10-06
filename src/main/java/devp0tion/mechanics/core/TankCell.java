package devp0tion.mechanics.core;

import java.util.Objects;

/**
 * One tile as seen by the tank validator: the object on it ({@link CellKind}, plus the
 * {@link MineralTier} for mineral walls) and whether its floor is the tank floor tile.
 *
 * <p>TODO(design): the tank floor tile element itself (name and special function) is undecided
 * (5-6, 8-8). Callers supply {@code tankFloor} directly until it exists.
 */
public final class TankCell {

    private final CellKind kind;
    private final MineralTier mineral;
    private final boolean tankFloor;

    private TankCell(CellKind kind, MineralTier mineral, boolean tankFloor) {
        this.kind = kind;
        this.mineral = mineral;
        this.tankFloor = tankFloor;
    }

    /** A mineral wall of the given tier. */
    public static TankCell mineralWall(MineralTier tier) {
        return new TankCell(CellKind.MINERAL_WALL, Objects.requireNonNull(tier, "tier"), false);
    }

    /** A cell of any kind except {@link CellKind#MINERAL_WALL} (use {@link #mineralWall}). */
    public static TankCell of(CellKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (kind == CellKind.MINERAL_WALL) {
            throw new IllegalArgumentException("Use TankCell.mineralWall(tier) for mineral walls");
        }
        return new TankCell(kind, null, false);
    }

    /** A copy of this cell with the given floor state. */
    public TankCell withTankFloor(boolean tankFloor) {
        return tankFloor == this.tankFloor ? this : new TankCell(kind, mineral, tankFloor);
    }

    public CellKind getKind() {
        return kind;
    }

    /** The wall's tier, or {@code null} if this is not a mineral wall. */
    public MineralTier getMineral() {
        return mineral;
    }

    /** Whether the floor tile under this cell is the tank floor tile. */
    public boolean isTankFloor() {
        return tankFloor;
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
        return kind == other.kind && mineral == other.mineral && tankFloor == other.tankFloor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, mineral, tankFloor);
    }

    @Override
    public String toString() {
        return kind + (mineral != null ? "(" + mineral + ")" : "") + (tankFloor ? "+tankFloor" : "");
    }

}
