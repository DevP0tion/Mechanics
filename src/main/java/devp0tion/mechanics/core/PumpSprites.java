package devp0tion.mechanics.core;

/**
 * Which cell of its sheet a pump draws (N36-38, N36-39, N36-41). Pure selection logic; the game side
 * ({@code PumpObject}) reads the rotation and the form and draws the cell, placed and as the placement
 * preview (N36-40). The sheets are drawn by {@code tools/textures/draw_pipes_pumps.py}.
 *
 * <p>Sheet {@code objects/<stringID>.png}, 128x128: one column per output side, the object's
 * rotation (0 north, 1 east, 2 south, 3 west; N36-10), each its own drawing of the whole body turned
 * that way with its output and input ports (N36-38); row 0 the valve form, row 1 the ground form,
 * whose input goes down into the ground instead of out of the back (N36-39, N36-41). A cell is
 * {@link #CELL_WIDTH}x{@link #CELL_HEIGHT}: the lower 32 px on the tile, the upper 32 px above it,
 * like the tank parts, so it is drawn 32 px above the tile.
 */
public final class PumpSprites {

    /** A cell's width in px: one tile. */
    public static final int CELL_WIDTH = 32;
    /** A cell's height in px: the tile and 32 px above it. */
    public static final int CELL_HEIGHT = 64;

    private PumpSprites() {
    }

    /** The sheet column of a rotation: the output side, 0 north to 3 west (N36-38). */
    public static int column(int rotation) {
        return rotation & 3;
    }

    /** The sheet row of a form: 0 valve, 1 ground; no form is the ground form (N36-32). */
    public static int row(PumpForm form) {
        return form == PumpForm.VALVE ? 0 : 1;
    }

    /**
     * The cell to draw, as the texture section {@code {startX, endX, startY, endY}} in px (the order of
     * the engine's {@code section}).
     */
    public static int[] section(int rotation, PumpForm form) {
        int x = column(rotation) * CELL_WIDTH;
        int y = row(form) * CELL_HEIGHT;
        return new int[]{x, x + CELL_WIDTH, y, y + CELL_HEIGHT};
    }

}
