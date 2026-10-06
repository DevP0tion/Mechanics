package devp0tion.mechanics.core;

/**
 * The link (cut) flags of a pipe, a tank valve or a pump, packed into one int so they can be saved
 * and synced as a single value.
 *
 * <ul>
 *     <li>Bits 0-3, one per {@link Direction} (by ordinal): the side toward that neighbour is open.
 *     A link between two parts needs both facing flags open; the wrench sets both (12-8, 13-4).</li>
 *     <li>Bit 4: the vertical link to the other layer on the same tile, between a basic and an
 *     underground pipe or between an underground pipe and a tank valve (9-5, 9-9, 13-5).</li>
 * </ul>
 * A flag belongs to its own part and stays when the other side is removed (N16-4): a part placed
 * again next to a cut flag does not link through it.
 */
public final class LinkFlags {

    /** Every side and the vertical link open. */
    public static final int ALL_OPEN = 0b11111;

    /** The vertical link bit. */
    public static final int VERTICAL = 1 << 4;

    /** The four side bits. */
    public static final int SIDES = ALL_OPEN & ~VERTICAL;

    private LinkFlags() {
    }

    public static int bit(Direction direction) {
        return 1 << direction.ordinal();
    }

    public static boolean isSideOpen(int flags, Direction direction) {
        return (flags & bit(direction)) != 0;
    }

    public static int withSide(int flags, Direction direction, boolean open) {
        return open ? flags | bit(direction) : flags & ~bit(direction);
    }

    public static boolean isVerticalOpen(int flags) {
        return (flags & VERTICAL) != 0;
    }

    public static int withVertical(int flags, boolean open) {
        return open ? flags | VERTICAL : flags & ~VERTICAL;
    }

    /** Keeps only the defined bits (for values read from saves and packets). */
    public static int sanitize(int flags) {
        return flags & ALL_OPEN;
    }

}
