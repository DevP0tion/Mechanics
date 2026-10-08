package devp0tion.mechanics.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link GlassBlockSprites}: the glass ceiling's joined pane with its rim (N34-1, N34-2, N34-3)
 * and the glass wall's vanilla wall quarters (N34-5), each drawn once (N34-7).
 */
final class GlassBlockSpritesTest {

    private static final int[][] OFFSETS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private GlassBlockSpritesTest() {
    }

    /** The neighbours of (x, y) among the cells of a layout ('#' = glass), in the adjacent order. */
    private static boolean[] neighbours(String[] layout, int x, int y) {
        boolean[] n = new boolean[8];
        for (int i = 0; i < 8; i++) {
            n[i] = isSet(layout, x + OFFSETS[i][0], y + OFFSETS[i][1]);
        }
        return n;
    }

    private static boolean isSet(String[] layout, int x, int y) {
        return y >= 0 && y < layout.length && x >= 0 && x < layout[y].length() && layout[y].charAt(x) == '#';
    }

    private static String ceiling(String[] layout, int x, int y) {
        return GlassBlockSprites.ceilingPieces(neighbours(layout, x, y)).toString();
    }

    private static boolean[] bits(int mask) {
        boolean[] n = new boolean[8];
        for (int i = 0; i < 8; i++) {
            n[i] = (mask & (1 << i)) != 0;
        }
        return n;
    }

    // ------------------------------------------------------------------ ceiling

    public static void testCeiling3x2TankIsOnePane() {
        String[] tank = {"###", "###"};
        // North row: rim on top (left end, joined, right end), no top border below it.
        Check.equal("[(4,3)@(0,-32), (5,2)@(16,-32), (0,2)@(0,-16), (5,0)@(16,-16), (0,3)@(0,0), (5,1)@(16,0)]",
                ceiling(tank, 0, 0));
        Check.equal("[(4,2)@(0,-32), (5,2)@(16,-32), (4,0)@(0,-16), (5,0)@(16,-16), (4,1)@(0,0), (5,1)@(16,0)]",
                ceiling(tank, 1, 0));
        Check.equal("[(4,2)@(0,-32), (5,3)@(16,-32), (4,0)@(0,-16), (1,2)@(16,-16), (4,1)@(0,0), (1,3)@(16,0)]",
                ceiling(tank, 2, 0));
        // South row: no rim, bottom border on the lower quarters.
        Check.equal("[(0,2)@(0,-16), (5,0)@(16,-16), (0,1)@(0,0), (3,1)@(16,0)]", ceiling(tank, 0, 1));
        Check.equal("[(4,0)@(0,-16), (5,0)@(16,-16), (2,1)@(0,0), (3,1)@(16,0)]", ceiling(tank, 1, 1));
        Check.equal("[(4,0)@(0,-16), (1,2)@(16,-16), (2,1)@(0,0), (1,1)@(16,0)]", ceiling(tank, 2, 1));
    }

    public static void testSingleCeilingGlassIsAClosedFrame() {
        Check.equal("[(4,3)@(0,-32), (5,3)@(16,-32), (0,2)@(0,-16), (1,2)@(16,-16), (0,1)@(0,0), (1,1)@(16,0)]",
                ceiling(new String[]{"#"}, 0, 0));
    }

    public static void testRimOnlyWithoutCeilingGlassToTheNorth() {
        Check.isTrue(GlassBlockSprites.hasRim(neighbours(new String[]{".", "#"}, 0, 1)), "nothing north: rim (N34-2)");
        Check.isFalse(GlassBlockSprites.hasRim(neighbours(new String[]{"#", "#"}, 0, 1)), "glass north: no rim");
        Check.isTrue(GlassBlockSprites.hasRim(neighbours(new String[]{"#.#", ".#."}, 1, 1)), "diagonals only: rim");
    }

    public static void testInnerCorner() {
        // Right and bottom glass, bottom-right missing: the lower right quarter is an inner corner.
        Check.equal("[(4,3)@(0,-32), (5,2)@(16,-32), (0,2)@(0,-16), (5,0)@(16,-16), (0,3)@(0,0), (3,3)@(16,0)]",
                ceiling(new String[]{"##", "#."}, 0, 0));
    }

