package devp0tion.mechanics.core;

import java.awt.Rectangle;
import java.util.List;

/** The basic pipe's collision shape (N31-10): a center square plus arms toward its linked sides. */
final class PipeShapeTest {

    private static final int NONE = -1;

    private PipeShapeTest() {
    }

    private static int[] neighbours(int north, int east, int south, int west) {
        return new int[]{north, east, south, west};
    }

    public static void testALonePipeIsTheCenterSquare() {
        int linked = PipeShape.linkedSides(LinkFlags.ALL_OPEN, neighbours(NONE, NONE, NONE, NONE));
        Check.equal(0, linked, "no neighbour: no linked side");
        List<Rectangle> shape = PipeShape.collision(linked);
        Check.equal(1, shape.size());
        Check.equal(new Rectangle(9, 9, 14, 14), shape.get(0), "the center square, not the full tile");
    }

    public static void testArmsTowardTheLinkedSides() {
        int linked = PipeShape.linkedSides(LinkFlags.ALL_OPEN,
                neighbours(NONE, LinkFlags.ALL_OPEN, NONE, LinkFlags.ALL_OPEN));
        Check.equal(LinkFlags.bit(Direction.EAST) | LinkFlags.bit(Direction.WEST), linked);
        List<Rectangle> shape = PipeShape.collision(linked);
        Check.equal(3, shape.size(), "hub, east, west");
        Check.isTrue(shape.contains(new Rectangle(23, 10, 9, 12)), "east arm to the tile edge");
        Check.isTrue(shape.contains(new Rectangle(0, 10, 9, 12)), "west arm to the tile edge");
    }

    public static void testEveryArmReachesItsEdgeAndTheCenter() {
        List<Rectangle> shape = PipeShape.collision(LinkFlags.SIDES);
        Check.equal(5, shape.size());
        Rectangle tile = new Rectangle(0, 0, 32, 32);
        for (Rectangle part : shape) {
            Check.isTrue(tile.contains(part), part + " inside the tile");
        }
        // A line across the tile through the middle meets no gap.
        for (int x = 0; x < 32; x++) {
            Check.isTrue(contains(shape, x, 16), "row " + x);
        }
        for (int y = 0; y < 32; y++) {
            Check.isTrue(contains(shape, 16, y), "column " + y);
        }
        Check.isFalse(contains(shape, 2, 2), "the corners stay open");
    }

    public static void testACutFlagOnEitherSideIsNoArm() {
        int ownCutNorth = LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.NORTH, false);
        int southCut = LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.SOUTH, false);
        Check.equal(0, PipeShape.linkedSides(ownCutNorth, neighbours(LinkFlags.ALL_OPEN, NONE, NONE, NONE)),
                "own flag cut (the wrench)");
        Check.equal(0, PipeShape.linkedSides(LinkFlags.ALL_OPEN, neighbours(southCut, NONE, NONE, NONE)),
                "the neighbour's flag toward it cut");
        Check.equal(LinkFlags.bit(Direction.NORTH),
                PipeShape.linkedSides(LinkFlags.ALL_OPEN, neighbours(LinkFlags.ALL_OPEN, NONE, NONE, NONE)), "linked");
    }

    public static void testTheVerticalLinkAddsNoArm() {
        Check.equal(1, PipeShape.collision(LinkFlags.VERTICAL).size());
    }

    private static boolean contains(List<Rectangle> shape, int x, int y) {
        for (Rectangle part : shape) {
            if (part.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

}
