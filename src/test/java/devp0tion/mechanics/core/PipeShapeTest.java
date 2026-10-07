package devp0tion.mechanics.core;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The basic pipe's collision shape (N31-10, N31-13): a center square plus connection parts toward
 * its sides that are linked and not blocked by another fluid.
 */
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

    public static void testConnectionPartsTowardTheLinkedSides() {
        int linked = PipeShape.linkedSides(LinkFlags.ALL_OPEN,
                neighbours(NONE, LinkFlags.ALL_OPEN, NONE, LinkFlags.ALL_OPEN));
        Check.equal(LinkFlags.bit(Direction.EAST) | LinkFlags.bit(Direction.WEST), linked);
        List<Rectangle> shape = PipeShape.collision(linked);
        Check.equal(3, shape.size(), "hub, east, west");
        Check.isTrue(shape.contains(new Rectangle(23, 10, 9, 12)), "east connection part to the tile edge");
        Check.isTrue(shape.contains(new Rectangle(0, 10, 9, 12)), "west connection part to the tile edge");
    }

    public static void testEveryConnectionPartReachesItsEdgeAndTheCenter() {
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

    public static void testACutFlagOnEitherSideIsNoConnectionPart() {
        int ownCutNorth = LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.NORTH, false);
        int southCut = LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.SOUTH, false);
        Check.equal(0, PipeShape.linkedSides(ownCutNorth, neighbours(LinkFlags.ALL_OPEN, NONE, NONE, NONE)),
                "own flag cut (the wrench)");
        Check.equal(0, PipeShape.linkedSides(LinkFlags.ALL_OPEN, neighbours(southCut, NONE, NONE, NONE)),
                "the neighbour's flag toward it cut");
        Check.equal(LinkFlags.bit(Direction.NORTH),
                PipeShape.linkedSides(LinkFlags.ALL_OPEN, neighbours(LinkFlags.ALL_OPEN, NONE, NONE, NONE)), "linked");
    }

    public static void testTheVerticalLinkAddsNoConnectionPart() {
        Check.equal(1, PipeShape.collision(LinkFlags.VERTICAL).size());
    }

    // ---------- N31-13: a side blocked by another fluid is closed, like a cut link ----------

    public static void testAFluidBlockedSideHasNoConnectionPart() {
        int linked = LinkFlags.bit(Direction.EAST) | LinkFlags.bit(Direction.WEST);
        int open = PipeShape.openSides(linked, LinkFlags.bit(Direction.EAST));
        Check.equal(LinkFlags.bit(Direction.WEST), open, "east linked but blocked: closed");
        List<Rectangle> shape = PipeShape.collision(open);
        Check.equal(2, shape.size(), "center, west");
        Check.isFalse(shape.contains(new Rectangle(23, 10, 9, 12)), "no east connection part");
        Check.isFalse(contains(shape, 28, 16), "the east edge is free, as for a cut link");
        Check.isTrue(contains(shape, 3, 16), "the west connection part stays");
        Check.equal(PipeShape.collision(LinkFlags.bit(Direction.WEST)), shape, "the same shape as with the east link cut");
    }

    public static void testABlockedFaceOpensNothing() {
        Check.equal(0, PipeShape.openSides(0, LinkFlags.bit(Direction.NORTH)), "blocked, not linked");
        Check.equal(0, PipeShape.openSides(0, LinkFlags.ALL_OPEN), "every face blocked, nothing linked");
        Check.equal(LinkFlags.bit(Direction.SOUTH),
                PipeShape.openSides(LinkFlags.bit(Direction.SOUTH), LinkFlags.bit(Direction.NORTH)), "another face blocked");
    }

    public static void testTheVerticalBlockedFaceClosesNoSide() {
        Check.equal(LinkFlags.SIDES, PipeShape.openSides(LinkFlags.SIDES, LinkFlags.VERTICAL),
                "blocked toward the other layer only: every side keeps its connection part");
        Check.equal(LinkFlags.SIDES, PipeShape.openSides(LinkFlags.ALL_OPEN, 0), "the vertical link is no side");
    }

    public static void testPipesOfDifferentFluidsHaveNoConnectionPartBetweenThem() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(0, 1, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 0, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        Fluids.set(grid, 0, 1, PipeLayer.BASE, FluidType.LAVA, 20);
        Check.isTrue(grid.areLinked(grid.getPipe(0, 0, PipeLayer.BASE), grid.getPipe(0, 1, PipeLayer.BASE)),
                "linked, but the face is a dead end (N13-2)");
        Check.equal(0, openSides(grid, 0, 0), "north pipe: no connection part toward the lava");
        Check.equal(0, openSides(grid, 0, 1), "south pipe: no connection part toward the water");
        // The edge between them is free: a mob can pass between the two center squares.
        Check.isFalse(contains(shape(grid, 0, 0), 16, 31), "north pipe's south edge");
        Check.isFalse(contains(shape(grid, 0, 1), 16, 0), "south pipe's north edge");
    }

    public static void testTheSameFluidOrAnEmptyPipeKeepsTheConnectionPart() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(0, 1, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 0, 0, PipeLayer.BASE, FluidType.LAVA, 20);
        Check.equal(LinkFlags.bit(Direction.SOUTH), openSides(grid, 0, 0), "the other pipe empty");
        Check.equal(LinkFlags.bit(Direction.NORTH), openSides(grid, 0, 1));
        Fluids.set(grid, 0, 1, PipeLayer.BASE, FluidType.LAVA, 20);
        Check.equal(LinkFlags.bit(Direction.SOUTH), openSides(grid, 0, 0), "the same fluid");
        Check.equal(LinkFlags.bit(Direction.NORTH), openSides(grid, 0, 1));
        Check.isTrue(contains(shape(grid, 0, 0), 16, 31), "the connection part reaches the edge");
    }

    /**
     * The game syncs a basic pipe's blocked faces when the engine says a pipe started or stopped
     * holding fluid (the pipe, the pipes next to it), and the collision reads the synced faces: the
     * shape from them follows fluid arriving, a pipe removed and a neighbour of another fluid.
     */
    public static void testTheSyncedFacesFollowTheFluidChanges() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        SyncedFaces synced = new SyncedFaces(grid);
        grid.setListener(synced);
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(0, 1, PipeLayer.BASE, MineralTier.COPPER);
        int south = LinkFlags.bit(Direction.SOUTH);
        Check.equal(south, synced.openSides(0, 0), "both empty");

        Fluids.set(grid, 0, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        Check.equal(south, synced.openSides(0, 0), "water arrived, the other pipe empty");
        Fluids.set(grid, 0, 1, PipeLayer.BASE, FluidType.LAVA, 20);
        Check.equal(0, synced.openSides(0, 0), "lava arrived next to it: closed");
        Check.equal(0, synced.openSides(0, 1), "closed on the lava side too");

        grid.removePipe(0, 1, PipeLayer.BASE);
        synced.forget(0, 1);
        Check.equal(0, synced.blocked(0, 0), "the lava pipe removed: no longer blocked");
        grid.placePipe(0, 1, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(south, synced.openSides(0, 0), "an empty pipe in its place: open");

        Fluids.set(grid, 0, 1, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        Check.equal(south, synced.openSides(0, 0), "the same fluid: open");

        grid.removePipe(0, 1, PipeLayer.BASE);
        synced.forget(0, 1);
        grid.placePipe(0, 1, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 0, 1, PipeLayer.BASE, FluidType.SLIME, 20);
        Check.equal(0, synced.openSides(0, 0), "a neighbour of another fluid again: closed");
        Check.equal(0, synced.openSides(0, 1));
        for (int y = 0; y <= 1; y++) {
            Check.equal(grid.getFluidBlockedSides(0, y, PipeLayer.BASE), synced.blocked(0, y), "synced = engine at y " + y);
        }
    }

    /** The open sides of the basic pipe at the tile, from the engine (what the game syncs). */
    private static int openSides(PipeGrid grid, int x, int y) {
        return PipeShape.openSides(linkedSides(grid, x, y), grid.getFluidBlockedSides(x, y, PipeLayer.BASE));
    }

    private static List<Rectangle> shape(PipeGrid grid, int x, int y) {
        return PipeShape.collision(openSides(grid, x, y));
    }

    private static int linkedSides(PipeGrid grid, int x, int y) {
        int[] neighbours = new int[Direction.values().length];
        for (Direction d : Direction.values()) {
            PipeNode neighbour = grid.getPipe(x + d.dx, y + d.dy, PipeLayer.BASE);
            neighbours[d.ordinal()] = neighbour == null ? NONE : neighbour.getLinks();
        }
        return PipeShape.linkedSides(grid.getPipe(x, y, PipeLayer.BASE).getLinks(), neighbours);
    }

    /**
     * The basic pipes' blocked faces as the game keeps them in their object entities: updated only
     * when the engine reports a fluid change, for the pipe and the pipes of its layer next to it
     * ({@code PipeSystem.onPipeFluidChanged}).
     */
    private static final class SyncedFaces implements PipeGrid.Listener {
        private final PipeGrid grid;
        private final Map<Long, Integer> faces = new HashMap<>();

        SyncedFaces(PipeGrid grid) {
            this.grid = grid;
        }

        @Override
        public void onLinksChanged(int tileX, int tileY, PipeGrid.Part part) {
        }

        @Override
        public void onPipeFluidChanged(int tileX, int tileY, PipeLayer layer) {
            sync(tileX, tileY);
            if (layer == PipeLayer.BASE) {
                for (Direction d : Direction.values()) {
                    sync(tileX + d.dx, tileY + d.dy);
                }
            }
        }

        private void sync(int x, int y) {
            if (grid.getPipe(x, y, PipeLayer.BASE) != null) {
                faces.put(PipeGrid.key(x, y), grid.getFluidBlockedSides(x, y, PipeLayer.BASE));
            }
        }

        /** The pipe was removed: its entity is gone. */
        void forget(int x, int y) {
            faces.remove(PipeGrid.key(x, y));
        }

        int blocked(int x, int y) {
            Integer f = faces.get(PipeGrid.key(x, y));
            return f == null ? 0 : f;
        }

        int openSides(int x, int y) {
            return PipeShape.openSides(linkedSides(grid, x, y), blocked(x, y));
        }
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
