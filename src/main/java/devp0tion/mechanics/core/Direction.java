package devp0tion.mechanics.core;

/**
 * The four tile directions. Tile y grows downwards, as in the game.
 */
public enum Direction {

    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public Direction opposite() {
        return values()[(ordinal() + 2) % 4];
    }

}
