package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

/**
 * Pumping through pipe networks: the push and frontier updates (N7-1), distribution (N7-2), the
 * stop rule (N7-4), tier restrictions (12-1, 12-5, 12-6), fuel and timing (N3, N6).
 */
final class PumpPushTest {

    private PumpPushTest() {
    }

    // ---------- frontier (N7-1) ----------

    public static void testOnlyFrontierPipesAreUpdated() {
        // Pump at (0,0), ten pipes (1..10, 0) holding 10 each, valve at (11, 0).
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 10, 0, MineralTier.COPPER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(11, 0, valve);

        for (int cycle = 1; cycle <= 5; cycle++) {
            PumpResult result = Fluids.cycle(pump);
            Check.equal(Status.PUMPED, result.getStatus(), "cycle " + cycle);
            Check.equal(20, result.getPipeFill(), "cycle " + cycle + " fills pipes");
            Check.equal(2, result.getPipesUpdated(), "cycle " + cycle + ": only the two frontier pipes");
            Check.equal(0, result.getDelivered(valve), "cycle " + cycle + ": not there yet");
        }
        for (int cycle = 6; cycle <= 8; cycle++) {
            PumpResult result = Fluids.cycle(pump);
            Check.equal(0, result.getPipesUpdated(), "cycle " + cycle + ": path full, no pipe written");
            Check.equal(20, result.getDelivered(valve), "cycle " + cycle + ": straight to the tank");
        }
        for (int x = 1; x <= 10; x++) {
            PipeNode node = grid.getPipe(x, 0, PipeLayer.BASE);
            Check.equal(1, node.getWriteCount(), "pipe " + x + " written once, never again");
            Check.equal(10, node.getAmount(), "pipe " + x + " full");
        }
        Check.equal(60, valve.getTank().getAmount());
        Check.equal(0, pump.getAmount(), "nothing left in the pump");
    }

