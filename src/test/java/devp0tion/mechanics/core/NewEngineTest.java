package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

import java.util.Map;

/**
 * The new engine's own rules, which the compatibility comparison leaves out on purpose (N26-1):
 * the split at junctions (N24-3, N25-5, N27-1, N28-5, N28-8, N28-14), the fill speed (N25-1~N25-3,
 * N28-7, N28-9~N28-11), dead-end branches filling (N28-6), the cap order by distance (N26-4) and
 * the network summary for unloaded regions (N23-2, N28-1~N28-4); and its structure: cell hints
 * repaired when used and kept across saves (N22-3, N25-1, N25-4, N25-6, N28-12, N28-13), systems run
 * from one tick (N22-5).
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

    // ---------------------------------------------------------------- N25-1~N25-3, N28-7, N28-9~N28-11

    /** Every pipe carries 20 (the cap per cycle window). */
    private static final PipeTierRules SLOW = Fluids.uniform(20);

    public static void testFillSpeedIsPipesPerCycleProportionalToTheTransportAmount() {
        // N28-7, N28-11: pipes per cycle = transport amount / 4 (provisional constant): iron's 80 gives
        // the 20 pipes per cycle of before; at least one pipe.
        Check.equal(20, PipeTierRules.TABLE.getFillCellsPerCycle(MineralTier.IRON), "iron");
        Check.equal(10, Fluids.uniform(40).getFillCellsPerCycle(MineralTier.COPPER), "transport 40");
        Check.equal(1, Fluids.uniform(2).getFillCellsPerCycle(MineralTier.COPPER), "never below one pipe");
    }

    public static void testFrontReachesOnePipePerCycleAndFullPathsPassAtFullRate() {
        // The cap (N14-2) lets a path reach at most one new pipe per cycle window: a pipe filled from
        // empty used its whole cap. Any speed of one pipe per cycle or more therefore fills like the
        // compatibility mode (no speed limit); a full path passes at full rate (N25-3).
        for (boolean compatMode : new boolean[]{false, true}) {
            PipeGrid grid = compatMode ? compat(SLOW) : grid(SLOW);
            line(grid, 1, 0, 5, 0);
            TankValve valve = Fluids.valve(100000);
            grid.placeValve(6, 0, valve);
            Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
            for (int cycle = 1; cycle <= 5; cycle++) {
                cycle(grid, pump);
                Check.isTrue(grid.getPipe(cycle, 0, PipeLayer.BASE).isFull(), compatMode + ": pipe " + cycle + " in cycle " + cycle);
                if (cycle < 5) {
                    Check.isFalse(grid.getPipe(cycle + 1, 0, PipeLayer.BASE).isReached(), compatMode + ": not further");
                }
            }
            for (int cycle = 6; cycle <= 8; cycle++) {
                Check.equal(20, cycle(grid, pump).getDelivered(valve), "a full path passes at full rate (N25-3)");
            }
        }
    }

    public static void testFillSpeedKeepsFillingAReachedPipe() {
        // A pipe reached in an earlier cycle is filled up, and the front goes on into the next one in
        // the same cycle: the front advances once per pump cycle (N28-7), not on a tick clock.
        PipeGrid grid = grid(Fluids.uniform(30));
        line(grid, 1, 0, 3, 0);
        grid.placeValve(4, 0, Fluids.valve(1000));
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        Check.equal(20, cycle(grid, pump).getPipeFill(), "the first pipe reached");
        PumpResult second = cycle(grid, pump);
        Check.equal(Status.PUMPED, second.getStatus());
        Check.equal(20, second.getPipeFill(), "the reached pipe filled up and the next one reached");
        Check.equal(30, grid.getPipe(1, 0, PipeLayer.BASE).getAmount());
        Check.equal(10, grid.getPipe(2, 0, PipeLayer.BASE).getAmount(), "the next cycle: no tick clock to wait for");
    }

    /** Copper pipes: the front cannot enter them (speed 0, a test value); iron: one per cycle. */
    private static final PipeTierRules NO_COPPER_FRONT = new PipeTierRules() {
        @Override
        public int getTransportAmount(MineralTier tier) {
            return 20;
        }

        @Override
        public int getFillCellsPerCycle(MineralTier tier) {
            return tier == MineralTier.COPPER ? 0 : 1;
        }
    };

    public static void testFrontSpeedIsTheNextPipesTier() {
        // N28-10: in mixed piping the speed of the next pipe the front fills counts.
        PipeGrid grid = grid(NO_COPPER_FRONT);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(3, 0, PipeLayer.BASE, MineralTier.IRON);
        grid.placeValve(4, 0, Fluids.valve(1000));
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        cycle(grid, pump);
        cycle(grid, pump);
        Check.isTrue(grid.getPipe(1, 0, PipeLayer.BASE).isFull(), "the iron pipe filled");
        Check.isFalse(grid.getPipe(2, 0, PipeLayer.BASE).isReached(), "the copper pipe's own speed holds the front");
        // The other way round: copper first, then iron; the iron pipe after it is reached at iron's speed.
        PipeGrid other = grid(new PipeTierRules() {
            @Override
            public int getTransportAmount(MineralTier tier) {
                return 20;
            }

            @Override
            public int getFillCellsPerCycle(MineralTier tier) {
                return tier == MineralTier.IRON ? 0 : 1;
            }
        });
        other.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        other.placePipe(2, 0, PipeLayer.BASE, MineralTier.IRON);
        other.placeValve(3, 0, Fluids.valve(1000));
        Pump second = pump(other, 0, 0, PumpTier.FIRE);
        cycle(other, second);
        cycle(other, second);
        Check.isTrue(other.getPipe(1, 0, PipeLayer.BASE).isFull(), "copper at copper's speed");
        Check.isFalse(other.getPipe(2, 0, PipeLayer.BASE).isReached(), "iron at iron's speed");
    }

    public static void testWhatTheSpeedHoldsBackStaysInThePump() {
        // N28-9: pump (0,0) -> (1,0) -> J (2,0); east (3,0) to valve A (4,0), full; north a copper
        // pipe (2,-1) the front cannot enter (test speed 0) before valve B (2,-2). While filling, J
        // gives each direction 10 (N28-8); B's 10 is not given to A, it stays in the pump.
        PipeGrid grid = grid(NO_COPPER_FRONT);
        line(grid, 1, 0, 3, 0);
        TankValve a = Fluids.valve(100000);
        grid.placeValve(4, 0, a);
        fillAll(grid);
        grid.placePipe(2, -1, PipeLayer.BASE, MineralTier.COPPER);
        TankValve b = Fluids.valve(100000);
        grid.placeValve(2, -2, b);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        PumpResult first = cycle(grid, pump);
        Check.equal(10, first.getDelivered(a), "A: its own direction's amount only");
        Check.equal(0, first.getDelivered(b));
        Check.equal(10, pump.getAmount(), "B's amount stays in the pump");
        PumpResult second = cycle(grid, pump);
        Check.equal(10, second.getDelivered(a), "the next cycle pushes no more because of it (N28-9)");
        Check.equal(10, pump.getAmount(), "the pump pulled only what it pushed");
        Check.isFalse(grid.getPipe(2, -1, PipeLayer.BASE).isReached(), "the front never entered the copper pipe");

        // The compatibility mode has no speed limit: everything goes on.
        PipeGrid old = compat(NO_COPPER_FRONT);
        line(old, 1, 0, 3, 0);
        TankValve oldA = Fluids.valve(100000);
        old.placeValve(4, 0, oldA);
        fillAll(old);
        old.placePipe(2, -1, PipeLayer.BASE, MineralTier.COPPER);
        old.placeValve(2, -2, Fluids.valve(100000));
        Pump oldPump = pump(old, 0, 0, PumpTier.FIRE);
        cycle(old, oldPump);
        Check.isTrue(old.getPipe(2, -1, PipeLayer.BASE).isReached(), "compat: no speed limit");
        Check.equal(0, oldPump.getAmount());
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
        PipeGrid.RouteSummary written = grid.getSummaries().get(0);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "intact and unchanged: works as before");
        Check.equal(40, grid.getPathDistance(pump[0], valve[0], FluidType.FRESHWATER), "the summary's steps count");
        Check.isTrue(written == grid.getSummaries().get(0), "a cycle that skips a stretch writes nothing (N28-2)");

        // N28-1: edits in regions the path does not pass (region (0,1) here) keep the flow going.
        grid.placePipe(5, 20, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(6, 20, PipeLayer.BASE, MineralTier.IRON);
        grid.toggleSide(5, 20, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        grid.removePipe(6, 20, PipeLayer.BASE);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "an edit outside the passed regions");
        // An edit inside a passed region, even one that changes no path (a cut linked again): the
        // entries of that region are invalid, so the flow stops beyond the unloaded stretch.
        grid.toggleSide(39, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        grid.toggleSide(39, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.equal(Status.NO_DESTINATION, cycle(grid, pump[0]).getStatus(), "the unloaded region is a dead end now");
        Check.isTrue(written == grid.getSummaries().get(0), "marked invalid, not written again (N28-2)");

        // The region loads again: a normal run writes the summary again, and it holds once more.
        loadMiddle(grid, held);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "loaded: normal");
        Check.equal(1, grid.getSummaries().size());
        Check.isTrue(written != grid.getSummaries().get(0), "written again by the normal run");
        unloadMiddle(grid, held);
        Check.equal(20, cycle(grid, pump[0]).getDelivered(valve[0]), "intact again");
    }

    public static void testEditInAPassedLoadedRegionWhileTheMiddleIsUnloaded() {
        // N28-1 from the pump's side: a dead-end pipe placed next to the path in region 0, which the
        // path passes, stops the flow beyond the unloaded middle until a normal run.
        TankValve[] valve = new TankValve[1];
        Pump[] pump = new Pump[1];
        PipeGrid grid = longLine(valve, pump);
        cycle(grid, pump[0]);
        PipeNode[] held = new PipeNode[32];
        unloadMiddle(grid, held);
        grid.placePipe(5, 1, PipeLayer.BASE, MineralTier.IRON);
        PumpResult result = cycle(grid, pump[0]);
        Check.equal(0, result.getDelivered(valve[0]), "nothing beyond the unloaded stretch");
        Check.equal(Status.NO_DESTINATION, result.getStatus(), "no destination at all: the dead end is not filled either (N28-6)");
        loadMiddle(grid, held);
        Check.equal(10, cycle(grid, pump[0]).getDelivered(valve[0]), "normal: the dead end takes one direction's amount (N28-8)");
    }

    public static void testRegionChangeNumbersAreKeptForTheSummaries() {
        // N28-1: the numbers the game saves are those of the regions some summary passes; restored
        // with the summary, it holds; without them it would not.
        TankValve[] valve = new TankValve[1];
        Pump[] pump = new Pump[1];
        PipeGrid grid = longLine(valve, pump);
        cycle(grid, pump[0]);
        grid.placePipe(5, 20, PipeLayer.BASE, MineralTier.IRON);
        Map<Long, Integer> saved = grid.getSavedRegionChanges();
        Check.equal(3, saved.size(), "regions (0,0), (1,0), (2,0)");
        Check.isFalse(saved.containsKey(TileBuckets.bucketOf(5, 20)), "a region no summary passes is not saved");
        PipeGrid.RouteSummary summary = grid.getSummaries().get(0);
        Check.isTrue(grid.isIntact(summary), "intact");

        PipeGrid restored = grid(Fluids.uniform(20));
        for (Map.Entry<Long, Integer> entry : saved.entrySet()) {
            restored.loadRegionChange(entry.getKey(), entry.getValue());
        }
        restored.loadSummary(summary);
        Check.isTrue(restored.isIntact(summary), "restored with its numbers");
        PipeGrid without = grid(Fluids.uniform(20));
        without.loadSummary(summary);
        Check.isFalse(without.isIntact(summary), "the numbers belong with the summary");
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

    public static void testJunctionInsideAnUnloadedStretchSplitsAsWhenLoaded() {
        // N28-3: pump (0,5), trunk (1..40,5) to valve A (41,5); at J (20,5) a branch south (20,6..17),
        // which parts at (20,17): west to valve B (18,17), east to valve C (22,17). J and the branch
        // down to (20,15) are in region (1,0), B and C in region (1,1). B and C are nearer than A.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 5, 40, 5);
        line(grid, 20, 6, 20, 17);
        grid.placePipe(19, 17, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(21, 17, PipeLayer.BASE, MineralTier.IRON);
        TankValve a = Fluids.valve(100000);
        TankValve b = Fluids.valve(100000);
        TankValve c = Fluids.valve(100000);
        grid.placeValve(41, 5, a);
        grid.placeValve(18, 17, b);
        grid.placeValve(22, 17, c);
        fillAll(grid);
        Pump pump = pump(grid, 0, 5, PumpTier.FIRE);
        PumpResult loaded = cycle(grid, pump);
        // J: east 1 destination (A), south 2 (B, C): 20 = 6 each + 2, east first: A 7, south 13.
        // (20,17): 13 = 6 each + 1, east first: C 7, B 6 (N24-3, N27-1).
        Check.equal(7, loaded.getDelivered(a));
        Check.equal(6, loaded.getDelivered(b));
        Check.equal(7, loaded.getDelivered(c));
        PipeGrid.RouteSummary toB = null;
        for (PipeGrid.RouteSummary summary : grid.getSummaries()) {
            if (summary.valveX == 18) {
                toB = summary;
            }
        }
        Check.equal(5, toB.runs.length, "cut at regions and after the junctions J and (20,17) (N28-3)");
        for (int x = 16; x < 32; x++) {
            grid.unloadPipe(x, 5, PipeLayer.BASE);
        }
        for (int y = 6; y < 16; y++) {
            grid.unloadPipe(20, y, PipeLayer.BASE);
        }
        PumpResult skipped = cycle(grid, pump);
        Check.equal(7, skipped.getDelivered(a), "J is unloaded: the same split by directions (N28-3)");
        Check.equal(6, skipped.getDelivered(b));
        Check.equal(7, skipped.getDelivered(c));
    }

    public static void testValveInAnUnloadedCellIsADeadEnd() {
        // N28-4: pump (0,5), line (1..10,5) to valve A (11,5); at (5,5) a branch south (5,6..15) to
        // valve B (5,16) in region (0,1). With B's cell unloaded, its amount goes to A.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 5, 10, 5);
        line(grid, 5, 6, 5, 15);
        TankValve a = Fluids.valve(30);
        TankValve b = Fluids.valve(100000);
        grid.placeValve(11, 5, a);
        grid.placeValve(5, 16, b);
        fillAll(grid);
        Pump pump = pump(grid, 0, 5, PumpTier.FIRE);
        PumpResult loaded = cycle(grid, pump);
        Check.equal(10, loaded.getDelivered(a));
        Check.equal(10, loaded.getDelivered(b));
        grid.unloadValve(5, 16);
        Check.equal(20, cycle(grid, pump).getDelivered(a), "B is a dead end: its amount goes to A");
        Check.equal(Status.NO_DESTINATION, cycle(grid, pump).getStatus(), "A full, B unloaded: the pump stops (N7-4)");
    }

    public static void testSkippedStretchMayEndAtALoadedValve() {
        // N23-2, N28-4: line (1..31,5) and valve (32,5) in region (2,0); the stretch of region (1,0)
        // reaches the valve, which is loaded: it takes the fluid.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 5, 31, 5);
        TankValve valve = Fluids.valve(100000);
        grid.placeValve(32, 5, valve);
        fillAll(grid);
        Pump pump = pump(grid, 0, 5, PumpTier.FIRE);
        Check.equal(20, cycle(grid, pump).getDelivered(valve));
        for (int x = 16; x < 32; x++) {
            grid.unloadPipe(x, 5, PipeLayer.BASE);
        }
        Check.equal(20, cycle(grid, pump).getDelivered(valve), "the last output position is loaded");
    }

    // ---------------------------------------------------------------- N28-5, N28-6, N28-8, N28-14

    public static void testVerticalDirectionTakesTheRemainderFirst() {
        // N28-5: pump (0,0) -> (1,0) -> J (2,0). From J: north (2,-1) to valve N (2,-2), east (3,0) to
        // valve E (4,0), and down to the underground pipes (2,0..3) to valve V on (2,3). All full:
        // 20 = 6 each + 2, vertical first, then north: V 7, N 7, E 6.
        PipeGrid grid = grid(Fluids.uniform(1000));
        line(grid, 1, 0, 3, 0);
        grid.placePipe(2, -1, PipeLayer.BASE, MineralTier.IRON);
        for (int y = 0; y <= 3; y++) {
            grid.placePipe(2, y, PipeLayer.UNDERGROUND, MineralTier.IRON);
        }
        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(2, 0), "link J to the underground pipe (N16-4)");
        TankValve north = Fluids.valve(10000);
        TankValve east = Fluids.valve(10000);
        TankValve vertical = Fluids.valve(10000);
        grid.placeValve(2, -2, north);
        grid.placeValve(4, 0, east);
        grid.placeValve(2, 3, vertical);
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        PumpResult result = cycle(grid, pump);
        Check.equal(7, result.getDelivered(vertical), "vertical first (N28-5)");
        Check.equal(7, result.getDelivered(north), "then north");
        Check.equal(6, result.getDelivered(east), "east last");
    }

    public static void testDeadEndBranchesFillToo() {
        // N28-6: pump (0,0), full line (1..5,0) to valve (6,0), and an empty dead-end branch (3,1),
        // (3,2). While it fills, J (3,0) gives each direction one equal amount (N28-8).
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 5, 0);
        TankValve valve = Fluids.valve(100000);
        grid.placeValve(6, 0, valve);
        fillAll(grid);
        grid.placePipe(3, 1, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(3, 2, PipeLayer.BASE, MineralTier.IRON);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        int[] branch = {10, 20, 30, 40};
        for (int cycle = 0; cycle < 4; cycle++) {
            PumpResult result = cycle(grid, pump);
            Check.equal(10, result.getDelivered(valve), "cycle " + cycle + ": the valve's direction");
            Check.equal(branch[cycle], grid.getPipe(3, 1, PipeLayer.BASE).getAmount()
                    + grid.getPipe(3, 2, PipeLayer.BASE).getAmount(), "cycle " + cycle + ": the dead-end direction");
        }
        Check.equal(20, cycle(grid, pump).getDelivered(valve), "the branch is full: everything to the valve");

        // The compatibility mode keeps the old fill of the destinations' paths only (TODO(confirm) G17).
        PipeGrid old = compat(Fluids.uniform(20));
        line(old, 1, 0, 5, 0);
        TankValve oldValve = Fluids.valve(100000);
        old.placeValve(6, 0, oldValve);
        fillAll(old);
        old.placePipe(3, 1, PipeLayer.BASE, MineralTier.IRON);
        Pump oldPump = pump(old, 0, 0, PumpTier.FIRE);
        Check.equal(20, cycle(old, oldPump).getDelivered(oldValve));
        Check.isFalse(old.getPipe(3, 1, PipeLayer.BASE).isReached(), "compat: the dead end stays empty");
    }

    public static void testNoDestinationAtAllStopsThePump() {
        // N7-4 as N28-6 reads it: without any destination the pump stops, empty pipes or not; with
        // one, it fills the empty pipes even when every tank is full.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 3, 0);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        Check.equal(Status.NO_DESTINATION, cycle(grid, pump).getStatus(), "no destination at all");
        Check.isFalse(grid.getPipe(1, 0, PipeLayer.BASE).isReached(), "nothing filled");
        TankValve valve = Fluids.valve(10);
        valve.getTank().insert(FluidType.FRESHWATER, 10);
        grid.placeValve(2, 1, valve);
        PumpResult result = cycle(grid, pump);
        Check.equal(Status.PUMPED, result.getStatus(), "a destination, full: the connected empty pipes still fill");
        Check.equal(20, result.getPipeFill());
        Check.equal(0, result.getDelivered(valve));
    }

    public static void testWhileFillingEachDirectionGetsOneEqualAmount() {
        // N28-8: pump (0,0) -> (1,0) -> J (2,0). East (3,0) to valve A (4,0); south (2,1), (2,2),
        // which parts to valve B (0,2) through (1,2) and valve C (4,2) through (3,2); north an empty
        // dead end (2,-1). All but the dead end full.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 3, 0);
        line(grid, 2, 1, 2, 2);
        grid.placePipe(1, 2, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(3, 2, PipeLayer.BASE, MineralTier.IRON);
        TankValve a = Fluids.valve(100000);
        TankValve b = Fluids.valve(100000);
        TankValve c = Fluids.valve(100000);
        grid.placeValve(4, 0, a);
        grid.placeValve(0, 2, b);
        grid.placeValve(4, 2, c);
        fillAll(grid);
        grid.placePipe(2, -1, PipeLayer.BASE, MineralTier.IRON);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        PumpResult filling = cycle(grid, pump);
        // Filling at J: 3 directions, 20 = 6 each + 2, north then east first: dead end 7, A 7, south 6;
        // (2,2) is not filling: by destinations, 3 each.
        Check.equal(7, grid.getPipe(2, -1, PipeLayer.BASE).getAmount(), "the dead-end direction (N28-8)");
        Check.equal(7, filling.getDelivered(a));
        Check.equal(3, filling.getDelivered(b));
        Check.equal(3, filling.getDelivered(c));
        for (int i = 0; i < 4; i++) {
            cycle(grid, pump);
        }
        Check.isTrue(grid.getPipe(2, -1, PipeLayer.BASE).isFull(), "the dead end filled");
        PumpResult full = cycle(grid, pump);
        // Not filling: by destinations (N24-3): east 1, south 2: A 7, south 13 -> C 7, B 6.
        Check.equal(7, full.getDelivered(a));
        Check.equal(6, full.getDelivered(b));
        Check.equal(7, full.getDelivered(c));
    }

    public static void testJunctionsCountOnlyTheDestinationsTheFluidCarries() {
        // N28-14: a ring of 16 pipes entered at (1,0) from the pump (0,0). Valve A hangs below (4,0),
        // three steps east; valve B above (3,-4), six steps the other way round. At (4,0), B's hint
        // points on east (B is nearer that way from there), but the fluid arriving there carries A
        // only: B is not counted again, and each gets half.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 5, 0);
        line(grid, 5, -4, 5, -1);
        line(grid, 1, -4, 4, -4);
        line(grid, 1, -3, 1, -1);
        TankValve a = Fluids.valve(100000);
        TankValve b = Fluids.valve(100000);
        grid.placeValve(4, 1, a);
        grid.placeValve(3, -5, b);
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        PumpResult result = cycle(grid, pump);
        Check.equal(Direction.EAST.ordinal(), grid.getPipe(4, 0, PipeLayer.BASE).getHint(PipeGrid.key(3, -5)),
                "B's hint at A's junction points on, away from the fluid's way in");
        Check.equal(10, result.getDelivered(a), "A: not split with B at its junction");
        Check.equal(10, result.getDelivered(b));
    }

    public static void testChangeInAFullStretchMarksEveryDestinationOfItsNetwork() {
        // N28-12: a full trunk with branches to V1 and V2. A pipe of V1's branch removed: V2 is
        // searched again too, and a shortcut made inside the full stretch is taken at once.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 10, 0);
        line(grid, 3, 1, 3, 4);
        line(grid, 8, 1, 8, 4);
        grid.placeValve(3, 5, Fluids.valve(1000));
        grid.placeValve(8, 5, Fluids.valve(1000));
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        cycle(grid, pump);
        grid.removePipe(3, 2, PipeLayer.BASE);
        Check.equal(new java.util.HashSet<>(java.util.Arrays.asList(PipeGrid.key(3, 5), PipeGrid.key(8, 5))),
                new java.util.HashSet<>(grid.getStaleDestinations()), "every destination of the network");

        // A detour to V2 and a shortcut placed inside it.
        PipeGrid loop = grid(Fluids.uniform(20));
        line(loop, 1, 0, 2, 0);
        line(loop, 2, -3, 2, -1);
        line(loop, 3, -3, 6, -3);
        line(loop, 6, -2, 6, 0);
        TankValve v = Fluids.valve(100000);
        loop.placeValve(7, 0, v);
        fillAll(loop);
        Pump second = pump(loop, 0, 0, PumpTier.FIRE);
        Check.equal(12, loop.getPathDistance(second, v, FluidType.FRESHWATER), "the detour");
        line(loop, 3, 0, 5, 0);
        Check.equal(6, loop.getPathDistance(second, v, FluidType.FRESHWATER), "the shortcut at once");
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

    public static void testLoadedHintsAreCheckedOnTheirOwnPipe() {
        // N28-13: a loaded pipe's hints are checked on that pipe only; N25-6: a pipe loaded without a
        // destination its neighbour has is how that destination reaches the pipes behind it.
        PipeGrid grid = grid(Fluids.uniform(20));
        line(grid, 1, 0, 6, 0);
        grid.placeValve(7, 0, Fluids.valve(1000));
        fillAll(grid);
        Pump pump = pump(grid, 0, 0, PumpTier.FIRE);
        cycle(grid, pump);
        long dest = PipeGrid.key(7, 0);
        PipeNode held = grid.unloadPipe(3, 0, PipeLayer.BASE);
        grid.loadPipe(3, 0, PipeLayer.BASE, held.getTier(), held.getLinks(), held.getFluid(), held.getAmount(), true,
                held.getHintDestinations(), held.getHintCodes());
        grid.checkLoadedHints();
        Check.equal(0, grid.getStaleDestinations().size(), "agreeing hints: nothing to search");

        held = grid.unloadPipe(3, 0, PipeLayer.BASE);
        grid.loadPipe(3, 0, PipeLayer.BASE, held.getTier(), held.getLinks(), held.getFluid(), held.getAmount(), true,
                new long[]{dest}, new byte[]{(byte) Direction.NORTH.ordinal()});
        grid.checkLoadedHints();
        Check.equal(-1, grid.getPipe(3, 0, PipeLayer.BASE).getHint(dest), "a hint pointing at no pipe is dropped");
        Check.equal(java.util.Collections.singleton(dest), grid.getStaleDestinations(), "and searched again when used");
        Check.equal(20, cycle(grid, pump).getDelivered(grid.getValve(7, 0)), "repaired when used (N25-1)");
        Check.equal(Direction.EAST.ordinal(), grid.getPipe(3, 0, PipeLayer.BASE).getHint(dest));

        held = grid.unloadPipe(3, 0, PipeLayer.BASE);
        grid.loadPipe(3, 0, PipeLayer.BASE, held.getTier(), held.getLinks(), held.getFluid(), held.getAmount(), true,
                new long[0], new byte[0]);
        grid.checkLoadedHints();
        Check.equal(java.util.Collections.singleton(dest), grid.getStaleDestinations(),
                "a destination its neighbours have and it lacks (N25-6)");
        cycle(grid, pump);

        // A hint toward a tile that is not loaded cannot be checked and stays.
        grid.unloadPipe(6, 0, PipeLayer.BASE);
        held = grid.unloadPipe(5, 0, PipeLayer.BASE);
        grid.loadPipe(5, 0, PipeLayer.BASE, held.getTier(), held.getLinks(), held.getFluid(), held.getAmount(), true,
                held.getHintDestinations(), held.getHintCodes());
        grid.checkLoadedHints();
        Check.equal(Direction.EAST.ordinal(), grid.getPipe(5, 0, PipeLayer.BASE).getHint(dest), "kept");
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
