package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

/**
 * Pumping through pipe networks: the push and frontier updates (N7-1), distribution (N7-2), the
 * stop rule (N7-4), the transport cap (N14-2, N18-1, N19-8), pipe breaks (N12-4, N12-5, N14-1),
 * unloaded regions (N14-3, N15-1), tier restrictions (12-1, 12-5, 12-6, N17-5), timed fuel (N18-4)
 * and timing (N3, N6).
 */
final class PumpPushTest {

    private PumpPushTest() {
    }

    // ---------- frontier (N7-1) ----------

    public static void testOnlyFrontierPipesAreUpdated() {
        // Pump at (0,0), five pipes (1..5, 0) holding 20 each, valve at (6, 0).
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 5, 0, MineralTier.COPPER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(6, 0, valve);

        for (int cycle = 1; cycle <= 5; cycle++) {
            PumpResult result = Fluids.cycle(pump);
            Check.equal(Status.PUMPED, result.getStatus(), "cycle " + cycle);
            Check.equal(20, result.getPipeFill(), "cycle " + cycle + " fills pipes");
            Check.equal(1, result.getPipesUpdated(), "cycle " + cycle + ": only the frontier pipe");
            Check.equal(0, result.getDelivered(valve), "cycle " + cycle + ": not there yet");
        }
        for (int cycle = 6; cycle <= 8; cycle++) {
            PumpResult result = Fluids.cycle(pump);
            Check.equal(0, result.getPipesUpdated(), "cycle " + cycle + ": path full, no pipe written");
            Check.equal(20, result.getDelivered(valve), "cycle " + cycle + ": straight to the tank");
        }
        for (int x = 1; x <= 5; x++) {
            PipeNode node = grid.getPipe(x, 0, PipeLayer.BASE);
            Check.equal(1, node.getWriteCount(), "pipe " + x + " written once, never again");
            Check.equal(20, node.getAmount(), "pipe " + x + " full");
        }
        Check.equal(60, valve.getTank().getAmount());
        Check.equal(0, pump.getAmount(), "nothing left in the pump");
    }

