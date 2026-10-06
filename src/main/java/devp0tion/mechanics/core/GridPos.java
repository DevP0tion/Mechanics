package devp0tion.mechanics.core;

/**
 * A position on an integer grid: a tile (for example the controller a valve belongs to, N13-3) or a
 * region ({@link TankRegions}). Immutable.
 */
public final class GridPos {

    public final int x;
    public final int y;

    public GridPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GridPos)) {
            return false;
        }
        GridPos other = (GridPos) o;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return x * 31 + y;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

}
