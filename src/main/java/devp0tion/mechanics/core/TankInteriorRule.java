package devp0tion.mechanics.core;

/**
 * What may be placed inside a recognized tank (N16-2). The interior of a recognized (valid) tank
 * takes only glass blocks, the tank floor tile and underground pipes; every other placement into
 * an interior cell is rejected: objects on any object layer (basic pipes included), any other floor
 * tile, liquid tiles. The interior of an inactive (invalid) tank is not covered.
 *
 * <p>This is the one allow-list; the game adapter maps objects and tiles to a {@link Placement}.
 */
public final class TankInteriorRule {

    /** What a placement puts on an interior cell. */
    public enum Placement {
        /** A glass block (2-1, 5-5): allowed. */
        GLASS_BLOCK,
        /** The tank floor tile (5-6, 8-8; does not exist yet): allowed. */
        TANK_FLOOR,
        /** An underground pipe (9-1, 10-3): allowed. */
        UNDERGROUND_PIPE,
        /** A basic (base layer) pipe (9-1): rejected like everything else. */
        BASIC_PIPE,
        /** Anything else: any other object on any object layer, any other floor tile, liquids. */
        OTHER
    }

    private TankInteriorRule() {
    }

    /** Whether {@code placement} is allowed on an interior cell of a recognized tank (N16-2). */
    public static boolean isAllowed(Placement placement) {
        switch (placement) {
            case GLASS_BLOCK:
            case TANK_FLOOR:
            case UNDERGROUND_PIPE:
                return true;
            default:
                return false;
        }
    }

}