    public static void testPartlyFilledFrontierPipe() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(15));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 4, 0, MineralTier.COPPER);
        grid.placeValve(5, 0, Fluids.valve(1000));
        Fluids.cycle(pump);
        Check.equal(15, grid.getPipe(1, 0, PipeLayer.BASE).getAmount());
        Check.equal(5, grid.getPipe(2, 0, PipeLayer.BASE).getAmount(), "the frontier pipe");
        PumpResult second = Fluids.cycle(pump);
        Check.equal(2, second.getPipesUpdated(), "frontier pipe and the next");
        Check.equal(1, grid.getPipe(1, 0, PipeLayer.BASE).getWriteCount(), "full pipe untouched");
        Check.equal(15, grid.getPipe(2, 0, PipeLayer.BASE).getAmount());
        Check.equal(10, grid.getPipe(3, 0, PipeLayer.BASE).getAmount());
    }

    public static void testDeadEndBranchesAreNotFilled() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        Fluids.baseLine(grid, 1, 1, 1, MineralTier.COPPER);
        Fluids.baseLine(grid, 1, 1, 2, MineralTier.COPPER);
        grid.placeValve(3, 0, Fluids.valve(1000));
        Fluids.cycle(pump);
        Fluids.cycle(pump);
        Check.equal(0, grid.getPipe(1, 1, PipeLayer.BASE).getAmount(), "branch without destination");
        Check.equal(0, grid.getPipe(1, 2, PipeLayer.BASE).getAmount(), "branch without destination");
        Check.equal(FluidType.FRESHWATER, grid.getNetwork(1, 2, PipeLayer.BASE).getFluid(), "still one network fluid");
    }

    public static void testRemovingAPipeRecomputesThePaths() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
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
        Check.equal(10, result.getPipeFill(), "the new pipe is the frontier");
        Check.equal(1, result.getPipesUpdated());
        Check.equal(10, result.getDelivered(valve));
    }

    public static void testPushThroughUndergroundPipes() {
        // Pump -> basic pipe (1,0) -> underground (1,0)..(3,0) -> valve above (3,0).
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        for (int x = 1; x <= 3; x++) {
            grid.placePipe(x, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        }
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(3, 0, valve);
        Check.equal(4, grid.getPathDistance(pump, valve), "1 basic + 3 underground cells");
        Fluids.cycle(pump);
        Fluids.cycle(pump);
        Check.equal(20, Fluids.cycle(pump).getDelivered(valve));
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
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump[] pump = new Pump[1];
        TankValve[] valves = threeDestinations(grid, pump, PumpTier.FIRE);
        Check.equal(1, grid.getPathDistance(pump[0], valves[0]));
        Check.equal(3, grid.getPathDistance(pump[0], valves[1]));
        Check.equal(4, grid.getPathDistance(pump[0], valves[2]));
        PumpResult result = Fluids.cycle(pump[0]);
        Check.equal(7, result.getDelivered(valves[0]), "20 / 3 = 6 r 2: nearest +1");
        Check.equal(7, result.getDelivered(valves[1]), "second nearest +1");
        Check.equal(6, result.getDelivered(valves[2]), "farthest");
        Check.equal(0, result.getPipesUpdated(), "full paths");
    }

    public static void testSingleRemainderUnitGoesToTheNearest() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump[] pump = new Pump[1];
        TankValve[] valves = threeDestinations(grid, pump, PumpTier.ADVANCED_FIRE);
        PumpResult result = Fluids.cycle(pump[0]);
        Check.equal(14, result.getDelivered(valves[0]), "40 / 3 = 13 r 1");
        Check.equal(13, result.getDelivered(valves[1]));
        Check.equal(13, result.getDelivered(valves[2]));
    }

    public static void testEqualDistanceTieBreaksByTile() {
        // Three valves around the pump's only pipe (1,0): all at distance 1.
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
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
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
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
        // Near valve (distance 1) has a full path; the far valve's path is still empty.
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
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

    // ---------- stop rule (N7-4) ----------

    public static void testNoDestinationStopsThePumpWithoutFuel() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.Logs logs = new Fluids.Logs(3);
        pump.setFuelSupply(logs);
        Fluids.baseLine(grid, 1, 5, 0, MineralTier.COPPER);
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus());
        Check.equal(0, logs.consumed, "no log burned");
        Check.equal(0, pump.getBurnTicksLeft(), "no burn time");
        Check.equal(0L, grid.getNetwork(1, 0, PipeLayer.BASE).getTotalAmount(), "nothing pushed into the pipes");
        Check.equal(0, pump.getAmount(), "nothing pulled");

        grid.placeValve(6, 0, Fluids.valve(100));
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "a destination appears");
        Check.equal(1, logs.consumed, "now it burns a log");
    }

    public static void testAllDestinationsFullStopsThePump() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
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
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
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
        Check.equal(0L, grid.getNetwork(1, 0, PipeLayer.BASE).getTotalAmount(), "nothing pushed");
    }

    public static void testNetworkWithAnotherFluidIsNoDestination() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        grid.getPipe(2, 0, PipeLayer.BASE).setContents(FluidType.LAVA, 3);
        grid.placeValve(3, 0, Fluids.valve(100));
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus(), "one fluid per network (12-7)");
    }

    public static void testUndergroundPipeNextToPumpIsNoDestination() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.placeValve(1, 0, Fluids.valve(100));
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus(), "9-9");
    }

    // ---------- tier restrictions (12-1, 12-5, 12-6) ----------

    private static PumpResult pumpOnce(PumpTier tier, FluidType fluid) {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, tier, fluid);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        return tier == PumpTier.MANUAL ? pump.click() : Fluids.cycle(pump);
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
    }

    public static void testTierRestrictionWhenPullingFromATank() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = new Pump(PumpTier.FIRE);
        Fluids.Logs logs = new Fluids.Logs(5);
        pump.setFuelSupply(logs);
        grid.placePump(0, 0, pump);
        TankStorage sourceTank = Fluids.tank(100);
        sourceTank.insert(FluidType.SLIME, 50);
        grid.placeValve(-1, 0, Fluids.valveOf(sourceTank));
        pump.setSource(sourceTank);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        Check.equal(Status.FLUID_NOT_ALLOWED, Fluids.cycle(pump).getStatus(), "fire pump cannot move slime (12-6)");
        Check.equal(50, sourceTank.getAmount(), "tank untouched");
        Check.equal(0, logs.consumed, "no fuel");
    }

    // ---------- sources ----------

    public static void testPumpingOutOfAnAttachedTank() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(5));
        grid.placePump(0, 0, pump);
        TankStorage sourceTank = Fluids.tank(100);
        sourceTank.insert(FluidType.LAVA, 25);
        grid.placeValve(0, 1, Fluids.valveOf(sourceTank));
        pump.setSource(sourceTank);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve target = Fluids.valve(100);
        grid.placeValve(2, 0, target);
        // The source tank's own valve is also linked to the pipe: it must not be a destination.
        grid.placeValve(1, 1, Fluids.valveOf(sourceTank));

        PumpResult first = Fluids.cycle(pump);
        Check.equal(20, first.getMoved());
        Check.equal(10, first.getDelivered(target), "10 into the pipe, 10 delivered");
        Check.equal(5, sourceTank.getAmount(), "pulled 20 of 25");
        PumpResult second = Fluids.cycle(pump);
        Check.equal(5, second.getMoved(), "only 5 left in the source");
        Check.equal(0, sourceTank.getAmount());
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus(), "source empty");
    }

    public static void testPumpWithoutSource() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(5));
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus());
    }

    // ---------- fuel and timing (N3-2, N3-3, N6) ----------

    private static int pumpUntilOutOfFuel(PumpTier tier) {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
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

    public static void testFirePumpRunsEveryTwentyTicks() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(10000));
        int pumpedAt = -1;
        int previous = -1;
        for (int tick = 1; tick <= 61; tick++) {
            if (pump.tick().isPumped()) {
                previous = pumpedAt;
                pumpedAt = tick;
            }
        }
        Check.equal(61, pumpedAt, "cycles at ticks 1, 21, 41, 61");
        Check.equal(41, previous);
    }

    public static void testWireSwitchesTheFirePumpOff() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(10000));
        pump.setEnabled(false);
        for (int i = 0; i < 40; i++) {
            Check.equal(Status.DISABLED, pump.tick().getStatus());
        }
        pump.setEnabled(true);
        Check.equal(Status.PUMPED, pump.tick().getStatus(), "on again (11-3)");
    }

    public static void testManualPumpClickCooldown() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(2, 0, valve);
        Check.equal(20, pump.click().getMoved(), "20 per click (N3-2)");
        Check.equal(Status.WAITING, pump.click().getStatus(), "cooldown (N3-3)");
        for (int i = 0; i < 19; i++) {
            Check.equal(Status.WAITING, pump.tick().getStatus(), "manual pumps never run on their own");
        }
        Check.equal(Status.WAITING, pump.click().getStatus(), "19 ticks");
        pump.tick();
        Check.equal(Status.PUMPED, pump.click().getStatus(), "20 ticks");
        Check.equal(30, valve.getTank().getAmount(), "10 + 20 (the first click filled the pipe with 10)");
    }

    public static void testPumpKindsAndControls() {
        Check.throwsException(IllegalStateException.class, () -> new Pump(PumpTier.MANUAL).setEnabled(false));
        Check.throwsException(IllegalStateException.class, () -> new Pump(PumpTier.FIRE).click());
        Check.throwsException(UnsupportedOperationException.class, () -> new Pump(PumpTier.ELECTRIC));
        Check.equal(40, new Pump(PumpTier.ADVANCED_FIRE).getCapacity(), "holds one cycle");
        Check.throwsException(IllegalStateException.class, () -> new Pump(PumpTier.MANUAL).click());
    }

}
