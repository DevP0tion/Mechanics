package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

import java.util.ArrayList;
import java.util.List;

/**
 * A pump's output side (N36): it links only on its front, and on its back as a valve pump (N36-1,
 * N36-2, N36-3, N36-5); the valve in front is a destination without pipes (N36-4, N36-47, N36-50,
 * N36-56); the baseline with a pipe of another fluid in front (N36-19); several pumps into one tank
 * (N36-49, N36-59); the source tank is no destination (N36-51); and a cheat turning a placed pump
 * (N36-36).
 */
final class PumpFrontTest {

    private PumpFrontTest() {
    }

    /** A fire ground pump at (0,0) facing east on a finite pond of water tiles, with logs counted. */
    private static Pump pondPump(PipeGrid grid, Fluids.Liquids pond, Fluids.Logs logs) {
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setDirection(Direction.EAST);
        pump.setFuelSupply(logs);
        LiquidTileSource tile = new LiquidTileSource(pond, 0, 0);
        tile.judgeArea();
        pump.setTileSource(tile);
        grid.placePump(0, 0, pump);
        return pump;
    }

    // ---------- the valve in front: a destination without pipes (N36-4) ----------

    public static void testFrontValveTakesThePumpsAmountUpToTheTanksRoom() {
        // N36-47: only the pump's amount and the tank's room cap a cycle; no valve tier.
        PipeGrid grid = new PipeGrid(Fluids.uniform(10));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.ADVANCED_FIRE, FluidType.FRESHWATER);
        TankValve valve = Fluids.valve(50);
        grid.placeValve(1, 0, valve);
        Check.equal(0, grid.getPathDistance(pump, valve, FluidType.FRESHWATER), "no pipe on the way");
        PumpResult first = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, first.getStatus());
        Check.equal(40, first.getDelivered(valve), "the advanced fire pump's 40, not a pipe's 10");
        Check.equal(0, first.getPipeFill(), "no pipe filled");
        Check.equal(10, Fluids.cycle(pump).getDelivered(valve), "the tank's room left");
        PumpResult full = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, full.getStatus(), "full (N7-4)");
        Check.equal(PumpResult.Detail.DESTINATION_FULL, full.getDetail());
        Check.equal(50, valve.getTank().getAmount());
        Check.equal(0, grid.getSummaries().size(), "no path to summarize");
    }

    public static void testFrontTankOfAnotherFluidOrSwitchedOffStopsThePump() {
        // N36-56: room 0 while it holds another fluid or is off: no log lit, no liquid tile used up;
        // the pump goes on once there is room.
        Fluids.Liquids pond = new Fluids.Liquids("ffff.");
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Fluids.Logs logs = new Fluids.Logs(10);
        Pump pump = pondPump(grid, pond, logs);
        TankValve valve = Fluids.valve(1000);
        valve.getTank().insert(FluidType.LAVA, 5);
        grid.placeValve(1, 0, valve);
        PumpResult other = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, other.getStatus(), "another fluid in the tank in front (N36-27)");
        Check.equal(PumpResult.Detail.DESTINATION_OTHER_FLUID, other.getDetail());
        Check.equal(0, logs.consumed, "no log lit");
        Check.equal(0, pond.consumed.size(), "no tile used up");
        Check.equal(FluidType.FRESHWATER, pump.cycleFluid(), "the baseline is the source's fluid (N36-46)");

        valve.getTank().extract(FluidType.LAVA, 5);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "room for water: going on");
        Check.equal(1, logs.consumed);

        valve.applyWireSignal(true);
        PumpResult off = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, off.getStatus(), "switched off by a wire signal (N27-4)");
        Check.equal(PumpResult.Detail.DESTINATION_OFF, off.getDetail());
        int used = pond.consumed.size();
        Fluids.cycle(pump);
        Check.equal(used, pond.consumed.size(), "no tile used up while it is off");
        valve.applyWireSignal(false);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "on again");
    }

    public static void testUnloadedFrontValveIsNoDestination() {
        // N36-50 (N28-4): the valve in front in a region that is not loaded is a dead end.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(1, 0, valve);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        grid.unloadValve(1, 0);
        PumpResult unloaded = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, unloaded.getStatus());
        Check.equal(PumpResult.Detail.DESTINATION_MISSING, unloaded.getDetail());
        grid.loadValve(1, 0, valve);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "loaded again");
        Check.equal(40, valve.getTank().getAmount());
    }

    // ---------- the pipe in front (N36-1, N36-19) ----------

    public static void testFrontPipeOfAnotherFluidIsTheBaseline() {
        // N36-19, N20-5: lava in the pipe in front; the water tile under the pump is dormant.
        Fluids.Liquids pond = new Fluids.Liquids("ffff.");
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Fluids.Logs logs = new Fluids.Logs(10);
        Pump pump = pondPump(grid, pond, logs);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.IRON);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.LAVA, 5);
        grid.placeValve(3, 0, Fluids.valve(1000));
        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, result.getStatus(), "the baseline is the pipe's lava");
        Check.equal(PumpResult.Detail.SOURCE_OTHER_FLUID, result.getDetail(), "the water source is dormant (N20-3)");
        Check.equal(0, pond.consumed.size());
        Check.equal(0, logs.consumed);
    }

    public static void testLeftoverFluidFacingAPipeOfAnotherFluid() {
        // N27-3: the fluid left in the pump goes first; a pipe of another fluid in front takes none.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setDirection(Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(10));
        pump.setTileSource(LiquidTileSource.infinite(FluidType.FRESHWATER));
        pump.setContents(FluidType.FRESHWATER, 5);
        grid.loadPump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.IRON);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.LAVA, 5);
        grid.placeValve(2, 0, Fluids.valve(1000));
        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, result.getStatus());
        Check.equal(PumpResult.Detail.DESTINATION_OTHER_FLUID, result.getDetail());
        Check.equal(5, pump.getAmount(), "kept");
    }

    public static void testSidePipesAndValvesAreNotLinked() {
        // N36-2, N36-5, N36-20: a ground pump facing east; pipes and valves on its other sides.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(0, -1, PipeLayer.BASE, MineralTier.COPPER);
        TankValve beyond = Fluids.valve(1000);
        grid.placeValve(0, -2, beyond);
        TankValve side = Fluids.valve(1000);
        grid.placeValve(0, 1, side);
        TankValve behind = Fluids.valve(1000);
        behind.getTank().insert(FluidType.FRESHWATER, 100);
        grid.placeValve(-1, 0, behind);
        Check.isTrue(side.isSideOpen(Direction.NORTH), "no cut start, but no link either");
        Check.equal(0, grid.getPumpEntries(pump).size(), "no pipe in front");
        for (Direction d : Direction.values()) {
            Check.isFalse(grid.isPumpValveLinked(pump, d), "no valve source " + d);
        }
        Check.equal(-1, grid.getPathDistance(pump, beyond, FluidType.FRESHWATER), "the side pipe is no output cell");
        Check.equal(-1, grid.getPathDistance(pump, side, FluidType.FRESHWATER), "the side valve is no destination");
        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, result.getStatus());
        Check.equal(PumpResult.Detail.DESTINATION_MISSING, result.getDetail(), "nothing in front");
        Check.isNull(grid.getPipe(0, -1, PipeLayer.BASE).getFluid(), "the side pipe stays empty");
        Check.equal(100, behind.getTank().getAmount(), "the valve behind a ground pump is untouched");
    }

    // ---------- several pumps into one tank (N36-49, N36-59) ----------

    /**
     * A pump at (0,0) pushing through full pipes (1..3, 0) into one valve (4,0) of a tank of room 30,
     * and a pump at (6,0) facing west into another valve (5,0) of the same tank, placed in the order
     * given.
     */
    private static PumpResult[] twoPumpsIntoOneTank(boolean pipePumpFirst, TankValve[] valves) {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankStorage tank = Fluids.tank(30);
        valves[0] = Fluids.valveOf(tank);
        valves[1] = Fluids.valveOf(tank);
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        Fluids.fill(grid, 1, 3, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        grid.placeValve(4, 0, valves[0]);
        grid.placeValve(5, 0, valves[1]);
        Pump piped;
        Pump direct;
        if (pipePumpFirst) {
            piped = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
            direct = Fluids.fueledPump(grid, 6, 0, PumpTier.FIRE, FluidType.FRESHWATER, Direction.WEST);
        } else {
            direct = Fluids.fueledPump(grid, 6, 0, PumpTier.FIRE, FluidType.FRESHWATER, Direction.WEST);
            piped = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        }
        return Fluids.push(grid, piped, direct);
    }

    public static void testPumpsIntoOneTankGoByInstallNumber() {
        // No shared pipe cell: the room goes in install order (N28-17), whatever the distance.
        TankValve[] valves = new TankValve[2];
        PumpResult[] pipeFirst = twoPumpsIntoOneTank(true, valves);
        Check.equal(20, pipeFirst[0].getDelivered(valves[0]), "placed first, through 3 pipes");
        Check.equal(10, pipeFirst[1].getDelivered(valves[1]), "placed second, direct: the room left");
        PumpResult[] directFirst = twoPumpsIntoOneTank(false, valves);
        Check.equal(20, directFirst[1].getDelivered(valves[1]), "placed first, direct");
        Check.equal(10, directFirst[0].getDelivered(valves[0]), "placed second: the room left");
    }

    // ---------- the source tank (N36-51) ----------

    public static void testTheTankItPullsFromIsNoDestination() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankStorage tank = Fluids.tank(1000);
        tank.insert(FluidType.FRESHWATER, 100);
        grid.placeValve(-1, 0, Fluids.valveOf(tank));
        grid.placeValve(1, 0, Fluids.valveOf(tank));
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(10));
        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, result.getStatus(), "its own source tank in front");
        Check.equal(PumpResult.Detail.DESTINATION_MISSING, result.getDetail());
        Check.equal(100, tank.getAmount());
    }

    // ---------- a cheat turning a placed pump (N36-36) ----------

    public static void testTurningAPumpRebuildsItsLinks() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        final List<String> heard = new ArrayList<>();
        Fluids.baseLine(grid, 1, 40, 0, MineralTier.IRON);
        TankValve east = Fluids.valve(100000);
        grid.placeValve(41, 0, east);
        Fluids.fill(grid, 1, 40, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        grid.placePipe(0, -1, PipeLayer.BASE, MineralTier.IRON);
        TankValve north = Fluids.valve(100000);
        grid.placeValve(0, -2, north);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Check.isTrue(Fluids.cycle(pump).getDelivered(east) > 0, "east");
        Check.equal(1, grid.getSummaries().size(), "summarized (N23-2)");
        Check.isTrue(pump.getNetwork() == grid.getNetwork(1, 0, PipeLayer.BASE), "the east pipes' network");

        grid.setListener((x, y, part) -> heard.add(part + "@" + x + "," + y));
        long region = TileBuckets.bucketOf(0, 0);
        int before = grid.getRegionChange(region);
        grid.setPumpDirection(0, 0, Direction.EAST);
        Check.equal(before, grid.getRegionChange(region), "the same direction: nothing happens");
        grid.setPumpDirection(0, 0, Direction.NORTH);
        Check.equal(Direction.NORTH, pump.getDirection());
        Check.isTrue(grid.getRegionChange(region) > before, "a structure change (N28-1)");
        Check.equal("[PUMP@0,0]", heard.toString(), "synced");
        Check.equal(0, grid.getSummaries().size(), "its summaries go");
        Check.isTrue(pump.getNetwork() != grid.getNetwork(1, 0, PipeLayer.BASE), "left the east network");
        Check.equal(java.util.Collections.singletonList(grid.getPipe(0, -1, PipeLayer.BASE)), grid.getPumpEntries(pump));
        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, result.getStatus());
        Check.equal(20, result.getPipeFill(), "into the north pipe");
        Check.equal(FluidType.FRESHWATER, grid.getPipe(0, -1, PipeLayer.BASE).getFluid());
    }

    public static void testChangingTheFormSwitchesTheSource() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve behind = Fluids.valve(1000);
        behind.getTank().insert(FluidType.FRESHWATER, 100);
        grid.placeValve(-1, 0, behind);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placeValve(1, 0, Fluids.valve(1000));
        Check.equal(1, pump.getSources().size(), "the tile");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "a ground pump: not linked (N36-20)");
        grid.setPumpForm(0, 0, PumpForm.VALVE);
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "a valve pump: the valve behind");
        Check.equal(java.util.Collections.<FluidSource>singletonList(behind.getTank()), pump.getSources());
        Fluids.cycle(pump);
        Check.equal(80, behind.getTank().getAmount(), "pulled from the tank");
    }

}
