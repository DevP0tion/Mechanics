package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

import java.util.Map;

/**
 * The new engine's own rules, which the compatibility comparison leaves out on purpose (N26-1):
 * the split at junctions (N24-3, N25-5, N27-1), the fill speed (N25-1~N25-3), the cap order by
 * distance (N26-4) and the network summary for unloaded regions (N23-2); and its structure: cell
 * hints repaired when used and kept across saves (N22-3, N25-1, N25-4, N25-6), systems run from one
 * tick (N22-5).
 */
final class NewEngineTest {

    private NewEngineTest() {
    }

    private static PipeGrid grid(PipeTierRules rules) {
        return new PipeGrid(rules);
    }

    private static PipeGrid compat(PipeTierRules rules) {
        PipeGrid grid = new PipeGrid(rules);
        grid.setCompatMode(true);
        return grid;
    }

    private static Pump pump(PipeGrid grid, int x, int y, PumpTier tier) {
        Pump pump = new Pump(tier);
        pump.setTileSource(LiquidTileSource.infinite(FluidType.FRESHWATER));
        pump.setFuelSupply(new Fluids.Logs(1000));
        grid.placePump(x, y, pump);
        return pump;
    }

    private static void line(PipeGrid grid, int x0, int y0, int x1, int y1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                grid.placePipe(x, y, PipeLayer.BASE, MineralTier.IRON);
            }
        }
    }

    private static void fillAll(PipeGrid grid) {
        for (PipeNode node : grid.getPipes()) {
            grid.loadPipe(node.getTileX(), node.getTileY(), node.getLayer(), node.getTier(), node.getLinks(),
                    FluidType.FRESHWATER, node.getCapacity(), true);
        }
    }

    /** Runs the systems until the pump at the tile ran a cycle; returns its result. */
    private static PumpResult cycle(PipeGrid grid, Pump pump) {
        for (int i = 0; i < 100; i++) {
            Map<Long, PumpResult> results = grid.runTick();
            PumpResult result = results.get(PipeGrid.key(pump.getTileX(), pump.getTileY()));
            if (result != null) {
                return result;
            }
        }
        throw new AssertionError("no cycle");
    }

    // ---------------------------------------------------------------- N24-3, N25-5, N27-1

    /**
     * Pump (0,0) -> (1,0)..(3,0) = junction J. North of J: a long branch to valve A (distance 10).
     * East of J: (4,0) = junction J2, south to valve B (distance 5), east to valve C (distance 6).
     */
    private static TankValve[] twoJunctions(PipeGrid grid) {
        line(grid, 1, 0, 6, 0);
        line(grid, 3, -7, 3, -1);
        grid.placePipe(4, 1, PipeLayer.BASE, MineralTier.IRON);
        TankValve a = Fluids.valve(100000);
        TankValve b = Fluids.valve(100000);
        TankValve c = Fluids.valve(100000);
        grid.placeValve(3, -8, a);
        grid.placeValve(4, 2, b);
        grid.placeValve(7, 0, c);
        fillAll(grid);
        return new TankValve[]{a, b, c};
    }

    public static void testJunctionsSplitByDestinationsPerFace() {
        PipeGrid grid = grid(Fluids.uniform(1000));
        TankValve[] v = twoJunctions(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        Check.equal(10, grid.getPathDistance(pump, v[0], FluidType.FRESHWATER));
        Check.equal(5, grid.getPathDistance(pump, v[1], FluidType.FRESHWATER));
        Check.equal(6, grid.getPathDistance(pump, v[2], FluidType.FRESHWATER));
        PumpResult result = cycle(grid, pump);
        // J: 3 destinations (north 1, east 2), 20 = 6 each + 2: one unit per destination by face,
        // north first (A), then east (one of B, C). J2: 13 for 2 destinations = 6 each + 1, east (C)
        // before south (B) (N24-3, N25-5, N27-1).
        Check.equal(7, result.getDelivered(v[0]), "A: north face of J");
        Check.equal(6, result.getDelivered(v[1]), "B: south face of J2");
        Check.equal(7, result.getDelivered(v[2]), "C: east face of J2, before south");
    }

    public static void testCompatModeKeepsTheGlobalNearestFirstRemainder() {
        PipeGrid grid = compat(Fluids.uniform(1000));
        TankValve[] v = twoJunctions(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        PumpResult result = cycle(grid, pump);
        // N7-2: 6 each, the remainder to the nearest: B (5), then C (6); A (10) gets none.
        Check.equal(6, result.getDelivered(v[0]), "A: farthest");
        Check.equal(7, result.getDelivered(v[1]), "B: nearest");
        Check.equal(7, result.getDelivered(v[2]), "C: second nearest");
    }

    public static void testPumpFacesTakeTheRemainderNorthEastSouthWest() {
        // Output pipes north, east and west of the pump, each to its own valve at distance 1.
        for (boolean compatMode : new boolean[]{false, true}) {
            PipeGrid grid = compatMode ? compat(Fluids.uniform(1000)) : grid(Fluids.uniform(1000));
            grid.placePipe(0, -1, PipeLayer.BASE, MineralTier.IRON);
            grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.IRON);
            grid.placePipe(-1, 0, PipeLayer.BASE, MineralTier.IRON);
            TankValve north = Fluids.valve(1000);
            TankValve east = Fluids.valve(1000);
            TankValve west = Fluids.valve(1000);
            grid.placeValve(0, -2, north);
            grid.placeValve(2, 0, east);
            grid.placeValve(-2, 0, west);
            fillAll(grid);
            Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
            PumpResult result = cycle(grid, pump);
            if (compatMode) {
                // Equal distances: smaller y, then x (N7-2): north, west, east.
                Check.equal(7, result.getDelivered(north), "compat: north");
                Check.equal(7, result.getDelivered(west), "compat: west before east (x)");
                Check.equal(6, result.getDelivered(east), "compat: east last");
            } else {
                Check.equal(7, result.getDelivered(north), "north first (N25-5, N27-1)");
                Check.equal(7, result.getDelivered(east), "then east");
                Check.equal(6, result.getDelivered(west), "west last");
            }
        }
    }

    public static void testFullTanksDoNotCountAtJunctions() {
        // N24-3 counts the destinations that can take something: a full tank behind the north face
        // leaves the whole amount to the east face.
        PipeGrid grid = grid(Fluids.uniform(1000));
        TankValve[] v = twoJunctions(grid);
        v[0].getTank().insert(FluidType.FRESHWATER, v[0].getTank().getSpaceFor(FluidType.FRESHWATER));
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        PumpResult result = cycle(grid, pump);
        Check.equal(0, result.getDelivered(v[0]), "full");
        Check.equal(10, result.getDelivered(v[1]));
        Check.equal(10, result.getDelivered(v[2]));
    }

    // ---------------------------------------------------------------- N25-1~N25-3

    /** Iron pipes carry 20; empty pipes are reached at most one per 60 ticks. */
    private static final PipeTierRules SLOW = new PipeTierRules() {
        @Override
        public int getTransportAmount(MineralTier tier) {
            return 20;
        }

        @Override
        public int getFillTicksPerBlock(MineralTier tier) {
            return 60;
        }
    };

    public static void testFillSpeedLimitsOnlyEmptyPipes() {
        PipeGrid grid = grid(SLOW);
        line(grid, 1, 0, 5, 0);
        TankValve valve = Fluids.valve(100000);
        grid.placeValve(6, 0, valve);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        int[] reachedAtCycle = new int[6];
        int deliveries = 0;
        for (int cycle = 1; cycle <= 25; cycle++) {
            PumpResult result = cycle(grid, pump);
            for (int x = 1; x <= 5; x++) {
                if (reachedAtCycle[x] == 0 && grid.getPipe(x, 0, PipeLayer.BASE).isReached()) {
                    reachedAtCycle[x] = cycle;
                }
            }
            if (result.getDelivered(valve) > 0) {
                Check.equal(20, result.getDelivered(valve), "cycle " + cycle + ": a full path passes at full rate (N25-3)");
                deliveries++;
            }
        }
        for (int x = 2; x <= 5; x++) {
            Check.equal(reachedAtCycle[x - 1] + 3, reachedAtCycle[x], "pipe " + x + ": one block per 60 ticks (N25-1)");
        }
        Check.equal(25 - reachedAtCycle[5], deliveries, "every cycle after the line is full delivers");

        // The same line without the speed limit (compatibility mode): one pipe per cycle (the cap).
        PipeGrid old = compat(SLOW);
        line(old, 1, 0, 5, 0);
        old.placeValve(6, 0, Fluids.valve(100000));
        Pump fast = pump(old, 0, 0, PumpTier.FIRE);
        for (int cycle = 1; cycle <= 5; cycle++) {
            cycle(old, fast);
            Check.isTrue(old.getPipe(cycle, 0, PipeLayer.BASE).isFull(), "compat: pipe " + cycle + " in cycle " + cycle);
        }
    }

    public static void testFillSpeedKeepsFillingAReachedPipe() {
        // A pipe already reached is filled whatever the speed; only the next empty one waits.
        PipeTierRules rules = new PipeTierRules() {
            @Override
            public int getTransportAmount(MineralTier tier) {
                return 30;
            }

            @Override
            public int getFillTicksPerBlock(MineralTier tier) {
                return 60;
            }
        };
        PipeGrid grid = grid(rules);
        line(grid, 1, 0, 3, 0);
        grid.placeValve(4, 0, Fluids.valve(1000));
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        Check.equal(20, cycle(grid, pump).getPipeFill(), "the first pipe reached");
        PumpResult second = cycle(grid, pump);
        Check.equal(Status.PUMPED, second.getStatus());
        Check.equal(10, second.getPipeFill(), "the reached pipe filled up, the next one not reached yet");
        Check.isFalse(grid.getPipe(2, 0, PipeLayer.BASE).isReached(), "20 ticks after the first: too early");
        Check.equal(Status.NO_DESTINATION, cycle(grid, pump).getStatus(), "40 ticks: nothing can move yet");
        Check.equal(20, cycle(grid, pump).getPipeFill(), "60 ticks: the next pipe is reached");
    }

    // ---------------------------------------------------------------- N26-4

    /** Pump A (0,0) feeds a trunk (1..9, 0) to valve V (10,0); pump B joins at (3,0) through (3,-1).. */
    private static Pump[] sharedTrunk(PipeGrid grid, int branchLength, TankValve[] valveOut) {
        line(grid, 1, 0, 9, 0);
        line(grid, 3, -branchLength, 3, -1);
        valveOut[0] = Fluids.valve(100000);
        grid.placeValve(10, 0, valveOut[0]);
        fillAll(grid);
        Pump a = pump(grid, 0, 0, PumpTier.FIRE);
        Pump b = pump(grid, 3, -branchLength - 1, PumpTier.FIRE);
        return new Pump[]{a, b};
    }

    public static void testNearerPumpTakesTheSharedCapFirst() {
        // Cap 20 per cycle on the trunk, two pumps of 20 in the same tick. At (3,0): A is 3 steps
        // away, B 2: B goes first (N26-4), though A was connected first.
        PipeGrid grid = grid(Fluids.uniform(20));
        TankValve[] valve = new TankValve[1];
        Pump[] pumps = sharedTrunk(grid, 1, valve);
        Map<Long, PumpResult> results = grid.runTick();
        PumpResult a = results.get(PipeGrid.key(0, 0));
        PumpResult b = results.get(PipeGrid.key(3, -2));
        Check.equal(Status.PUMPED, b.getStatus(), "the nearer pump");
        Check.equal(20, b.getDelivered(valve[0]));
        Check.equal(Status.NO_DESTINATION, a.getStatus(), "the cap is used up");
        Check.equal(0, pumps[0].getAmount(), "nothing pulled");

        PipeGrid old = compat(Fluids.uniform(20));
        sharedTrunk(old, 1, valve);
        Map<Long, PumpResult> oldResults = old.runTick();
        Check.equal(Status.PUMPED, oldResults.get(PipeGrid.key(0, 0)).getStatus(), "compat: the first to run");
        Check.equal(Status.NO_DESTINATION, oldResults.get(PipeGrid.key(3, -2)).getStatus());
    }

    public static void testEqualDistanceGoesByConnectionOrder() {
        // B's branch is 2 long: both are 3 steps from (3,0); A was connected first (N26-4).
        PipeGrid grid = grid(Fluids.uniform(20));
        TankValve[] valve = new TankValve[1];
        sharedTrunk(grid, 2, valve);
        Map<Long, PumpResult> results = grid.runTick();
        Check.equal(Status.PUMPED, results.get(PipeGrid.key(0, 0)).getStatus(), "A: connected first");
        Check.equal(Status.NO_DESTINATION, results.get(PipeGrid.key(3, -3)).getStatus());
    }

    // ---------------------------------------------------------------- N23-2

    /** Pump (0,0), line (1..40, 0) over regions 0, 1 and 2, valve (41, 0), all full. */
    private static PipeGrid longLine(TankValve[] valveOut, Pump[] pumpOut) {
        return longLine(valveOut, pumpOut, 20);
    }

    private static PipeGrid longLine(TankValve[] valveOut, Pump[] pumpOut, int capacity) {
        PipeGrid grid = grid(Fluids.uniform(capacity));
        line(grid, 1, 0, 40, 0);
        valveOut[0] = Fluids.valve(100000);
        grid.placeValve(41, 0, valveOut[0]);
        fillAll(grid);
        pumpOut[0] = pump(grid, 0, 0, PumpTier.FIRE);
        return grid;
    }

    private static void unloadMiddle(PipeGrid grid, PipeNode[] held) {
        for (int x = 16; x < 32; x++) {
            held[x] = grid.unloadPipe(x, 0, PipeLayer.BASE);
        }
    }

    private static void loadMiddle(PipeGrid grid, PipeNode[] held) {
        for (int x = 16; x < 32; x++) {
            PipeNode node = held[x];
            grid.loadPipe(x, 0, PipeLayer.BASE, node.getTier(), node.getLinks(), node.getFluid(), node.getAmount(), true,
                    node.getHintDestinations(), node.getHintCodes());
        }
    }

    public static void testSummaryCarriesThroughAnUnloadedRegion() {
        TankValve[] valve = new TankValve[1];
        Pump[] pump = new Pump[1];
        PipeGrid grid = longLine(valve, pump);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "a normal cycle");
        Check.equal(1, grid.getSummaries().size(), "summarized (N23-2)");
        Check.equal(3, grid.getSummaries().get(0).runs.length, "three regions passed through");
        PipeNode[] held = new PipeNode[32];
        unloadMiddle(grid, held);
        Check.isNull(grid.getPipe(20, 0, PipeLayer.BASE), "no mirror: the region's pipes left the engine");
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "intact and unchanged: works as before");
        Check.equal(40, grid.getPathDistance(pump[0], valve[0], FluidType.FRESHWATER), "the summary's steps count");

        // A dead-end pipe placed next to the path changes no path: the summary holds.
        grid.placePipe(40, 1, PipeLayer.BASE, MineralTier.IRON);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "a leaf touches no destination");
        // A structure change on the path (cut and linked again): no longer the network last seen
        // running normally (N23-2).
        grid.toggleSide(39, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        grid.toggleSide(39, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.equal(Status.NO_DESTINATION, cycle(grid, pump[0]).getStatus(), "the unloaded region is a dead end now");

        // The region loads again: normal again, and summarized again.
        loadMiddle(grid, held);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "loaded: normal");
        Check.equal(1, grid.getSummaries().size());
    }

    public static void testNoSummaryWithoutANormalCycle() {
        TankValve[] valve = new TankValve[1];
        Pump[] pump = new Pump[1];
        PipeGrid grid = longLine(valve, pump);
        PipeNode[] held = new PipeNode[32];
        unloadMiddle(grid, held);
        Check.equal(Status.NO_DESTINATION, cycle(grid, pump[0]).getStatus(), "never ran normally: a dead end (N14-3)");
        loadMiddle(grid, held);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]));
    }

    public static void testUnloadedStretchThatWasNotFullIsADeadEnd() {
        TankValve[] valve = new TankValve[1];
        Pump[] pump = new Pump[1];
        PipeGrid grid = longLine(valve, pump, 40);
        // Empty one pipe of the middle region: the front is there.
        grid.removePipe(20, 0, PipeLayer.BASE);
        grid.placePipe(20, 0, PipeLayer.BASE, MineralTier.IRON);
        Check.equal(20, cycle(grid, pump[0]).getPipeFill(), "fills the empty pipe");
        PipeNode[] held = new PipeNode[32];
        unloadMiddle(grid, held);
        Check.equal(Status.NO_DESTINATION, cycle(grid, pump[0]).getStatus(), "not full when last seen: a dead end");
        Check.equal(20, held[20].getAmount(), "never written while unloaded");
    }

    public static void testSummariesAreRefreshedWhileRegionsAreLoaded() {
        // A route over two unloaded regions in a row (N15-1 ②): both skipped while the summary holds.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 60, 0);
        TankValve valve = Fluids.valve(100000);
        grid.placeValve(61, 0, valve);
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        cycle(grid, pump);
        PipeNode[] held = new PipeNode[64];
        for (int x = 16; x < 48; x++) {
            held[x] = grid.unloadPipe(x, 0, PipeLayer.BASE);
        }
        Check.equal(20, cycle(grid, pump).getDelivered(valve), "two unloaded regions in a row skipped");
        for (int x = 32; x < 48; x++) {
            grid.loadPipe(x, 0, PipeLayer.BASE, held[x].getTier(), held[x].getLinks(), held[x].getFluid(),
                    held[x].getAmount(), true, held[x].getHintDestinations(), held[x].getHintCodes());
        }
        Check.equal(20, cycle(grid, pump).getDelivered(valve), "one loaded again, one still skipped");
        Check.equal(4, grid.getSummaries().get(0).runs.length);
    }

    // ---------------------------------------------------------------- hints (N22-3, N25-1, N25-4, N25-6)

    public static void testStructureChangeMarksOnlyTheDestinationsItTouches() {
        // A trunk with two branches to valves V1 (west branch) and V2 (east branch).
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 10, 0);
        line(grid, 3, 1, 3, 4);
        line(grid, 8, 1, 8, 4);
        grid.placeValve(3, 5, Fluids.valve(1000));
        grid.placeValve(8, 5, Fluids.valve(1000));
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        cycle(grid, pump);
        Check.equal(0, grid.getStaleDestinations().size(), "searched");
        // A dead-end pipe next to the west branch: no path changes, it takes its neighbour's hints.
        grid.placePipe(2, 3, PipeLayer.BASE, MineralTier.IRON);
        Check.equal(0, grid.getStaleDestinations().size(), "a leaf marks nothing (N25-4)");
        Check.equal(Direction.EAST.ordinal(), grid.getPipe(2, 3, PipeLayer.BASE).getHint(PipeGrid.key(3, 5)),
                "the leaf steps to its only neighbour");
        // The last pipe before V1 removed: only V1's paths crossed it.
        grid.removePipe(3, 4, PipeLayer.BASE);
        Check.equal(java.util.Collections.singleton(PipeGrid.key(3, 5)), grid.getStaleDestinations(),
                "only V1 (N25-4)");
        int before = grid.searches;
        cycle(grid, pump);
        Check.equal(before + 1, grid.searches, "searched again when used: one destination");
        // A cut in the trunk between the branches: V2's paths from the pump cross it.
        grid.toggleSide(5, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.isTrue(grid.getStaleDestinations().contains(PipeGrid.key(8, 5)), "V2 marked");
    }

    public static void testHintsAreRepairedWhenUsed() {
        // A loop: the short way (y=0) and a long way (around y=-2). Cutting the short way moves the
        // path to the long way at the next cycle.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 6, 0);
        line(grid, 1, -2, 6, -2);
        grid.placePipe(1, -1, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(6, -1, PipeLayer.BASE, MineralTier.IRON);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(7, 0, valve);
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        Check.equal(6, grid.getPathDistance(pump, valve, FluidType.FRESHWATER), "the short way");
        grid.toggleSide(3, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.equal(10, grid.getPathDistance(pump, valve, FluidType.FRESHWATER), "around the cut");
        Check.equal(20, cycle(grid, pump).getDelivered(valve));

        // Hints that lie (saved at another time, N25-6): stepping finds the loop and repairs them.
        PipeNode corner = grid.getPipe(1, -1, PipeLayer.BASE);
        corner.setHint(PipeGrid.key(7, 0), Direction.SOUTH.ordinal());
        grid.getPipe(1, 0, PipeLayer.BASE).setHint(PipeGrid.key(7, 0), Direction.NORTH.ordinal());
        Check.equal(20, cycle(grid, pump).getDelivered(valve), "repaired when used");
        Check.equal(10, grid.getPathDistance(pump, valve, FluidType.FRESHWATER));
    }

    public static void testNewValveIsFoundAtTheNextCycleAndARemovedOneDropsItsHints() {
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 5, 0);
        TankValve first = Fluids.valve(1000);
        grid.placeValve(6, 0, first);
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        cycle(grid, pump);
        Check.equal(5, grid.getHintCount(), "one hint per pipe");
        TankValve second = Fluids.valve(1000);
        grid.placeValve(3, 1, second);
        PumpResult result = cycle(grid, pump);
        Check.equal(10, result.getDelivered(second), "found at the next cycle");
        Check.equal(10, grid.getHintCount(), "its hints on every pipe that leads to it");
        grid.removeValve(3, 1);
        Check.equal(5, grid.getHintCount(), "a removed valve's hints are dropped");
        Check.equal(20, cycle(grid, pump).getDelivered(first));
    }

    public static void testSavedHintsNeedNoSearchOnLoad() {
        // N25-1, N25-6: a world saved with its hints loads without searching again.
        PipeGrid first = grid(Fluids.uniform(20));
        line(first, 1, 0, 8, 0);
        line(first, 4, 1, 4, 3);
        first.placeValve(9, 0, Fluids.valve(10000));
        first.placeValve(4, 4, Fluids.valve(10000));
        fillAll(first);
        Pump pump = pump(first, 0, 0, PumpTier.FIRE);
        cycle(first, pump);

        PipeGrid loaded = grid(Fluids.uniform(20));
        TankValve a = Fluids.valve(10000);
        TankValve b = Fluids.valve(10000);
        loaded.loadValve(9, 0, a);
        for (PipeNode node : first.getPipes()) {
            loaded.loadPipe(node.getTileX(), node.getTileY(), node.getLayer(), node.getTier(), node.getLinks(),
                    node.getFluid(), node.getAmount(), true, node.getHintDestinations(), node.getHintCodes());
        }
        loaded.loadValve(4, 4, b);
        Pump again = new Pump(PumpTier.FIRE);
        again.setTileSource(LiquidTileSource.infinite(FluidType.FRESHWATER));
        again.setFuelSupply(new Fluids.Logs(10));
        again.setSourceSlots(pump.getSourceSlots());
        again.setLastPushedFluid(pump.getLastPushedFluid());
        loaded.loadPump(0, 0, again);
        PumpResult result = cycle(loaded, again);
        Check.equal(0, loaded.searches, "no search on the first cycle after loading (N25-1)");
        Check.equal(10, result.getDelivered(a));
        Check.equal(10, result.getDelivered(b));

        // A world saved before hints existed: its valves are searched at the first cycle.
        PipeGrid migrated = grid(Fluids.uniform(20));
        migrated.loadValve(9, 0, Fluids.valve(10000));
        for (PipeNode node : first.getPipes()) {
            migrated.loadPipe(node.getTileX(), node.getTileY(), node.getLayer(), node.getTier(), node.getLinks(),
                    node.getFluid(), node.getAmount(), true);
        }
        Pump third = pump(migrated, 0, 0, PumpTier.FIRE);
        cycle(migrated, third);
        Check.equal(1, migrated.searches, "searched once");
    }

    public static void testLoadedRegionMarksOnlyDisagreeingHints() {
        // A pipe loaded with hints that agree with its neighbours marks nothing; one that misses a
        // destination its neighbour has marks that destination.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 6, 0);
        grid.placeValve(7, 0, Fluids.valve(1000));
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        cycle(grid, pump);
        PipeNode held = grid.unloadPipe(3, 0, PipeLayer.BASE);
        grid.loadPipe(3, 0, PipeLayer.BASE, held.getTier(), held.getLinks(), held.getFluid(), held.getAmount(), true,
                held.getHintDestinations(), held.getHintCodes());
        Check.equal(0, grid.getStaleDestinations().size(), "agreeing hints: nothing to search");
        held = grid.unloadPipe(3, 0, PipeLayer.BASE);
        grid.loadPipe(3, 0, PipeLayer.BASE, held.getTier(), held.getLinks(), held.getFluid(), held.getAmount(), true,
                new long[0], new byte[0]);
        Check.equal(java.util.Collections.singleton(PipeGrid.key(7, 0)), grid.getStaleDestinations(),
                "a hint missing next to its neighbours' (N25-6)");
    }

    // ---------------------------------------------------------------- structure and systems

    public static void testWrenchNeverChangesAnUnloadedTile() {
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 0, 0, 1, 0);
        grid.unloadPipe(1, 0, PipeLayer.BASE);
        Check.equal(PipeGrid.Check.NOT_LOADED, grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST),
                "toward an unloaded tile");
        Check.isTrue(grid.getPipe(0, 0, PipeLayer.BASE).isSideOpen(Direction.EAST), "own flag unchanged");
        Check.equal(PipeGrid.Check.NOT_LOADED, grid.toggleVertical(1, 0), "an unloaded tile");
    }

    public static void testClicksRunInTheNextTick() {
        // N22-5: the manual pump's click is queued and runs in the systems' tick.
        PipeGrid grid = grid(Fluids.uniform(20));
        Pump pump = new Pump(PumpTier.MANUAL);
        pump.setTileSource(LiquidTileSource.infinite(FluidType.FRESHWATER));
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.IRON);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(2, 0, valve);
        Check.isTrue(grid.runTick().isEmpty(), "no click, no cycle");
        grid.queueClick(0, 0);
        Map<Long, PumpResult> results = grid.runTick();
        Check.equal(Status.PUMPED, results.get(PipeGrid.key(0, 0)).getStatus(), "the click ran");
        grid.queueClick(0, 0);
        Check.equal(Status.WAITING, grid.runTick().get(PipeGrid.key(0, 0)).getStatus(), "click cooldown (N3-3)");
    }

    public static void testSystemsJudgeTheTileSourceBeforeTheCycle() {
        // N20-7 in the source system: an unjudged liquid tile source is judged in the tick.
        PipeGrid grid = grid(Fluids.uniform(20));
        Fluids.Liquids lake = new Fluids.Liquids("fffff", "fffff", "fffff", "fffff", "fffff");
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setTileSource(tile);
        pump.setFuelSupply(new Fluids.Logs(10));
        grid.placePump(2, 2, pump);
        Check.isNull(tile.getJudgment(), "not judged yet");
        grid.runTick();
        Check.equal(Boolean.TRUE, tile.getJudgment(), "judged by the systems");
    }

}