    public static void testRaisedPaneMeetsDiagonalNeighbours() {
        // (1, 1) has no glass north, so its rim sits beside the pane of (0, 0): joined there.
        String[] layout = {"#.", ".#"};
        Check.equal("[(4,2)@(0,-32), (5,3)@(16,-32), (0,2)@(0,-16), (1,2)@(16,-16), (0,1)@(0,0), (1,1)@(16,0)]",
                ceiling(layout, 1, 1));
        // And the lower right quarter of (0, 0) sits beside that rim: joined, with a bottom border.
        Check.equal("[(4,3)@(0,-32), (5,3)@(16,-32), (0,2)@(0,-16), (1,2)@(16,-16), (0,1)@(0,0), (3,1)@(16,0)]",
                ceiling(layout, 0, 0));
        // An L: (1, 1) has glass to the left and top-left, its rim is joined to the left.
        Check.equal("[(4,2)@(0,-32), (5,3)@(16,-32), (4,0)@(0,-16), (1,2)@(16,-16), (2,1)@(0,0), (1,1)@(16,0)]",
                ceiling(new String[]{"#.", "##"}, 1, 1));
        // ... and (0, 0) of that L: its lower right quarter has the rim of (1, 1) beside it and
        // (0, 1) and (1, 1) below: inside.
        Check.equal("[(4,3)@(0,-32), (5,3)@(16,-32), (0,2)@(0,-16), (1,2)@(16,-16), (0,3)@(0,0), (5,1)@(16,0)]",
                ceiling(new String[]{"#.", "##"}, 0, 0));
    }

    public static void testCeilingNeverUsesTopBorderCellsAndNeverOverlaps() {
        Set<String> topBorderCells = new HashSet<>();
        for (String cell : new String[]{"0,0", "1,0", "2,0", "3,0", "2,2", "3,2"}) {
            topBorderCells.add(cell);
        }
        for (int mask = 0; mask < 256; mask++) {
            boolean[] n = bits(mask);
            List<GlassBlockSprites.Piece> pieces = GlassBlockSprites.ceilingPieces(n);
            Check.equal(GlassBlockSprites.hasRim(n) ? 6 : 4, pieces.size(), "pieces for " + mask);
            Set<String> positions = new HashSet<>();
            for (GlassBlockSprites.Piece p : pieces) {
                Check.isFalse(topBorderCells.contains(p.sheetX + "," + p.sheetY), "the rim is the top border: " + p);
                Check.isTrue(p.sheetX >= 0 && p.sheetX < 6 && p.sheetY >= 0 && p.sheetY < 4, "on the 6x4 sheet: " + p);
                Check.isTrue(p.sheetX % 2 == p.offsetX / 16, "left cells left, right cells right: " + p);
                Check.isTrue(p.offsetY >= -32 && p.offsetY <= 0, "between the rim and the tile's upper half: " + p);
                Check.isTrue(positions.add(p.offsetX + "," + p.offsetY), "one piece per position: " + pieces);
            }
        }
    }

    // ------------------------------------------------------------------ wall

    /** The wall pieces of glass (x, y) in a layout where every '#' is a glass wall. */
    private static String wall(String[] layout, int x, int y) {
        return GlassBlockSprites.wallPieces(neighbours(layout, x, y), false, false,
                isSet(layout, x, y - 1), isSet(layout, x, y + 1)).toString();
    }

    private static String vanilla(String[] layout, int x, int y) {
        return GlassBlockSprites.vanillaWallPieces(neighbours(layout, x, y), false, false).toString();
    }

    public static void testIsolatedGlassWall() {
        Check.equal("[(0,0)@(0,-16) light(0,-1) fades, (3,0)@(16,-16) light(1,-1) fades, (0,3)@(0,0), "
                        + "(0,4)@(0,16) light(0,1), (3,3)@(16,0) light(1,0), (3,4)@(16,16) light(1,1)]",
                wall(new String[]{"#"}, 0, 0));
    }

    public static void testHorizontalRun() {
        Check.equal("[(2,0)@(0,-16) light(0,-1) fades, (1,0)@(16,-16) light(1,-1) fades, (2,3)@(0,0), "
                        + "(2,4)@(0,16) light(0,1), (1,3)@(16,0) light(1,0), (1,4)@(16,16) light(1,1)]",
                wall(new String[]{"###"}, 1, 0));
    }

