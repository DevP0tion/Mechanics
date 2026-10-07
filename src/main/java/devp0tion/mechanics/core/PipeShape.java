package devp0tion.mechanics.core;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * The basic pipe's collision shape (N31-10): a center square plus an arm toward each side it is
 * linked toward, not the full tile. The sizes follow the pipe sheets (hub 14x14, arms 12 wide, see
 * {@code PipeRendering}), in pixels of a 32x32 tile.
 *
 * <p>A side is linked when both facing flags are open toward a part the pipe links to (9-4, 13-4,
 * N16-4): a basic pipe, a valve or a pump next to it. The game reads the flags when it asks for the
 * collision, so the shape follows every link change: the wrench toggling a side, a neighbour placed
 * or removed.
 * TODO(confirm): a side linked but blocked by another fluid (N13-2), which the pipe draws with a cut
 * mark, keeps its arm: the shape follows the links, not the fluids.
 *
 * <p>Game independent, so it is tested by the plain test runner.
 */
public final class PipeShape {

    /** The center square, relative to the tile's top left corner. */
    public static final Rectangle HUB = new Rectangle(9, 9, 14, 14);

    /** Arms per {@link Direction}, from the tile edge to the center square. */
    private static final Rectangle[] ARMS = {
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

    /** The collision rectangles of a pipe linked toward {@code linkedSides}, relative to its tile. */
    public static List<Rectangle> collision(int linkedSides) {
        List<Rectangle> shape = new ArrayList<>(5);
        shape.add(new Rectangle(HUB));
        for (Direction d : Direction.values()) {
            if (LinkFlags.isSideOpen(linkedSides, d)) {
                shape.add(new Rectangle(ARMS[d.ordinal()]));
            }
        }
        return shape;
    }

}
