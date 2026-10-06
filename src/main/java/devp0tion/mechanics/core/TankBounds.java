package devp0tion.mechanics.core;

/**
 * The outer rectangle of a tank candidate (border included), in tile coordinates.
 */
public final class TankBounds {

    public final int x;
    public final int y;
    public final int outerWidth;
    public final int outerHeight;

    public TankBounds(int x, int y, int outerWidth, int outerHeight) {
        this.x = x;
        this.y = y;
        this.outerWidth = outerWidth;
        this.outerHeight = outerHeight;
    }

    public int getInteriorWidth() {
        return outerWidth - 2;
    }

    public int getInteriorHeight() {
        return outerHeight - 2;
    }

    public int getInteriorCellCount() {
        return Math.max(0, getInteriorWidth()) * Math.max(0, getInteriorHeight());
    }

    public int getMaxX() {
        return x + outerWidth - 1;
    }

    public int getMaxY() {
        return y + outerHeight - 1;
    }

    public boolean contains(int tileX, int tileY) {
        return tileX >= x && tileX <= getMaxX() && tileY >= y && tileY <= getMaxY();
    }

    public boolean isOnBorder(int tileX, int tileY) {
        return contains(tileX, tileY) && (tileX == x || tileX == getMaxX() || tileY == y || tileY == getMaxY());
    }

    public boolean isCorner(int tileX, int tileY) {
        return (tileX == x || tileX == getMaxX()) && (tileY == y || tileY == getMaxY());
    }

    public boolean isInterior(int tileX, int tileY) {
        return contains(tileX, tileY) && !isOnBorder(tileX, tileY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TankBounds)) {
            return false;
        }
        TankBounds other = (TankBounds) o;
        return x == other.x && y == other.y && outerWidth == other.outerWidth && outerHeight == other.outerHeight;
    }

    @Override
    public int hashCode() {
        return ((x * 31 + y) * 31 + outerWidth) * 31 + outerHeight;
    }

    @Override
    public String toString() {
        return "TankBounds[x=" + x + ", y=" + y + ", outer=" + outerWidth + "x" + outerHeight + "]";
    }

}
