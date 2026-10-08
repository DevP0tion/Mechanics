package devp0tion.mechanics.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link GlassBlockSprites}: the glass ceiling's joined pane with its rim (N34-1, N34-2, N34-3)
 * and the glass wall's port of the vanilla wall quarters (N34-5).
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

    private static String wall(String[] layout, int x, int y) {
        return GlassBlockSprites.wallPieces(neighbours(layout, x, y), false, false).toString();
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

    public static void testVerticalRun() {
        Check.equal("[(0,2)@(0,0), (0,1)@(0,16) light(0,1), (3,2)@(16,0) light(1,0), (3,1)@(16,16) light(1,1)]",
                wall(new String[]{"#", "#", "#"}, 0, 1));
    }

    public static void testAllJoinedFastPath() {
        Check.equal("[(1,1)@(0,-16) light(0,-1) fast, (2,1)@(16,-16) light(1,-1) fast, (1,2)@(0,0) fast, "
                        + "(2,2)@(16,0) light(1,0) fast]",
                wall(new String[]{"###", "###", "###"}, 1, 1));
    }

    public static void testBlockCorner() {
        // Top-left of a 2x2 block: roof top edge, the left roof edge down, joined to the right.
        Check.equal("[(0,0)@(0,-16) light(0,-1) fades, (1,0)@(16,-16) light(1,-1) fades, (0,2)@(0,0), "
                        + "(0,1)@(0,16) light(0,1), (2,2)@(16,0) light(1,0), (2,1)@(16,16) light(1,1)]",
                wall(new String[]{"##", "##"}, 0, 0));
    }

    public static void testRoofBesideAJoinedFrontFace() {
        // Left joined, bottom joined, bottom-left not: the left neighbour shows its front face.
        String pieces = wall(new String[]{"##", ".#"}, 1, 0);
        Check.isTrue(pieces.contains("(0,5)@(0,0)") && pieces.contains("(0,6)@(0,16)"), pieces);
        // Bottom-left joined, left not: the roof inner corner piece below.
        pieces = wall(new String[]{".#", "##"}, 1, 0);
        Check.isTrue(pieces.contains("(0,2)@(0,0)") && pieces.contains("(0,7)@(0,16)"), pieces);
    }

    public static void testWindowNeighbours() {
        boolean[] vertical = neighbours(new String[]{"#", "#", "#"}, 0, 1);
        Check.equal("[(0,1)@(0,-16) light(0,-1), (3,1)@(16,-16) light(1,1), (0,2)@(0,0), (0,1)@(0,16) light(0,1), "
                        + "(3,2)@(16,0) light(1,0), (3,1)@(16,16) light(1,1)]",
                GlassBlockSprites.wallPieces(vertical, true, false).toString(), "forceDrawTop");
        Check.equal("[(0,2)@(0,0), (3,2)@(16,0) light(1,0)]",
                GlassBlockSprites.wallPieces(vertical, false, true).toString(), "forceRemoveBot");
    }

    public static void testWallStaysOnTheVanillaSheetCellsForOneWallType() {
        for (int mask = 0; mask < 256; mask++) {
            for (int force = 0; force < 4; force++) {
                for (GlassBlockSprites.Piece p : GlassBlockSprites.wallPieces(bits(mask), (force & 1) != 0, (force & 2) != 0)) {
                    Check.isTrue(p.sheetX >= 0 && p.sheetX < 4 && p.sheetY >= 0 && p.sheetY < 8, "on the 4x8 sheet: " + p);
                    Check.isFalse(p.sheetY >= 5 && p.sheetX >= 2, "cells (2..3, 5..7) are for other wall types: " + p);
                    Check.isTrue(p.offsetY >= -16 && p.offsetY <= 16, "roof above, face on the tile: " + p);
                }
            }
        }
    }

    public static void testNeighbourCountChecked() {
        Check.throwsException(IllegalArgumentException.class, () -> GlassBlockSprites.ceilingPieces(new boolean[4]));
        Check.throwsException(IllegalArgumentException.class, () -> GlassBlockSprites.wallPieces(null, false, false));
    }

}
