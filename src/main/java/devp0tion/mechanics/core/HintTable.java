package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The destination number table of one pipe group (N28-15): number → destination valve position.
 * A pipe's hints are pairs of a number from its group's table and a direction code ({@link PipeNode},
 * N24-2), so a pipe keeps small numbers instead of positions.
 *
 * <ul>
 *     <li>The group is the set of linked pipes, empty or not (not the reached-fluid network of
 *     N13-1). TODO(confirm): the reading of "network" for the number table (N28-15 1).</li>
 *     <li>Each pipe saves its table's id with its hints; the tables are saved with the level
 *     ({@code PipeSystem}). When groups merge or split, the loaded pipes are renumbered into the
 *     surviving tables; each part of a split gets a table of its own. An old table stays, read only,
 *     while pipes of unloaded regions still refer to it; a pipe that loads with it is renumbered
 *     into its group's table then, and the table is dropped once no pipe refers to it.
 *     TODO(confirm): this bookkeeping for partly unloaded groups (N28-15 2).</li>
 *     <li>Numbers are never reused within a table: the pipes of unloaded regions keep theirs.</li>
 * </ul>
 */
public final class HintTable {

    /** Level-unique id, saved with each pipe's hints. */
    public final int id;
    private long[] dests = new long[4];
    private int size;
    /** The loaded pipes of the group. */
    final Set<PipeNode> cells = Collections.newSetFromMap(new IdentityHashMap<PipeNode, Boolean>());
    /** Pipes of unloaded regions that refer to this table (they saved its id). */
    int unloadedRefs;
    /** An old table: its pipes were renumbered into {@link #successor}; read only. */
    boolean retired;
    int successor = -1;

    HintTable(int id) {
        this.id = id;
    }

    /** The number of a destination, or -1. */
    int numberOf(long dest) {
        for (int i = 0; i < size; i++) {
            if (dests[i] == dest) {
                return i;
            }
        }
        return -1;
    }

    /** The number of a destination, given a new one when it has none. */
    int numberFor(long dest) {
        int number = numberOf(dest);
        if (number >= 0) {
            return number;
        }
        if (size == dests.length) {
            dests = java.util.Arrays.copyOf(dests, size * 2);
        }
        dests[size] = dest;
        return size++;
    }

    /** The destination of a number, or {@link Long#MIN_VALUE} for a number the table does not have. */
    long destinationOf(int number) {
        return number >= 0 && number < size ? dests[number] : Long.MIN_VALUE;
    }

    /** Number → destination (for saving). */
    public long[] getDestinations() {
        return java.util.Arrays.copyOf(dests, size);
    }

    /** Pipes that refer to it: the loaded ones and those of unloaded regions (for saving). */
    public int getReferences() {
        return cells.size() + unloadedRefs;
    }

    public boolean isRetired() {
        return retired;
    }

    public int getSuccessor() {
        return successor;
    }

    void restore(long[] destinations) {
        dests = java.util.Arrays.copyOf(destinations, Math.max(4, destinations.length));
        size = destinations.length;
    }

    @Override
    public String toString() {
        return "HintTable[" + id + ", " + size + " numbers, " + cells.size() + " loaded + " + unloadedRefs + " unloaded"
                + (retired ? ", retired -> " + successor : "") + "]";
    }

}
