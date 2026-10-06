package devp0tion.mechanics.core;

/**
 * The two kinds of pipe (9-1, 9-8).
 */
public enum PipeLayer {

    /** Basic pipe, on the game's {@code base} object layer (9-1). */
    BASE,
    /**
     * Underground pipe, on the pipes' own object layer (9-1). It passes under anything (10-4),
     * links to the basic pipe on the same tile (9-5), to a tank valve only on the same tile, and
     * never to pumps (9-9).
     */
    UNDERGROUND;

    public PipeLayer other() {
        return this == BASE ? UNDERGROUND : BASE;
    }

}
