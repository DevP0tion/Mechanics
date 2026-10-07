package devp0tion.mechanics.core;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * The basic pipe's collision shape (N31-10, N31-13): a center square plus a connection part toward
 * each side that is linked and not blocked by another fluid, not the full tile. The sizes follow the
 * pipe sheets (hub 14x14, connection parts 12 wide, see {@code PipeRendering}), in pixels of a 32x32
 * tile.
 *
 * <p>A side is linked when both facing flags are open toward a part the pipe links to (9-4, 13-4,
 * N16-4): a basic pipe, a valve or a pump next to it. The game reads the flags when it asks for the
 * collision, so the shape follows every link change: the wrench toggling a side, a neighbour placed
 * or removed.
 *
 * <p>A side blocked by another fluid (N13-2: the pipe next to it holds another fluid than this one)
 * has its connection part closed, exactly like a cut link (N31-13): the collision follows the
 * drawing, which shows that face cut. So the shape is the center square plus a connection part
 * toward each side that is linked and not blocked ({@link #openSides}). The blocked faces are the
 * ones the server syncs to clients for the drawing, read from the same place on both sides, and
 * change when fluid arrives in a pipe, a pipe holding fluid is removed or a neighbour holds another
 * fluid.
 *
 * <p>Game independent, so it is tested by the plain test runner.
 */
public final class PipeShape {

    /** The center square, relative to the tile's top left corner. */
    public static final Rectangle HUB = new Rectangle(9, 9, 14, 14);

    /** Connection parts per {@link Direction}, from the tile edge to the center square. */
    private static final Rectangle[] CONNECTION_PARTS = {
            new Rectangle(10, 0, 12, 9),
            new Rectangle(23, 10, 9, 12),
            new Rectangle(10, 23, 12, 9),
            new Rectangle(0, 10, 9, 12)};

    private PipeShape() {
    }

    /**
     * The sides a pipe is linked toward ({@link LinkFlags} side bits).
     *
     * @param ownLinks       the pipe's link flags
     * @param neighbourLinks per {@link Direction} ordinal, the flags of the part next to it that it
     *                       links to, or -1 when there is none
     */
    public static int linkedSides(int ownLinks, int[] neighbourLinks) {
        int sides = 0;
        for (Direction d : Direction.values()) {
            int neighbour = neighbourLinks[d.ordinal()];
            if (LinkFlags.isSideOpen(ownLinks, d) && neighbour >= 0 && LinkFlags.isSideOpen(neighbour, d.opposite())) {
                sides |= LinkFlags.bit(d);
            }
        }
        return sides;
    }

    /**
     * The sides that get a connection part (N31-13): linked and not blocked by another fluid.
     *
     * @param linkedSides       {@link #linkedSides}
     * @param fluidBlockedSides the faces blocked by another fluid ({@link PipeGrid#getFluidBlockedSides},
     *                          as synced to clients); the vertical bit closes no side
     */
    public static int openSides(int linkedSides, int fluidBlockedSides) {
        return linkedSides & ~fluidBlockedSides & LinkFlags.SIDES;
    }

    /** The collision rectangles of a pipe with connection parts toward {@code openSides}, relative to its tile. */
    public static List<Rectangle> collision(int openSides) {
        List<Rectangle> shape = new ArrayList<>(5);
        shape.add(new Rectangle(HUB));
        for (Direction d : Direction.values()) {
            if (LinkFlags.isSideOpen(openSides, d)) {
                shape.add(new Rectangle(CONNECTION_PARTS[d.ordinal()]));
            }
        }
        return shape;
    }

}
