package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which 16 px quarter sprites a glass block draws, and where (N34-1, N34-2, N34-3, N34-5). Pure
 * selection logic; the game side ({@code GlassBlockObject}) looks up the neighbours, the light and
 * the textures. Both sheets are drawn by {@code tools/textures/draw_tank_parts.py}, which has a
 * Python copy of this selection for its previews.
 *
 * <p>Neighbour arrays follow the engine's adjacent order ({@code Level.adjacentGetters}):
 * {@link #TOP_LEFT}, {@link #TOP}, {@link #TOP_RIGHT}, {@link #LEFT}, {@link #RIGHT},
 * {@link #BOTTOM_LEFT}, {@link #BOTTOM}, {@link #BOTTOM_RIGHT}.
 *
 * <h2>Ceiling (inside a recognized tank)</h2>
 * The pane is drawn at wall-top height: 16 px above the tile (N34-1), so a tile's pane covers
 * {@code [drawY - 16, drawY + 16]}. A tile without a ceiling glass to its north also draws a 16 px
 * rim at {@code [drawY - 32, drawY - 16]} (N34-2), so the pane reaches the north wall's top edge.
 * Neighbouring ceiling glass joins into one pane with a border only on its outer edge (N34-3), in
 * the quarter scheme of the vanilla modular carpet. Sheet {@code objects/glassblock_ceiling.png},
 * 96x64, 6x4 cells of 16 px (cell = column, row):
 * <pre>
 *        col 0          col 1          col 2          col 3          col 4          col 5
 * row 0  corner TL      corner TR      top edge L     top edge R     inside TL      inside TR
 * row 1  corner BL      corner BR      bottom edge L  bottom edge R  inside BL      inside BR
 * row 2  left edge T    right edge T   inner TL       inner TR       rim L joined   rim R joined
 * row 3  left edge B    right edge B   inner BL       inner BR       rim L end      rim R end
 * </pre>
 * Columns 0-5 of rows 0-3 are the carpet layout (left quarters in even columns, right quarters in
 * odd ones; upper quarters in rows 0 and 2, lower ones in rows 1 and 3); the rim pieces sit in the
 * carpet's four spare cells. The rim is the pane's top border, so the pieces with a top border
 * (top edges, top corners, top inner corners: (0..3, 0), (2, 2), (3, 2)) are not used; the sheet
 * keeps them so it stays a complete carpet sheet.
 *
 * <p>Because the pane is raised by 16 px, a quarter's neighbours at its own height are not always
 * the orthogonal tiles: a lower quarter {@code [drawY, drawY + 16]} also meets the rim of the
 * bottom-left / bottom-right tile, and a rim meets the pane of the top-left / top-right tile. The
 * selection follows what is actually beside each quarter on screen. With only a diagonal
 * neighbour (a staircase of glass, possible on a tank floor, 5-5) the two panes' borders meet
 * corner to corner; the sheet has no extra corner piece for that. TODO(game): check how that
 * looks.
 *
 * <h2>Wall (outside a recognized tank)</h2>
 * Drawn like a vanilla wall: a port of {@code WallObject.addWallDrawOptions} for one wall type
 * ({@code isWall == sameWall == adj}), with its all-joined fast path ({@link #vanillaWallPieces}).
 * The glass wall is see-through (N34-7), so {@link #wallPieces} draws every screen quarter once,
 * where vanilla draws some twice; the result on screen is vanilla's. Sheet
 * {@code objects/glassblock_wall.png}, 64x128, the vanilla wall sheet layout (4x8 cells of 16 px):
 * row 0 roof top edge (col 0 corner TL, cols 1-2 edge, col 3 corner TR); rows 1 and 2 roof (col 0
 * left edge, cols 1-2 inside, col 3 right edge; row 1 is the phase drawn above the tile, row 2 the
 * tile's upper half); rows 3 and 4 the front face, upper and lower half; row 5/6 cols 0-1 the roof
 * beside a joined neighbour's front face; row 7 cols 0-1 roof inner corners. Cells (2..3, 5..7) are
 * only used by vanilla for a different wall type next to the wall and stay empty.
 */
public final class GlassBlockSprites {

    public static final int TOP_LEFT = 0;
    public static final int TOP = 1;
    public static final int TOP_RIGHT = 2;
    public static final int LEFT = 3;
    public static final int RIGHT = 4;
    public static final int BOTTOM_LEFT = 5;
    public static final int BOTTOM = 6;
    public static final int BOTTOM_RIGHT = 7;

    /** The size of a quarter sprite in px. */
    public static final int CELL = 16;

    /** The ceiling pane's offset above the tile in px: wall-top height (N34-1). */
    public static final int CEILING_RAISE = 16;

    private GlassBlockSprites() {
    }

    /** One quarter sprite to draw. */
    public static final class Piece {

        /** The sheet cell, in 16 px units. */
        public final int sheetX;
        public final int sheetY;
        /** Where it is drawn, in px from the tile's draw position. */
        public final int offsetX;
        public final int offsetY;
        /**
         * Walls only: the sprite coordinate the vanilla wall passes to its lighting
         * ({@code WallObject.applyLights} / {@code getAdvancedLight}); 0 for the ceiling.
         */
        public final int lightX;
        public final int lightY;
        /** Walls only: drawn with the wall's fade alpha (vanilla: the roof's top edge), else opaque. */
        public final boolean fades;
        /** Walls only: a piece of vanilla's all-joined fast path, always smooth-lit and opaque. */
        public final boolean fastPath;

        public Piece(int sheetX, int sheetY, int offsetX, int offsetY, int lightX, int lightY,
                     boolean fades, boolean fastPath) {
            this.sheetX = sheetX;
            this.sheetY = sheetY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.lightX = lightX;
            this.lightY = lightY;
            this.fades = fades;
            this.fastPath = fastPath;
        }

        static Piece ceiling(int sheetX, int sheetY, int offsetX, int offsetY) {
            return new Piece(sheetX, sheetY, offsetX, offsetY, 0, 0, false, false);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Piece)) {
                return false;
            }
            Piece p = (Piece) o;
            return sheetX == p.sheetX && sheetY == p.sheetY && offsetX == p.offsetX && offsetY == p.offsetY
                    && lightX == p.lightX && lightY == p.lightY && fades == p.fades && fastPath == p.fastPath;
        }

        @Override
        public int hashCode() {
            int h = sheetX;
            h = h * 31 + sheetY;
            h = h * 31 + offsetX;
            h = h * 31 + offsetY;
            h = h * 31 + lightX;
            h = h * 31 + lightY;
            h = h * 31 + (fades ? 1 : 0);
            return h * 31 + (fastPath ? 1 : 0);
        }

        @Override
        public String toString() {
            return "(" + sheetX + "," + sheetY + ")@(" + offsetX + "," + offsetY + ")"
                    + (lightX != 0 || lightY != 0 ? " light(" + lightX + "," + lightY + ")" : "")
                    + (fades ? " fades" : "") + (fastPath ? " fast" : "");
        }

    }

    // ------------------------------------------------------------------ ceiling

    /** Whether a ceiling glass draws the rim (N34-2): no ceiling glass to its north. */
    public static boolean hasRim(boolean[] ceiling) {
        return !ceiling[TOP];
    }

    /**
     * The ceiling pieces of a glass block inside a recognized tank.
     *
     * @param ceiling per neighbour (adjacent order): a glass block that is also drawn as a ceiling
     */
    public static List<Piece> ceilingPieces(boolean[] ceiling) {
        check(ceiling);
        boolean left = ceiling[LEFT];
        boolean right = ceiling[RIGHT];
        boolean bottom = ceiling[BOTTOM];
        boolean bottomLeft = ceiling[BOTTOM_LEFT];
        boolean bottomRight = ceiling[BOTTOM_RIGHT];
        List<Piece> pieces = new ArrayList<>(6);
        int rimY = -CEILING_RAISE - CELL;
        int upperY = -CEILING_RAISE;
        int lowerY = -CEILING_RAISE + CELL;
        if (hasRim(ceiling)) {
            // Beside the rim, at its height: a neighbour's rim, or the top-left / top-right tile's pane.
            boolean rimLeft = left || ceiling[TOP_LEFT];
            boolean rimRight = right || ceiling[TOP_RIGHT];
            pieces.add(Piece.ceiling(4, rimLeft ? 2 : 3, 0, rimY));
            pieces.add(Piece.ceiling(5, rimRight ? 2 : 3, CELL, rimY));
        }
        // Upper quarters: above them is always pane (the top tile's lower half, or the own rim), so
        // no top border; beside them only the orthogonal neighbour's pane reaches this height.
        pieces.add(left ? Piece.ceiling(4, 0, 0, upperY) : Piece.ceiling(0, 2, 0, upperY));
        pieces.add(right ? Piece.ceiling(5, 0, CELL, upperY) : Piece.ceiling(1, 2, CELL, upperY));
        // Lower quarters: beside them the orthogonal neighbour's pane or the diagonal neighbour's
        // rim; below them the bottom tile's pane; diagonally the bottom-left / bottom-right pane.
        pieces.add(lowerQuarter(left || bottomLeft, bottom, bottomLeft, false, lowerY));
        pieces.add(lowerQuarter(right || bottomRight, bottom, bottomRight, true, lowerY));
        return Collections.unmodifiableList(pieces);
    }

    private static Piece lowerQuarter(boolean side, boolean below, boolean diagonal, boolean rightSide, int offsetY) {
        int col;
        int row;
        if (side && below) {
            col = diagonal ? 4 : 2; // inside, or inner corner
            row = diagonal ? 1 : 3;
        } else if (side) {
            col = 2; // bottom edge
            row = 1;
        } else if (below) {
            col = 0; // side edge
            row = 3;
        } else {
            col = 0; // corner
            row = 1;
        }
        return Piece.ceiling(col + (rightSide ? 1 : 0), row, rightSide ? CELL : 0, offsetY);
    }

    // ------------------------------------------------------------------ wall

    /**
     * The wall pieces of a glass block outside a recognized tank (N34-5, N34-7): the vanilla wall's
     * quarters ({@link #vanillaWallPieces}), with every screen quarter drawn by exactly one tile,
     * because the glass wall is see-through and a quarter drawn twice would show darker.
     * <ul>
     *     <li>Within a tile, a quarter vanilla adds twice (the roof above the tile, when both sides
     *     are joined) is drawn once.</li>
     *     <li>Between two glass walls on top of each other, the quarter row between them (the upper
     *     tile's {@code +16}, the lower tile's {@code -16}) belongs to the lower tile: vanilla's
     *     upper tile draws it always and its lower tile again where its sides are joined. The upper
     *     tile leaves it out ({@code glassBelow}); the lower tile draws it with the sprites the upper
     *     tile would have used, which only depend on tiles both see ({@code glassAbove}).</li>
     * </ul>
     * The result on screen is vanilla's: the same sprite on every quarter vanilla covers. Toward a
     * joined block that is not glass (a wall, a rock), nothing changes: that block draws itself.
     *
     * @param joined         per neighbour (adjacent order): the glass joins toward it
     * @param forceDrawTop   the top neighbour is joined and a wall drawing its top (a window)
     * @param forceRemoveBot the bottom neighbour is joined and a wall drawing its top (a window)
     * @param glassAbove     the top neighbour is a glass block also drawn as a wall
     * @param glassBelow     the bottom neighbour is a glass block also drawn as a wall
     */
    public static List<Piece> wallPieces(boolean[] joined, boolean forceDrawTop, boolean forceRemoveBot,
                                         boolean glassAbove, boolean glassBelow) {
        List<Piece> vanilla = vanillaWallPieces(joined, forceDrawTop, forceRemoveBot);
        boolean all = vanilla.get(0).fastPath;
        boolean sharedAbove = glassAbove && joined[TOP] && !all;
        boolean sharedBelow = glassBelow && joined[BOTTOM];
        List<Piece> pieces = new ArrayList<>(8);
        if (sharedAbove) {
            // The upper glass wall's lower quarter row, as its bottom branch picks it: its left /
            // right and bottom-left / bottom-right are this tile's top-left / top-right and left / right.
            pieces.add(sharedRowQuarter(joined[TOP_LEFT], joined[LEFT], false));
            pieces.add(sharedRowQuarter(joined[TOP_RIGHT], joined[RIGHT], true));
        }
        for (Piece piece : vanilla) {
            if (sharedAbove && piece.offsetY < 0 || sharedBelow && piece.offsetY > 0) {
                continue;
            }
            boolean taken = false;
            for (Piece other : pieces) {
                taken |= other.offsetX == piece.offsetX && other.offsetY == piece.offsetY;
            }
            if (!taken) {
                pieces.add(piece);
            }
        }
        return Collections.unmodifiableList(pieces);
    }

    /**
     * One half of the quarter row between two glass walls on top of each other, drawn by the lower
     * one at {@code -16}: vanilla's bottom-branch {@code +16} piece of the upper one.
     *
     * @param upperSide the upper tile's left (right) neighbour is joined
     * @param lowerSide the lower tile's left (right) neighbour is joined
     */
    private static Piece sharedRowQuarter(boolean upperSide, boolean lowerSide, boolean rightSide) {
        int col;
        int row;
        if (upperSide) {
            col = lowerSide ? 1 : 0;  // roof inside, or the roof beside a joined front face
            row = lowerSide ? 1 : 6;
        } else {
            col = 0;                  // roof inner corner, or the roof's side edge
            row = lowerSide ? 7 : 1;
        }
        if (rightSide) {
            // (1, 1) -> (2, 1); (0, 6) -> (1, 6); (0, 7) -> (1, 7); (0, 1) -> (3, 1)
            col = upperSide ? col + 1 : (lowerSide ? 1 : 3);
        }
        return wall(col, row, rightSide ? 16 : 0, -16, rightSide ? 1 : 0, -1);
    }

    /**
     * {@code WallObject}'s quarter selection as vanilla draws it, with {@code adj}, {@code sameWall}
     * and {@code isWall} all {@code joined}, including its duplicates. The base of
     * {@link #wallPieces}.
     *
     * @param joined         per neighbour (adjacent order): the glass joins toward it
     * @param forceDrawTop   the top neighbour is joined and a wall drawing its top (a window)
     * @param forceRemoveBot the bottom neighbour is joined and a wall drawing its top (a window)
     */
    public static List<Piece> vanillaWallPieces(boolean[] joined, boolean forceDrawTop, boolean forceRemoveBot) {
        check(joined);
        List<Piece> pieces = new ArrayList<>(8);
        boolean all = true;
        for (boolean j : joined) {
            all &= j;
        }
        if (all) {
            pieces.add(new Piece(1, 1, 0, -16, 0, -1, false, true));
            pieces.add(new Piece(2, 1, 16, -16, 1, -1, false, true));
            pieces.add(new Piece(1, 2, 0, 0, 0, 0, false, true));
            pieces.add(new Piece(2, 2, 16, 0, 1, 0, false, true));
            return Collections.unmodifiableList(pieces);
        }
        boolean top = joined[TOP];
        boolean left = joined[LEFT];
        boolean right = joined[RIGHT];
        boolean botLeft = joined[BOTTOM_LEFT];
        boolean bot = joined[BOTTOM];
        boolean botRight = joined[BOTTOM_RIGHT];
        if (!top) {
            pieces.add(new Piece(left ? 2 : 0, 0, 0, -16, 0, -1, true, false));
            pieces.add(new Piece(right ? 1 : 3, 0, 16, -16, 1, -1, true, false));
        } else {
            boolean topLeft = joined[TOP_LEFT];
            boolean topRight = joined[TOP_RIGHT];
            // Vanilla: isWall[1] && (forceDrawTop || !sameWall[1]); with one wall type only forceDrawTop.
            if (forceDrawTop) {
                if (!left) {
                    pieces.add(wall(0, 1, 0, -16, 0, -1));
                } else if (!topLeft) {
                    pieces.add(wall(0, 7, 0, -16, 0, -1));
                }
                // Vanilla lights these right pieces with sprite coordinate (1, 1).
                if (!right) {
                    pieces.add(wall(3, 1, 16, -16, 1, 1));
                } else if (!topRight) {
                    pieces.add(wall(1, 7, 16, -16, 1, 1));
                }
            }
            if (left && (!bot || !botLeft) && topLeft) {
                if (right && topRight) {
                    pieces.add(wall(2, 1, 16, -16, 1, -1));
                }
                pieces.add(wall(1, 1, 0, -16, 0, -1));
            }
            if (right && (!bot || !botRight) && topRight) {
                pieces.add(wall(2, 1, 16, -16, 1, -1));
                if (left && topLeft) {
                    pieces.add(wall(1, 1, 0, -16, 0, -1));
                }
            }
        }
        if (bot) {
            if (left) {
                if (botLeft) {
                    pieces.add(wall(1, 2, 0, 0, 0, 0));
                    if (!forceRemoveBot) {
                        pieces.add(wall(1, 1, 0, 16, 0, 1));
                    }
                } else {
                    // Vanilla picks (3, 7) when a different wall is bottom-left; one type: (0, 5).
                    pieces.add(wall(0, 5, 0, 0, 0, 0));
                    if (!forceRemoveBot) {
                        pieces.add(wall(0, 6, 0, 16, 0, 1));
                    }
                }
            } else {
                pieces.add(wall(0, 2, 0, 0, 0, 0));
                if (!forceRemoveBot) {
                    pieces.add(wall(0, botLeft ? 7 : 1, 0, 16, 0, 1));
                }
            }
            if (right) {
                if (botRight) {
                    pieces.add(wall(2, 2, 16, 0, 1, 0));
                    if (!forceRemoveBot) {
                        pieces.add(wall(2, 1, 16, 16, 1, 1));
                    }
                } else {
                    pieces.add(wall(1, 5, 16, 0, 1, 0));
                    if (!forceRemoveBot) {
                        pieces.add(wall(1, 6, 16, 16, 1, 1));
                    }
                }
            } else {
                pieces.add(wall(3, 2, 16, 0, 1, 0));
                if (!forceRemoveBot) {
                    pieces.add(botRight ? wall(1, 7, 16, 16, 1, 1) : wall(3, 1, 16, 16, 1, 1));
                }
            }
        } else {
            // Vanilla picks (3, 5) / (2, 5) / (2, 6) / (3, 6) when a different wall is below.
            pieces.add(wall(left ? 2 : 0, 3, 0, 0, 0, 0));
            pieces.add(wall(left ? 2 : 0, 4, 0, 16, 0, 1));
            pieces.add(wall(right ? 1 : 3, 3, 16, 0, 1, 0));
            pieces.add(wall(right ? 1 : 3, 4, 16, 16, 1, 1));
        }
        return Collections.unmodifiableList(pieces);
    }

    private static Piece wall(int sheetX, int sheetY, int offsetX, int offsetY, int lightX, int lightY) {
        return new Piece(sheetX, sheetY, offsetX, offsetY, lightX, lightY, false, false);
    }

    private static void check(boolean[] neighbours) {
        if (neighbours == null || neighbours.length != 8) {
            throw new IllegalArgumentException("8 neighbours expected in the adjacent order");
        }
    }

}