    public static void testPartlyFilledFrontierPipe() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(30));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 4, 0, MineralTier.COPPER);
        grid.placeValve(5, 0, Fluids.valve(1000));
        Fluids.cycle(pump);
        Check.equal(20, grid.getPipe(1, 0, PipeLayer.BASE).getAmount(), "the frontier pipe");
        PumpResult second = Fluids.cycle(pump);
        Check.equal(2, second.getPipesUpdated(), "frontier pipe and the next");
        Check.equal(30, grid.getPipe(1, 0, PipeLayer.BASE).getAmount());
        Check.equal(10, grid.getPipe(2, 0, PipeLayer.BASE).getAmount());
        Fluids.cycle(pump);
        Check.equal(2, grid.getPipe(1, 0, PipeLayer.BASE).getWriteCount(), "full pipe untouched");
    }

    public static void testDeadEndBranchesAreNotFilled() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        Fluids.baseLine(grid, 1, 1, 1, MineralTier.COPPER);
        Fluids.baseLine(grid, 1, 1, 2, MineralTier.COPPER);
        grid.placeValve(3, 0, Fluids.valve(1000));
        Fluids.cycle(pump);
        Fluids.cycle(pump);
        Check.equal(0, grid.getPipe(1, 1, PipeLayer.BASE).getAmount(), "branch without destination");
        Check.equal(0, grid.getPipe(1, 2, PipeLayer.BASE).getAmount(), "branch without destination");
        Check.isNull(grid.getNetwork(1, 2, PipeLayer.BASE), "not reached: no network (N13-1)");
    }

    public static void testRemovingAPipeRecomputesThePaths() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(4, 0, valve);
        Fluids.fill(grid, 1, 3, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        Check.equal(20, Fluids.cycle(pump).getDelivered(valve));
        grid.removePipe(2, 0, PipeLayer.BASE);
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus(), "cut off");
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        PumpResult result = Fluids.cycle(pump);
        Check.equal(20, result.getPipeFill(), "the new pipe is the frontier");
        Check.equal(1, result.getPipesUpdated());
        Check.equal(20, Fluids.cycle(pump).getDelivered(valve));
    }

    public static void testPushThroughUndergroundPipes() {
        // Pump -> basic pipe (1,0) -> underground (1,0)..(3,0) -> valve above (3,0).
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.line(grid, 1, 3, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(3, 0, valve);
        Check.equal(-1, grid.getPathDistance(pump, valve, FluidType.FRESHWATER), "vertical link starts cut (N16-4)");
        grid.toggleVertical(1, 0);
        Check.equal(4, grid.getPathDistance(pump, valve, FluidType.FRESHWATER), "1 basic + 3 underground cells");
        for (int i = 0; i < 4; i++) {
            Fluids.cycle(pump);
        }
        Check.equal(20, Fluids.cycle(pump).getDelivered(valve), "same logic underground (N14-4, N19-6)");
    }

    // ---------- distribution (N7-2) ----------

    /** Pump (0,0), full pipes (1..4, 0), valves at distance 1, 3 and 4. */
    private static TankValve[] threeDestinations(PipeGrid grid, Pump[] pumpOut, PumpTier tier) {
        pumpOut[0] = Fluids.fueledPump(grid, 0, 0, tier, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 4, 0, MineralTier.COPPER);
        Fluids.fill(grid, 1, 4, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        TankValve near = Fluids.valve(1000);
        TankValve middle = Fluids.valve(1000);
        TankValve far = Fluids.valve(1000);
        grid.placeValve(1, 1, near);
        grid.placeValve(3, 1, middle);
        grid.placeValve(5, 0, far);
        return new TankValve[]{near, middle, far};
    }

    public static void testEqualSplitWithRemainderToTheNearest() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(40));
        Pump[] pump = new Pump[1];
        TankValve[] valves = threeDestinations(grid, pump, PumpTier.FIRE);
        Check.equal(1, grid.getPathDistance(pump[0], valves[0], FluidType.FRESHWATER));
        Check.equal(3, grid.getPathDistance(pump[0], valves[1], FluidType.FRESHWATER));
        Check.equal(4, grid.getPathDistance(pump[0], valves[2], FluidType.FRESHWATER));
        PumpResult result = Fluids.cycle(pump[0]);
        Check.equal(7, result.getDelivered(valves[0]), "20 / 3 = 6 r 2: nearest +1");
        Check.equal(7, result.getDelivered(valves[1]), "second nearest +1");
        Check.equal(6, result.getDelivered(valves[2]), "farthest");
        Check.equal(0, result.getPipesUpdated(), "full paths");
    }

    public static void testSingleRemainderUnitGoesToTheNearest() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(40));
        Pump[] pump = new Pump[1];
        TankValve[] valves = threeDestinations(grid, pump, PumpTier.ADVANCED_FIRE);
        PumpResult result = Fluids.cycle(pump[0]);
        Check.equal(14, result.getDelivered(valves[0]), "40 / 3 = 13 r 1");
        Check.equal(13, result.getDelivered(valves[1]));
        Check.equal(13, result.getDelivered(valves[2]));
    }

    public static void testEqualDistanceTieBreaksByTile() {
        // Three valves around the pump's only pipe (1,0): all at distance 1.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        TankValve south = Fluids.valve(1000);
        TankValve east = Fluids.valve(1000);
        TankValve north = Fluids.valve(1000);
        grid.placeValve(1, 1, south);
        grid.placeValve(2, 0, east);
        grid.placeValve(1, -1, north);
        PumpResult result = Fluids.cycle(pump);
        Check.equal(7, result.getDelivered(north), "smallest y first");
        Check.equal(7, result.getDelivered(east));
        Check.equal(6, result.getDelivered(south));
    }

    public static void testShareAFullTankCannotTakeGoesToTheOthers() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(40));
        Pump[] pump = new Pump[1];
        TankValve[] valves = threeDestinations(grid, pump, PumpTier.ADVANCED_FIRE);
        valves[0].getTank().insert(FluidType.FRESHWATER, 998);
        PumpResult result = Fluids.cycle(pump[0]);
        Check.equal(2, result.getDelivered(valves[0]), "only room for 2");
        Check.equal(19, result.getDelivered(valves[1]), "13, then 6 of the 12 the near tank could not take");
        Check.equal(19, result.getDelivered(valves[2]));
        Check.equal(40, result.getMoved(), "everything delivered");
    }

    public static void testEachShareFillsItsOwnPathFirst() {
        // Near valve (distance 1) has a full path; the far valve's path is not full yet.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        TankValve near = Fluids.valve(1000);
        TankValve far = Fluids.valve(1000);
        grid.placeValve(1, 1, near);
        grid.placeValve(4, 0, far);
        PumpResult result = Fluids.cycle(pump);
        Check.equal(10, result.getDelivered(near), "near share delivered");
        Check.equal(0, result.getDelivered(far), "far share fills the path");
        Check.equal(10, result.getPipeFill());
        Check.equal(1, result.getPipesUpdated());
    }

    // ---------- transport cap (N14-2, N18-1, N19-8) ----------

    public static void testLowestTransportAmountCapsThePathPerCycle() {
        // Gold (30), copper (10), gold (30): the advanced pump's 40 is capped at 10 per cycle.
        PipeGrid grid = new PipeGrid(Fluids.TIERS);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.ADVANCED_FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.GOLD);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(3, 0, PipeLayer.BASE, MineralTier.GOLD);
        Fluids.fill(grid, 1, 3, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        TankValve valve = Fluids.valve(10000);
        grid.placeValve(4, 0, valve);
        for (int cycle = 1; cycle <= 3; cycle++) {
            PumpResult result = Fluids.cycle(pump);
            Check.equal(10, result.getDelivered(valve), "cycle " + cycle + ": the copper pipe's amount (N14-2)");
            Check.equal(0, pump.getAmount(), "the pump pulls only what can move");
        }
    }

    public static void testPumpsOnOnePathShareTheCap() {
        // Two fire pumps push into the same copper (10) path: together at most 10 per cycle (N18-1).
        PipeGrid grid = new PipeGrid(Fluids.TIERS);
        Pump a = Fluids.fueledPump(grid, 1, 0, PumpTier.FIRE, FluidType.LAVA);
        Pump b = Fluids.fueledPump(grid, 1, 2, PumpTier.FIRE, FluidType.LAVA);
        Fluids.baseLine(grid, 1, 3, 1, MineralTier.COPPER);
        Fluids.fill(grid, 1, 3, 1, PipeLayer.BASE, FluidType.LAVA);
        TankValve valve = Fluids.valve(10000);
        grid.placeValve(4, 1, valve);
        int before = valve.getTank().getAmount();
        PumpResult[] results = Fluids.tickAll(grid, a, b);
        Check.equal(10, results[0].getDelivered(valve), "the first pump takes the cap");
        Check.equal(Status.NO_DESTINATION, results[1].getStatus(), "nothing left for the second this cycle");
        Check.equal(before + 10, valve.getTank().getAmount(), "10 per cycle in total");
    }

    public static void testPumpsAddUpUnderTheCap() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(80));
        Pump a = Fluids.fueledPump(grid, 1, 0, PumpTier.FIRE, FluidType.LAVA);
        Pump b = Fluids.fueledPump(grid, 1, 2, PumpTier.FIRE, FluidType.LAVA);
        Fluids.baseLine(grid, 1, 3, 1, MineralTier.COPPER);
        Fluids.fill(grid, 1, 3, 1, PipeLayer.BASE, FluidType.LAVA);
        TankValve valve = Fluids.valve(10000);
        grid.placeValve(4, 1, valve);
        Fluids.tickAll(grid, a, b);
        Check.equal(40, valve.getTank().getAmount(), "two fire pumps: 20 + 20 (N18-1)");
    }

    public static void testIronPathCarriesFourFirePumps() {
        // Five fire pumps feed one iron line; iron's 80 (N19-8) caps the last pipe at four pumps' worth.
        PipeGrid grid = new PipeGrid(PipeTierRules.TABLE);
        Fluids.baseLine(grid, 0, 5, 0, MineralTier.IRON);
        Fluids.fill(grid, 0, 5, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        Pump[] pumps = new Pump[5];
        for (int i = 0; i < 5; i++) {
            pumps[i] = Fluids.fueledPump(grid, i, -1, PumpTier.FIRE, FluidType.FRESHWATER);
        }
        TankValve valve = Fluids.valve(100000);
        grid.placeValve(6, 0, valve);
        Fluids.tickAll(grid, pumps);
        Check.equal(80, valve.getTank().getAmount(), "4 x 20 = 80 per cycle");
        for (int i = 0; i < 20; i++) {
            Fluids.tickAll(grid, pumps);
        }
        Check.equal(160, valve.getTank().getAmount(), "again 80 the next cycle");
    }

    public static void testSpeedDoesNotDependOnTheNumberOfSources() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(80));
        TankStorage tank = Fluids.tank(1000);
        tank.insert(FluidType.FRESHWATER, 500);
        grid.placeValve(0, 1, Fluids.valveOf(tank));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setTileSource(LiquidTileSource.infinite(FluidType.FRESHWATER));
        pump.setFuelSupply(new Fluids.Logs(10));
        grid.placePump(0, 0, pump);
        Check.equal(2, pump.getSources().size(), "tile and tank");
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(2, 0, target);
        Check.equal(20, Fluids.cycle(pump).getMoved(), "still 20 per cycle (N18-1)");
        Check.equal(500, tank.getAmount(), "pulled from the first source (the tile, N19-1)");
    }

    // ---------- pipe breaks (N12-4, N12-5, N14-1) ----------

    public static void testOnlyTheNewlyReachedPipeBreaksAndOnlyItsShareIsLost() {
        // Copper and iron cannot carry lava here. Valve A next to the full gold entry pipe; valve B
        // behind a copper pipe that the lava has not reached yet.
        PipeGrid grid = new PipeGrid(Fluids.breaking(20, FluidType.LAVA, MineralTier.GOLD));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.LAVA);
        Fluids.Logs logs = new Fluids.Logs(5);
        pump.setFuelSupply(logs);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.GOLD);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(3, 0, PipeLayer.BASE, MineralTier.GOLD);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.LAVA);
        TankValve a = Fluids.valve(1000);
        TankValve b = Fluids.valve(1000);
        grid.placeValve(1, 1, a);
        grid.placeValve(4, 0, b);

        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, result.getStatus());
        Check.equal(10, result.getDelivered(a), "the other share is delivered normally");
        Check.equal(10, result.getLost(), "the share headed into the copper pipe is lost");
        Check.equal(1, result.getBroken().size(), "one pipe broke");
        Check.equal(2, result.getBroken().get(0).getTileX(), "the newly reached copper pipe");
        Check.isNull(grid.getPipe(2, 0, PipeLayer.BASE), "gone (N12-5)");
        Check.isTrue(grid.getPipe(3, 0, PipeLayer.BASE) != null, "no chain (N14-1)");
        Check.isTrue(grid.getPipe(1, 0, PipeLayer.BASE) != null, "the reached pipe stays");
        Check.equal(1, logs.consumed, "fuel used normally");
        Check.equal(0, pump.getAmount(), "nothing left in the pump");
        Check.equal(20, Fluids.cycle(pump).getDelivered(a), "next cycle: everything to A");
    }

    public static void testTheCheckUsesTheNetworksLowestTier() {
        // A copper pipe already holds lava (saved from earlier); the gold pipe after it is judged by
        // the network's lowest tier, copper (N12-4), and breaks.
        PipeGrid grid = new PipeGrid(Fluids.breaking(20, FluidType.LAVA, MineralTier.GOLD));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.LAVA);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.GOLD);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.LAVA);
        grid.placeValve(3, 0, Fluids.valve(1000));
        PumpResult result = Fluids.cycle(pump);
        Check.equal(1, result.getBroken().size(), "the gold pipe breaks");
        Check.equal(20, result.getLost());
    }

    public static void testCarriedFluidDoesNotBreak() {
        PipeGrid grid = new PipeGrid(Fluids.breaking(20, FluidType.LAVA, MineralTier.GOLD));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        grid.placeValve(3, 0, Fluids.valve(1000));
        PumpResult result = Fluids.cycle(pump);
        Check.equal(0, result.getBroken().size(), "copper carries water here");
        Check.equal(20, result.getPipeFill());
    }

    // ---------- unloaded regions (N14-3, N15-1) ----------

    private static PipeGrid unloadedSetup(Pump[] pumpOut, TankValve[] valveOut) {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        pumpOut[0] = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 5, 0, MineralTier.COPPER);
        valveOut[0] = Fluids.valve(1000);
        grid.placeValve(6, 0, valveOut[0]);
        return grid;
    }

    public static void testUnloadedPipeThatIsNotFullIsADeadEnd() {
        Pump[] pump = new Pump[1];
        TankValve[] valve = new TankValve[1];
        PipeGrid grid = unloadedSetup(pump, valve);
        Fluids.fill(grid, 1, 2, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        grid.unloadPipe(3, 0, PipeLayer.BASE);
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump[0]).getStatus(), "N14-3");
        Check.equal(0, grid.getPipe(3, 0, PipeLayer.BASE).getWriteCount(), "never written");
    }

    public static void testFullUnloadedRegionIsSkipped() {
        Pump[] pump = new Pump[1];
        TankValve[] valve = new TankValve[1];
        PipeGrid grid = unloadedSetup(pump, valve);
        Fluids.fill(grid, 1, 5, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        grid.unloadPipe(2, 0, PipeLayer.BASE);
        grid.unloadPipe(3, 0, PipeLayer.BASE);
        int writes = grid.getPipe(2, 0, PipeLayer.BASE).getWriteCount();
        Check.equal(20, Fluids.cycle(pump[0]).getDelivered(valve[0]), "every pipe of the path there is full (N15-1)");
        Check.equal(writes, grid.getPipe(2, 0, PipeLayer.BASE).getWriteCount(), "not written");
    }

    public static void testSeveralUnloadedRegionsInARowAreSkipped() {
        Pump[] pump = new Pump[1];
        TankValve[] valve = new TankValve[1];
        PipeGrid grid = unloadedSetup(pump, valve);
        Fluids.fill(grid, 1, 4, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        grid.unloadPipe(2, 0, PipeLayer.BASE);
        grid.unloadPipe(4, 0, PipeLayer.BASE);
        PumpResult result = Fluids.cycle(pump[0]);
        Check.equal(20, result.getPipeFill(), "continues in the next loaded pipe (N15-1 ②)");
        Check.equal(20, grid.getPipe(5, 0, PipeLayer.BASE).getAmount());
    }

    // ---------- stop rule (N7-4) and dead ends ----------

    public static void testNoDestinationStopsThePumpWithoutLightingALog() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.Logs logs = new Fluids.Logs(3);
        pump.setFuelSupply(logs);
        Fluids.baseLine(grid, 1, 5, 0, MineralTier.COPPER);
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus());
        Check.equal(0, logs.consumed, "no log lit");
        Check.equal(0, pump.getBurnTicksLeft(), "no burn time");
        Check.isNull(grid.getNetwork(1, 0, PipeLayer.BASE), "nothing pushed into the pipes");
        Check.equal(0, pump.getAmount(), "nothing pulled");

        grid.placeValve(6, 0, Fluids.valve(100));
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "a destination appears");
        Check.equal(1, logs.consumed, "now it lights a log");
    }

    public static void testAllDestinationsFullStopsThePump() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.Logs logs = new Fluids.Logs(3);
        pump.setFuelSupply(logs);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve a = Fluids.valve(10);
        TankValve b = Fluids.valve(10);
        a.getTank().insert(FluidType.FRESHWATER, 10);
        b.getTank().insert(FluidType.FRESHWATER, 10);
        grid.placeValve(1, 1, a);
        grid.placeValve(2, 0, b);
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus());
        Check.equal(0, logs.consumed, "no fuel (N7-4)");
        Check.equal(0, grid.getPipe(1, 0, PipeLayer.BASE).getAmount(), "pipes toward full tanks stay empty");
    }

    public static void testUnusableDestinationsCountAsNone() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        TankValve disabled = Fluids.valve(100);
        disabled.setEnabled(false);
        TankValve otherFluid = Fluids.valve(100);
        otherFluid.getTank().insert(FluidType.LAVA, 1);
        TankValve inactive = Fluids.valve(100);
        inactive.getTank().applyStructure(null);
        TankValve noTank = new TankValve();
        grid.placeValve(1, 1, disabled);
        grid.placeValve(1, -1, inactive);
        grid.placeValve(2, 1, otherFluid);
        grid.placeValve(2, -1, noTank);
        Check.equal(4, grid.getLinkedValves(grid.getPipe(1, 0, PipeLayer.BASE)).size()
                + grid.getLinkedValves(grid.getPipe(2, 0, PipeLayer.BASE)).size(), "all four are linked");
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus());
        Check.equal(0, grid.getPipe(1, 0, PipeLayer.BASE).getAmount(), "nothing pushed");
    }

    public static void testPipeWithAnotherFluidIsADeadEnd() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        Fluids.set(grid, 2, 0, PipeLayer.BASE, FluidType.LAVA, 3);
        grid.placeValve(3, 0, Fluids.valve(100));
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus(), "N13-2: the face is a dead end");
    }

    public static void testUndergroundPipeNextToPumpIsNoDestination() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.placeValve(1, 0, Fluids.valve(100));
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus(), "9-9");
    }

    // ---------- tier restrictions (12-1, 12-5, 12-6, N17-5) ----------

    private static PumpResult pumpOnce(PumpTier tier, FluidType fluid) {
        PipeGrid grid = new PipeGrid(Fluids.uniform(40));
        Pump pump = Fluids.fueledPump(grid, 0, 0, tier, fluid);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        if (tier == PumpTier.MANUAL) {
            grid.tick();
            return pump.click();
        }
        return Fluids.cycle(pump);
    }

    public static void testTierRestrictionsOnPush() {
        for (PumpTier tier : new PumpTier[]{PumpTier.MANUAL, PumpTier.FIRE, PumpTier.ADVANCED_FIRE}) {
            for (FluidType fluid : FluidType.values()) {
                Status expected = tier.canPump(fluid) ? Status.PUMPED : Status.FLUID_NOT_ALLOWED;
                Check.equal(expected, pumpOnce(tier, fluid).getStatus(), tier + " " + fluid);
            }
        }
        Check.equal(Status.PUMPED, pumpOnce(PumpTier.MANUAL, FluidType.SEAWATER).getStatus(), "seawater is water (12-5)");
        Check.equal(Status.FLUID_NOT_ALLOWED, pumpOnce(PumpTier.MANUAL, FluidType.LAVA).getStatus(), "12-1");
        Check.equal(Status.FLUID_NOT_ALLOWED, pumpOnce(PumpTier.FIRE, FluidType.SLIME).getStatus(), "12-1");
        Check.equal(Status.FLUID_NOT_ALLOWED, pumpOnce(PumpTier.FIRE, FluidType.CRUDE_OIL).getStatus(), "N17-5");
        Check.equal(Status.PUMPED, pumpOnce(PumpTier.ADVANCED_FIRE, FluidType.CRUDE_OIL).getStatus(), "N17-5");
    }

    public static void testTierRestrictionWhenPullingFromATank() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankStorage sourceTank = Fluids.tank(100);
        sourceTank.insert(FluidType.SLIME, 50);
        grid.placeValve(-1, 0, Fluids.valveOf(sourceTank));
        Pump pump = new Pump(PumpTier.FIRE);
        Fluids.Logs logs = new Fluids.Logs(5);
        pump.setFuelSupply(logs);
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        Check.equal(Status.FLUID_NOT_ALLOWED, Fluids.cycle(pump).getStatus(), "fire pump cannot move slime (12-6)");
        Check.equal(50, sourceTank.getAmount(), "tank untouched");
        Check.equal(0, logs.consumed, "no fuel");
    }

    // ---------- tank sources ----------

    public static void testPumpingOutOfALinkedTank() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankStorage sourceTank = Fluids.tank(100);
        sourceTank.insert(FluidType.LAVA, 25);
        grid.placeValve(0, 1, Fluids.valveOf(sourceTank));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(5));
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve target = Fluids.valve(100);
        grid.placeValve(2, 0, target);
        // The source tank's own valve is also linked to the pipe: it must not be a destination.
        grid.placeValve(1, 1, Fluids.valveOf(sourceTank));

        PumpResult first = Fluids.cycle(pump);
        Check.equal(20, first.getMoved());
        Check.equal(20, first.getPipeFill(), "the pipe takes the first 20");
        Check.equal(5, sourceTank.getAmount(), "pulled 20 of 25");
        PumpResult second = Fluids.cycle(pump);
        Check.equal(5, second.getMoved(), "only 5 left in the source");
        Check.equal(5, second.getDelivered(target));
        Check.equal(0, sourceTank.getAmount());
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus(), "source empty");
    }

    public static void testPumpWithoutSource() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(5));
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus());
    }

    // ---------- fuel (N6, N18-4) and timing (N3) ----------

    private static int pumpUntilOutOfFuel(PumpTier tier) {
        PipeGrid grid = new PipeGrid(Fluids.uniform(40));
        Pump pump = Fluids.pump(grid, 0, 0, tier, FluidType.LAVA);
        Fluids.Logs logs = new Fluids.Logs(1);
        pump.setFuelSupply(logs);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.LAVA);
        TankValve valve = Fluids.valve(100000);
        grid.placeValve(2, 0, valve);
        int cycles = 0;
        while (true) {
            PumpResult result = Fluids.cycle(pump);
            if (result.getStatus() == Status.NO_FUEL) {
                break;
            }
            Check.equal(Status.PUMPED, result.getStatus());
            cycles++;
        }
        Check.equal(5, cycles, tier + ": one log = 100 ticks = 5 cycles");
        Check.equal(1, logs.consumed);
        return valve.getTank().getAmount();
    }

    public static void testOneLogMovesOneHundredWithTheFirePump() {
        Check.equal(100, pumpUntilOutOfFuel(PumpTier.FIRE), "N6-2");
    }

    public static void testOneLogMovesTwoHundredWithTheAdvancedFirePump() {
        Check.equal(200, pumpUntilOutOfFuel(PumpTier.ADVANCED_FIRE), "N6-4");
    }

    public static void testALitLogBurnsOnTimeEvenWhenThePumpStops() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.Logs logs = new Fluids.Logs(5);
        pump.setFuelSupply(logs);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(1000));
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        Check.equal(1, logs.consumed, "lit");
        Check.equal(100, pump.getBurnTicksLeft());
        grid.removeValve(2, 0);
        for (int i = 0; i < 60; i++) {
            Fluids.tickAll(grid, pump);
        }
        Check.equal(40, pump.getBurnTicksLeft(), "burns on time with nothing to do (N18-4)");
        for (int i = 0; i < 60; i++) {
            Fluids.tickAll(grid, pump);
        }
        Check.equal(0, pump.getBurnTicksLeft(), "burnt out");
        Check.equal(1, logs.consumed, "no new log while there is no destination (N7-4)");
        grid.placeValve(2, 0, Fluids.valve(1000));
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        Check.equal(2, logs.consumed, "relit when the pump can run");
    }

    public static void testFirePumpRunsEveryTwentyTicks() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(10000));
        int pumpedAt = -1;
        int previous = -1;
        for (int tick = 1; tick <= 61; tick++) {
            if (Fluids.tickAll(grid, pump)[0].isPumped()) {
                previous = pumpedAt;
                pumpedAt = tick;
            }
        }
        Check.equal(61, pumpedAt, "cycles at ticks 1, 21, 41, 61");
        Check.equal(41, previous);
    }

    public static void testWireSwitchesTheFirePumpOff() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(10000));
        pump.setEnabled(false);
        for (int i = 0; i < 40; i++) {
            Check.equal(Status.DISABLED, Fluids.tickAll(grid, pump)[0].getStatus());
        }
        pump.setEnabled(true);
        Check.equal(Status.PUMPED, Fluids.tickAll(grid, pump)[0].getStatus(), "on again (11-3)");
    }

    public static void testManualPumpClickCooldown() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(2, 0, valve);
        grid.tick();
        Check.equal(20, pump.click().getMoved(), "20 per click (N3-2)");
        Check.equal(Status.WAITING, pump.click().getStatus(), "cooldown (N3-3)");
        for (int i = 0; i < 19; i++) {
            Check.equal(Status.WAITING, Fluids.tickAll(grid, pump)[0].getStatus(), "manual pumps never run on their own");
        }
        Check.equal(Status.WAITING, pump.click().getStatus(), "19 ticks");
        Fluids.tickAll(grid, pump);
        Check.equal(Status.PUMPED, pump.click().getStatus(), "20 ticks");
        Check.equal(20, valve.getTank().getAmount(), "the first click filled the pipe with 20");
    }

    public static void testPumpKindsAndControls() {
        Check.throwsException(IllegalStateException.class, () -> new Pump(PumpTier.MANUAL).setEnabled(false));
        Check.throwsException(IllegalStateException.class, () -> new Pump(PumpTier.FIRE).click());
        Check.throwsException(UnsupportedOperationException.class, () -> new Pump(PumpTier.ELECTRIC));
        Check.equal(40, new Pump(PumpTier.ADVANCED_FIRE).getCapacity(), "holds one cycle");
        Check.throwsException(IllegalStateException.class, () -> new Pump(PumpTier.MANUAL).click());
    }

}