    public static void testVerticalRunSharesTheRowBetweenTiles() {
        String[] column = {"#", "#", "#"};
        // Vanilla: the roof below the tile (+16) is drawn by this tile...
        Check.equal("[(0,2)@(0,0), (0,1)@(0,16) light(0,1), (3,2)@(16,0) light(1,0), (3,1)@(16,16) light(1,1)]",
                vanilla(column, 0, 1));
        // ... N34-7: the row between two glass walls is drawn once, by the lower tile (-16).
        Check.equal("[(0,1)@(0,-16) light(0,-1), (3,1)@(16,-16) light(1,-1), (0,2)@(0,0), (3,2)@(16,0) light(1,0)]",
                wall(column, 0, 1));
    }

    public static void testAllJoinedFastPath() {
        Check.equal("[(1,1)@(0,-16) light(0,-1) fast, (2,1)@(16,-16) light(1,-1) fast, (1,2)@(0,0) fast, "
                        + "(2,2)@(16,0) light(1,0) fast]",
                wall(new String[]{"###", "###", "###"}, 1, 1));
    }

    public static void testBlockCorner() {
        String[] block = {"##", "##"};
        // Top-left of a 2x2 block: roof top edge, the roof's left edge; the row below is the lower tile's.
        Check.equal("[(0,0)@(0,-16) light(0,-1) fades, (1,0)@(16,-16) light(1,-1) fades, (0,2)@(0,0), "
                        + "(2,2)@(16,0) light(1,0)]",
                wall(block, 0, 0));
        // Bottom-left: draws that row with the sprites vanilla's top-left tile drew there, then its face.
        Check.equal("[(0,1)@(0,-16) light(0,-1), (2,1)@(16,-16) light(1,-1), (0,3)@(0,0), (0,4)@(0,16) light(0,1), "
                        + "(1,3)@(16,0) light(1,0), (1,4)@(16,16) light(1,1)]",
                wall(block, 0, 1));
        // Vanilla drew (2, 1) there twice: from the top-left tile (+16) and from the bottom-left tile (-16).
        Check.isTrue(vanilla(block, 0, 0).contains("(2,1)@(16,16)") && vanilla(block, 0, 1).contains("(2,1)@(16,-16)"),
                "vanilla overdraw");
    }

    public static void testRoofBesideAJoinedFrontFace() {
        // Left joined, bottom joined, bottom-left not: the left neighbour shows its front face.
        String[] layout = {"##", ".#"};
        Check.isTrue(wall(layout, 1, 0).contains("(0,5)@(0,0)"), wall(layout, 1, 0));
        Check.isTrue(wall(layout, 1, 1).contains("(0,6)@(0,-16)"), "drawn by the lower tile: " + wall(layout, 1, 1));
        // Bottom-left joined, left not: the roof inner corner piece, also from the lower tile.
        layout = new String[]{".#", "##"};
        Check.isTrue(wall(layout, 1, 0).contains("(0,2)@(0,0)"), wall(layout, 1, 0));
        Check.isTrue(wall(layout, 1, 1).contains("(0,7)@(0,-16)"), "drawn by the lower tile: " + wall(layout, 1, 1));
    }

    public static void testGlassBesideOtherBlocksKeepsVanillaRows() {
        // A rock (joined, not glass) below and above: each draws its own pieces, the glass keeps vanilla's.
        boolean[] joined = neighbours(new String[]{"#", "#", "#"}, 0, 1);
        Check.equal(GlassBlockSprites.vanillaWallPieces(joined, false, false),
                GlassBlockSprites.wallPieces(joined, false, false, false, false), "no glass above or below");
    }

    public static void testWindowNeighbours() {
        boolean[] vertical = neighbours(new String[]{"#", "#", "#"}, 0, 1);
        Check.equal("[(0,1)@(0,-16) light(0,-1), (3,1)@(16,-16) light(1,1), (0,2)@(0,0), (0,1)@(0,16) light(0,1), "
                        + "(3,2)@(16,0) light(1,0), (3,1)@(16,16) light(1,1)]",
                GlassBlockSprites.wallPieces(vertical, true, false, false, false).toString(), "forceDrawTop");
        Check.equal("[(0,2)@(0,0), (3,2)@(16,0) light(1,0)]",
                GlassBlockSprites.wallPieces(vertical, false, true, false, false).toString(), "forceRemoveBot");
    }

