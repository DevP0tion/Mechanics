package devp0tion.mechanics.core;

/**
 * What occupies a tile, as far as multiblock tank recognition is concerned.
 */
public enum CellKind {
    /** A mineral wall (5-7, 5-12). The cell also carries its {@link MineralTier}. */
    MINERAL_WALL,
    /** The tank controller (2-1, 4-3). */
    CONTROLLER,
    /** A tank valve (2-1, 4-4). */
    VALVE,
    /** A glass block (2-1, 5-5). */
    GLASS,
    /** No object. */
    EMPTY,
    /** Anything else. */
    OTHER
}
