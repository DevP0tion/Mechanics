package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A spatial index: values filed under square buckets of tiles, one game region each
 * ({@link #SIZE}), so the values near a tile are found without scanning all of them. A value may be
 * filed under several buckets (for example a route crossing several regions). Lookups return every
 * value of the buckets they touch; callers check the exact condition themselves.
 *
 * <p>Not thread-safe.
 */
public final class TileBuckets<T> {

    /** Bucket size in tiles: the game's region size ({@code RegionManager.REGION_SIZE}). Technical. */
    public static final int SIZE = 16;

    private final Map<Long, Set<T>> buckets = new HashMap<>();

    /** The bucket of a tile. */
    public static long bucketOf(int tileX, int tileY) {
        return PipeGrid.key(Math.floorDiv(tileX, SIZE), Math.floorDiv(tileY, SIZE));
    }

    /** Files {@code value} under the bucket of the tile. */
    public void add(int tileX, int tileY, T value) {
        add(bucketOf(tileX, tileY), value);
    }

    public void add(long bucket, T value) {
        Set<T> set = buckets.get(bucket);
        if (set == null) {
            set = new LinkedHashSet<>();
            buckets.put(bucket, set);
        }
        set.add(value);
    }

    /** Removes {@code value} from the bucket of the tile. */
    public void remove(int tileX, int tileY, T value) {
        remove(bucketOf(tileX, tileY), value);
    }

    public void remove(long bucket, T value) {
        Set<T> set = buckets.get(bucket);
        if (set != null && set.remove(value) && set.isEmpty()) {
            buckets.remove(bucket);
        }
    }

    /** The values filed under the bucket of the tile (a copy). */
    public List<T> at(int tileX, int tileY) {
        Set<T> set = buckets.get(bucketOf(tileX, tileY));
        return set == null ? Collections.<T>emptyList() : new ArrayList<>(set);
    }

    /**
     * The values filed under the buckets that overlap the tile rectangle {@code [x0, x1] x [y0, y1]}
     * (a copy; a value filed under several of them appears once). It may hold values outside the
     * rectangle.
     */
    public List<T> near(int x0, int y0, int x1, int y1) {
        int bx0 = Math.floorDiv(Math.min(x0, x1), SIZE);
        int bx1 = Math.floorDiv(Math.max(x0, x1), SIZE);
        int by0 = Math.floorDiv(Math.min(y0, y1), SIZE);
        int by1 = Math.floorDiv(Math.max(y0, y1), SIZE);
        Set<T> result = null;
        for (int by = by0; by <= by1; by++) {
            for (int bx = bx0; bx <= bx1; bx++) {
                Set<T> set = buckets.get(PipeGrid.key(bx, by));
                if (set != null) {
                    if (result == null) {
                        result = new LinkedHashSet<>();
                    }
                    result.addAll(set);
                }
            }
        }
        return result == null ? Collections.<T>emptyList() : new ArrayList<>(result);
    }

    /** Number of (bucket, value) entries, for tests. */
    int entryCount() {
        int count = 0;
        for (Set<T> set : buckets.values()) {
            count += set.size();
        }
        return count;
    }

}