    public static void testNoQuarterTwiceWithinATile() {
        for (int mask = 0; mask < 256; mask++) {
            boolean[] joined = bits(mask);
            for (int flags = 0; flags < 16; flags++) {
                List<GlassBlockSprites.Piece> pieces = GlassBlockSprites.wallPieces(joined, (flags & 1) != 0,
                        (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
                Set<String> positions = new HashSet<>();
                for (GlassBlockSprites.Piece p : pieces) {
                    Check.isTrue(positions.add(p.offsetX + "," + p.offsetY), "one piece per quarter: " + mask + " " + pieces);
                    Check.isTrue(p.sheetX >= 0 && p.sheetX < 4 && p.sheetY >= 0 && p.sheetY < 8, "on the 4x8 sheet: " + p);
                    Check.isFalse(p.sheetY >= 5 && p.sheetX >= 2, "cells (2..3, 5..7) are for other wall types: " + p);
                    Check.isTrue(p.offsetY >= -16 && p.offsetY <= 16, "roof above, face on the tile: " + p);
                }
            }
        }
    }

    /**
     * Every layout of a 3x3 grid of glass walls, rocks (blocking the whole tile, not glass) and
     * empty tiles, and of a 4x4 grid of glass and empty tiles: no screen quarter is drawn by two
     * glass tiles, and the quarters drawn are vanilla's, with vanilla's sprites.
     */
    public static void testEachQuarterDrawnOnceAndAsVanilla() {
        int layouts = 0;
        for (int code = 0; code < 19683; code++) {
            int[] cells = new int[9];
            int c = code;
            for (int i = 0; i < 9; i++) {
                cells[i] = c % 3;
                c /= 3;
            }
            checkLayout(cells, 3, 3);
            layouts++;
        }
        for (int code = 0; code < 65536; code++) {
            int[] cells = new int[16];
            for (int i = 0; i < 16; i++) {
                cells[i] = (code >> i) & 1;
            }
            checkLayout(cells, 4, 4);
            layouts++;
        }
        Check.equal(19683 + 65536, layouts);
    }

    /** cells: 0 empty, 1 glass wall, 2 rock. */
    private static void checkLayout(int[] cells, int width, int height) {
        Map<String, String> drawn = new HashMap<>();
        Map<String, String> vanilla = new HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cell(cells, width, height, x, y) != 1) {
                    continue;
                }
                boolean[] joined = new boolean[8];
                for (int i = 0; i < 8; i++) {
                    joined[i] = cell(cells, width, height, x + OFFSETS[i][0], y + OFFSETS[i][1]) != 0;
                }
                boolean glassAbove = cell(cells, width, height, x, y - 1) == 1;
                boolean glassBelow = cell(cells, width, height, x, y + 1) == 1;
                for (GlassBlockSprites.Piece p : GlassBlockSprites.wallPieces(joined, false, false, glassAbove, glassBelow)) {
                    String slot = slot(x, y, p);
                    String previous = drawn.put(slot, p.sheetX + "," + p.sheetY);
                    Check.isNull(previous, "quarter " + slot + " drawn twice in " + Arrays.toString(cells));
                }
                for (GlassBlockSprites.Piece p : GlassBlockSprites.vanillaWallPieces(joined, false, false)) {
                    String sprite = p.sheetX + "," + p.sheetY;
                    String previous = vanilla.put(slot(x, y, p), sprite);
                    Check.isTrue(previous == null || previous.equals(sprite),
                            "vanilla draws one sprite per quarter: " + Arrays.toString(cells));
                }
            }
        }
        Check.equal(vanilla, drawn, "the same quarters and sprites as vanilla: " + Arrays.toString(cells));
    }

    private static int cell(int[] cells, int width, int height, int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : cells[y * width + x];
    }

    /** A screen quarter, in 16 px units. */
    private static String slot(int x, int y, GlassBlockSprites.Piece p) {
        return (x * 2 + p.offsetX / 16) + "," + (y * 2 + p.offsetY / 16);
    }

    public static void testNeighbourCountChecked() {
        Check.throwsException(IllegalArgumentException.class, () -> GlassBlockSprites.ceilingPieces(new boolean[4]));
        Check.throwsException(IllegalArgumentException.class,
                () -> GlassBlockSprites.wallPieces(null, false, false, false, false));
    }

}
