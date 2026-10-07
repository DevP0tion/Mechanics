package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The floor tiles of a recognized tank's interior, recorded by its controller (N29-9).
 *
 * <ul>
 *     <li>When the controller recognizes a tank (it becomes active, or active with other bounds), it
 *     records the floor tile of every interior cell ({@link #take}). Natural grass or snow floors that
 *     are there then stay: they are what is recorded.</li>
 *     <li>At a later judgment of the same active tank, a loaded interior cell whose floor is now a
 *     natural spread floor (grass or snow, which spread onto dirt by themselves, N29-4) and differs
 *     from the record goes back to the recorded floor ({@link #check}). Inside a recognized tank of a
 *     loaded controller the spread is blocked (N29-4), so this covers what spread while the tank was
 *     dormant (N21-2, N29-3), for example in the world time simulation of a region that loaded
 *     before the controller's. Any other difference is left as it is.</li>
 *     <li>An interior cell that is not loaded when the record is taken has no record yet
 *     ({@link #UNKNOWN}); a later judgment that finds it loaded records it, without reverting it.
 *     A tank recognized before records existed (a saved judgment without one) gets its record the
 *     same way at its next judgment ({@link #afterJudgment}), and nothing is reverted then.</li>
 *     <li>While the tank is not active there is no record; recognizing it again takes a new one.</li>
 * </ul>
 * TODO(confirm): a grass or snow floor that differs from the record is reverted also when a player
 * put it there while the tank was dormant (placements inside a dormant tank are not rejected, N29-3);
 * the game cannot tell it from natural spread.
 * TODO(confirm): a floor that changed otherwise (not to grass or snow) does not update the record, so
 * grass or snow spreading onto it later is compared with the floor recorded at the recognition.
 *
 * <p>The record is saved with the controller's judgment: one floor per interior cell (at most 5x5,
 * 4-2). Game independent: floors are the game's tile ids, which the controller saves by name.
 */
public final class TankFloorRecord {

    /** No floor recorded for the cell yet: it was not loaded. */
    public static final int UNKNOWN = -1;

    /** Reads the floors of a level. The game adapter implements it; tests use their own. */
    public interface FloorLookup {

        /** Whether the tile is loaded. A floor is read only on a loaded tile. */
        boolean isLoaded(int tileX, int tileY);

        /** The floor tile id on a loaded tile. */
        int getFloor(int tileX, int tileY);

        /**
         * Whether the floor is one that natural spread puts on dirt by itself: a grass floor that
         * spreads, or snow (N29-4).
         */
        boolean isNaturalSpread(int floor);
    }

    /** An interior cell to set back to its recorded floor. */
    public static final class Revert {
        public final int tileX;
        public final int tileY;
        public final int floor;

        Revert(int tileX, int tileY, int floor) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.floor = floor;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Revert)) {
                return false;
            }
            Revert other = (Revert) o;
            return tileX == other.tileX && tileY == other.tileY && floor == other.floor;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tileX, tileY, floor);
        }

        @Override
        public String toString() {
            return "Revert[" + tileX + "," + tileY + " -> " + floor + "]";
        }
    }

    private final TankBounds bounds;
    /** One floor per interior cell, in reading order (row by row from the top left). */
    private final int[] floors;

    private TankFloorRecord(TankBounds bounds, int[] floors) {
        this.bounds = bounds;
        this.floors = floors;
    }

    /** Records the interior floors of {@code bounds}: the loaded cells now, the others {@link #UNKNOWN}. */
    public static TankFloorRecord take(TankBounds bounds, FloorLookup lookup) {
        int[] floors = new int[bounds.getInteriorCellCount()];
        int i = 0;
        for (int y = bounds.y + 1; y < bounds.getMaxY(); y++) {
            for (int x = bounds.x + 1; x < bounds.getMaxX(); x++) {
                floors[i++] = lookup.isLoaded(x, y) ? lookup.getFloor(x, y) : UNKNOWN;
            }
        }
        return new TankFloorRecord(bounds, floors);
    }

    /**
     * A saved record, or {@code null} when it does not fit the bounds (one floor per interior cell);
     * a negative floor is {@link #UNKNOWN}.
     */
    public static TankFloorRecord restore(TankBounds bounds, int[] floors) {
        if (bounds == null || floors == null || floors.length != bounds.getInteriorCellCount()) {
            return null;
        }
        int[] copy = floors.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] < 0) {
                copy[i] = UNKNOWN;
            }
        }
        return new TankFloorRecord(bounds, copy);
    }

    /**
     * The record after a judgment of the controller (N29-9), adding to {@code reverts} the cells to set
     * back now.
     *
     * @param record     the record before the judgment, or {@code null}
     * @param activeTank the controller's tank when it is active after the judgment, else {@code null}
     * @param wasActive  whether the tank was active before the judgment (a saved active judgment counts)
     * @return the record to keep: {@code null} without an active tank; a new one when the tank is
     *         newly recognized, or has no record yet (nothing reverted then); else {@code record},
     *         checked ({@link #check})
     */
    public static TankFloorRecord afterJudgment(TankFloorRecord record, TankBounds activeTank, boolean wasActive,
                                                FloorLookup lookup, List<Revert> reverts) {
        if (activeTank == null) {
            return null;
        }
        if (!wasActive || record == null || !record.bounds.equals(activeTank)) {
            return take(activeTank, lookup);
        }
        reverts.addAll(record.check(lookup));
        return record;
    }

    /**
     * Compares the loaded interior cells with the record: the cells whose floor is now a natural
     * spread floor different from the recorded one are returned, to be set back to it; cells
     * without a record yet are recorded now.
     */
    public List<Revert> check(FloorLookup lookup) {
        List<Revert> reverts = new ArrayList<>();
        int i = 0;
        for (int y = bounds.y + 1; y < bounds.getMaxY(); y++) {
            for (int x = bounds.x + 1; x < bounds.getMaxX(); x++, i++) {
                if (!lookup.isLoaded(x, y)) {
                    continue;
                }
                int floor = lookup.getFloor(x, y);
                if (floors[i] == UNKNOWN) {
                    floors[i] = floor;
                } else if (floor != floors[i] && lookup.isNaturalSpread(floor)) {
                    reverts.add(new Revert(x, y, floors[i]));
                }
            }
        }
        return reverts;
    }

    public TankBounds getBounds() {
        return bounds;
    }

    /** The recorded floors, one per interior cell in reading order ({@link #UNKNOWN} for none yet). */
    public int[] getFloors() {
        return floors.clone();
    }

    /** The recorded floor of an interior cell, or {@link #UNKNOWN}. */
    public int getFloor(int tileX, int tileY) {
        if (!bounds.isInterior(tileX, tileY)) {
            return UNKNOWN;
        }
        return floors[(tileY - bounds.y - 1) * bounds.getInteriorWidth() + (tileX - bounds.x - 1)];
    }

    @Override
    public String toString() {
        return "TankFloorRecord[" + bounds + ", " + Arrays.toString(floors) + "]";
    }

}
