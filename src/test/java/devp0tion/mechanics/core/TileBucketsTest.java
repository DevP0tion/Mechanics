package devp0tion.mechanics.core;

import java.util.Arrays;
import java.util.Collections;

/**
 * The region-keyed spatial index ({@link TileBuckets}) behind the change lookups of the tank
 * registry; its buckets are also the regions of the pipe engine's summaries (N23-2, N28-1).
 */
final class TileBucketsTest {

    private TileBucketsTest() {
    }

    public static void testBucketsAreRegions() {
        Check.equal(TileBuckets.bucketOf(0, 0), TileBuckets.bucketOf(15, 15), "one region");
        Check.isFalse(TileBuckets.bucketOf(15, 0) == TileBuckets.bucketOf(16, 0), "next region east");
        Check.equal(TileBuckets.bucketOf(-1, -1), TileBuckets.bucketOf(-16, -16), "negative tiles round down");
        Check.isFalse(TileBuckets.bucketOf(-1, 0) == TileBuckets.bucketOf(0, 0), "-1 is the region west of 0");
    }

    public static void testNearFindsTheValuesOfTheTouchedBuckets() {
        TileBuckets<String> index = new TileBuckets<>();
        index.add(2, 2, "a");
        index.add(20, 2, "b");
        index.add(-3, 2, "c");
        index.add(100, 100, "far");
        Check.equal(Arrays.asList("a"), index.at(5, 5));
        Check.equal(Arrays.asList("c", "a", "b"), index.near(-6, 0, 18, 4), "three regions in a row, west first");
        Check.equal(Arrays.asList("a", "b"), index.near(10, 2, 16, 2), "a rectangle across a region edge");
        Check.equal(Collections.emptyList(), index.near(40, 40, 60, 60));
        TileBuckets<String> route = new TileBuckets<>();
        route.add(1, 1, "x");
        route.add(40, 40, "x");
        Check.equal(Arrays.asList("x"), route.near(0, 0, 47, 47), "a value under two buckets appears once");
        Check.equal(2, route.entryCount());
    }

    public static void testRemoveDropsEmptyBuckets() {
        TileBuckets<String> index = new TileBuckets<>();
        index.add(1, 1, "a");
        index.add(2, 2, "b");
        index.remove(1, 1, "a");
        Check.equal(Arrays.asList("b"), index.at(0, 0));
        index.remove(3, 3, "b");
        Check.equal(0, index.entryCount());
        index.remove(3, 3, "b");
        Check.equal(Collections.emptyList(), index.at(0, 0), "removing twice is harmless");
    }

}
