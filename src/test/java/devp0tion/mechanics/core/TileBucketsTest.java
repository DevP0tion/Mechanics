package devp0tion.mechanics.core;

import java.util.Arrays;
import java.util.Collections;

/**
 * The region-keyed spatial index ({@link TileBuckets}) behind the change lookups of the tank
 * registry and the pipe grid's route caches, and the route index staying in step with the caches.
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

    // ---------- the pipe grid's route index ----------

    /** Every cached route is filed under all the buckets it crosses, and nothing else is filed. */
    private static void checkRouteIndex(PipeGrid grid, String step) {
        int expected = 0;
        for (PipeNetwork network : grid.getNetworks()) {
            for (java.util.Map.Entry<Pump, PipeGrid.PumpRoutes> entry : network.routeCache.entrySet()) {
                Check.isTrue(entry.getKey().getNetwork() == network, step + ": a cache lives in its pump's network");
                for (long bucket : entry.getValue().buckets) {
                    Check.isTrue(grid.routeBuckets.at(PipeGrid.keyX(bucket) * TileBuckets.SIZE,
                            PipeGrid.keyY(bucket) * TileBuckets.SIZE).contains(entry.getKey()), step + ": filed");
                }
                expected += entry.getValue().buckets.size();
            }
        }
        Check.equal(expected, grid.routeBuckets.entryCount(), step + ": no stale entries");
    }

    private static boolean cached(Pump pump) {
        return pump.getNetwork() != null && pump.getNetwork().routeCache.containsKey(pump);
    }

    public static void testAChangeDropsOnlyTheRoutesNearIt() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        // Pump A's line crosses from region 0 into region 1; pump B is far away in region 4.
        Pump a = Fluids.fueledPump(grid, 10, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 11, 20, 0, MineralTier.COPPER);
        grid.placeValve(21, 0, Fluids.valve(10000));
        Pump b = Fluids.fueledPump(grid, 70, 0, PumpTier.FIRE, FluidType.LAVA);
        Fluids.baseLine(grid, 71, 73, 0, MineralTier.COPPER);
        grid.placeValve(74, 0, Fluids.valve(10000));
        Check.equal(10, grid.getPathDistance(a, grid.getValve(21, 0), FluidType.FRESHWATER));
        Check.equal(3, grid.getPathDistance(b, grid.getValve(74, 0), FluidType.LAVA));
        Check.isTrue(cached(a) && cached(b), "both cached");
        checkRouteIndex(grid, "cached");
        grid.placePipe(19, 1, PipeLayer.BASE, MineralTier.COPPER);
        Check.isFalse(cached(a), "a change next to A's route drops it");
        Check.isTrue(cached(b), "B's route is far away: kept");
        checkRouteIndex(grid, "after a change");
        grid.placePipe(40, 5, PipeLayer.BASE, MineralTier.COPPER);
        Check.isTrue(cached(b), "a change near nothing drops nothing");
        Check.equal(10, grid.getPathDistance(a, grid.getValve(21, 0), FluidType.FRESHWATER), "A's routes again");
        checkRouteIndex(grid, "recomputed");
    }

    public static void testRouteIndexFollowsPushesMergesAndRemovals() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        // Two pumps at the ends of a line across a region edge (x = 15/16), a tank below its middle.
        Pump a = Fluids.fueledPump(grid, 10, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Pump b = Fluids.fueledPump(grid, 20, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 11, 19, 0, MineralTier.COPPER);
        grid.placeValve(15, 1, Fluids.valve(100000));
        for (int i = 0; i < 200; i++) {
            Fluids.tickAll(grid, a, b);
            checkRouteIndex(grid, "tick " + i);
        }
        Check.isTrue(a.getNetwork() == b.getNetwork(), "the two pumps' fluid met: one network (N18-2)");
        Check.isTrue(cached(a) && cached(b), "both pushing with cached routes");
        grid.toggleSide(13, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        checkRouteIndex(grid, "cut");
        Fluids.tickAll(grid, a, b);
        checkRouteIndex(grid, "after the cut");
        grid.removePipe(17, 0, PipeLayer.BASE);
        checkRouteIndex(grid, "pipe removed");
        for (int i = 0; i < 40; i++) {
            Fluids.tickAll(grid, a, b);
        }
        checkRouteIndex(grid, "pushing again");
        grid.removePump(20, 0);
        checkRouteIndex(grid, "pump removed");
        Check.isFalse(grid.routeBuckets.near(-100, -100, 100, 100).contains(b), "the removed pump is filed nowhere");
        Fluids.tickAll(grid, a);
        checkRouteIndex(grid, "end");
    }

}
