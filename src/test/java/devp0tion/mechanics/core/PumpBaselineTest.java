package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

import java.util.Arrays;

/**
 * The baseline fluid of a pump and its dormant sources (N20-3~N20-5): the fluid in the output cell
 * decides which sources are pulled from; sources of another fluid, or of a fluid the tier cannot
 * move, stay connected without stopping the pump.
 */
final class PumpBaselineTest {

    private PumpBaselineTest() {
    }

    /** A valve of a new, empty tank. */
    private static TankValve emptyValve() {
        return Fluids.valve(1000);
    }

    /** A fire pump at (0,0) whose sources are the valves north and south of it, connected while empty. */
    private static Pump pumpWithNorthAndSouth(EngineApi grid, PumpTier tier, TankValve north, TankValve south) {
        grid.placeValve(0, -1, north);
        grid.placeValve(0, 1, south);
        Pump pump = new Pump(tier);
        pump.setFuelSupply(new Fluids.Logs(100));
        grid.placePump(0, 0, pump);
        Check.equal(Arrays.asList(Pump.SourceSlot.valve(Direction.NORTH), Pump.SourceSlot.valve(Direction.SOUTH)),
                pump.getSourceSlots(), "both connected while empty (N17-1)");
        return pump;
    }

    // ---------- N20-3, N20-5: the output cell's fluid ----------

    public static void testSourceOfAnotherFluidThanTheOutputCellIsDormant() {
        // QA F5: sources [A north, B south], water already in the pipe; A fills with lava, B with water.
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve a = emptyValve();
        TankValve b = emptyValve();
        Pump pump = pumpWithNorthAndSouth(grid, PumpTier.FIRE, a, b);
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER, 5);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(4, 0, target);
        a.getTank().insert(FluidType.LAVA, 40);
        b.getTank().insert(FluidType.FRESHWATER, 40);

        PumpResult first = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, first.getStatus(), "the pump does not stop for the lava source");
        Check.equal(FluidType.FRESHWATER, first.getFluid(), "the output cell holds water: water is the baseline");
        Check.equal(20, first.getMoved());
        Check.equal(20, b.getTank().getAmount(), "pulled from the water source");
        Check.equal(40, a.getTank().getAmount(), "the lava source is dormant (N20-3)");
        Fluids.cycle(pump);
        Check.equal(0, b.getTank().getAmount(), "the water source is used up");
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus(), "only the dormant lava source is left");
        Check.equal(40, a.getTank().getAmount(), "still nothing pulled from it");
        Check.equal(2, pump.getSourceSlots().size(), "the dormant source stays connected (N20-3)");
        Check.equal(FluidType.FRESHWATER, grid.getPipe(1, 0, PipeLayer.BASE).getFluid(), "no lava entered");
    }

    public static void testOnlySourcesOfTheBaselineAreSummed() {
        // Water in the output cell; sources [water 10, lava 100, water 100]: a cycle of 20 takes the
        // first water source's 10 and 10 from the other water source, nothing from the lava (N17-2).
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve water1 = emptyValve();
        TankValve lava = emptyValve();
        TankValve water2 = emptyValve();
        grid.placeValve(0, -1, water1);
        grid.placeValve(0, 1, lava);
        grid.placeValve(-1, 0, water2);
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(100));
        grid.placePump(0, 0, pump);
        Check.equal(Arrays.asList(Pump.SourceSlot.valve(Direction.NORTH), Pump.SourceSlot.valve(Direction.SOUTH),
                Pump.SourceSlot.valve(Direction.WEST)), pump.getSourceSlots(), "water, lava, water");
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        grid.placeValve(2, 0, Fluids.valve(1000));
        water1.getTank().insert(FluidType.FRESHWATER, 10);
        lava.getTank().insert(FluidType.LAVA, 100);
        water2.getTank().insert(FluidType.FRESHWATER, 100);

        PumpResult result = Fluids.cycle(pump);
        Check.equal(20, result.getMoved());
        Check.equal(0, water1.getTank().getAmount(), "the first water source first (N19-1)");
        Check.equal(100, lava.getTank().getAmount(), "skipped: dormant");
        Check.equal(90, water2.getTank().getAmount(), "then the next water source");
    }

    public static void testEmptyOutputCellTakesTheFirstSourceAndItsFluidBecomesTheBaseline() {
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve lava = emptyValve();
        TankValve water = emptyValve();
        Pump pump = pumpWithNorthAndSouth(grid, PumpTier.FIRE, lava, water);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(2, 0, target);
        lava.getTank().insert(FluidType.LAVA, 30);
        water.getTank().insert(FluidType.FRESHWATER, 100);

        PumpResult first = Fluids.cycle(pump);
        Check.equal(FluidType.LAVA, first.getFluid(), "empty output cell: the first source in pull order (N20-5)");
        Check.equal(FluidType.LAVA, grid.getPipe(1, 0, PipeLayer.BASE).getFluid(), "lava entered the output cell");
        Check.equal(10, Fluids.cycle(pump).getMoved(), "the rest of the lava");
        Check.equal(0, lava.getTank().getAmount());
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus(),
                "the output cell holds lava: the water source is dormant");
        Check.equal(100, water.getTank().getAmount(), "untouched");
        Check.equal(30, target.getTank().getAmount() + grid.getPipe(1, 0, PipeLayer.BASE).getAmount(), "only lava moved");
    }

    public static void testBaselineIsReadFromTheOutputCellNotFromTheNetwork() {
        // The output cell (1,0) is empty, the pipe after it holds water and the pump last pushed
        // water: the baseline is still the first source's lava, which enters the empty cell and the
        // valve linked to it.
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve lava = emptyValve();
        TankValve water = emptyValve();
        grid.placeValve(0, -1, lava);
        grid.placeValve(0, 1, water);
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(100));
        pump.setLastPushedFluid(FluidType.FRESHWATER);
        grid.placePump(0, 0, pump);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        Fluids.set(grid, 2, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        grid.placeValve(3, 0, Fluids.valve(1000));
        TankValve side = Fluids.valve(1000);
        grid.placeValve(1, 1, side);
        lava.getTank().insert(FluidType.LAVA, 100);
        water.getTank().insert(FluidType.FRESHWATER, 100);

        PumpResult first = Fluids.cycle(pump);
        Check.equal(FluidType.LAVA, first.getFluid(), "per cell: the empty output cell gives no baseline (N20-5)");
        Check.equal(FluidType.LAVA, grid.getPipe(1, 0, PipeLayer.BASE).getFluid());
        Check.equal(FluidType.FRESHWATER, grid.getPipe(2, 0, PipeLayer.BASE).getFluid(), "the water pipe is a dead end for lava");
        Check.equal(100, water.getTank().getAmount(), "the water source is dormant from now on");
        Fluids.cycle(pump);
        Check.equal(20, side.getTank().getAmount(), "the lava reaches the valve of the empty cell");
    }

    public static void testOutputCellsOfDifferentFluidsKeepTheFirstSourcesFluid() {
        // N27-2: output cells east (water) and west (lava), and the pump never pushed: it chooses as
        // with empty output cells (first source in pull order) and pushes only where that fluid can go.
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve lava = emptyValve();
        TankValve water = emptyValve();
        Pump pump = pumpWithNorthAndSouth(grid, PumpTier.FIRE, lava, water);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        grid.placeValve(2, 0, Fluids.valve(1000));
        grid.placePipe(-1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, -1, 0, PipeLayer.BASE, FluidType.LAVA, 20);
        TankValve west = Fluids.valve(1000);
        grid.placeValve(-2, 0, west);
        lava.getTank().insert(FluidType.LAVA, 100);
        water.getTank().insert(FluidType.FRESHWATER, 100);

        PumpResult result = Fluids.cycle(pump);
        Check.equal(FluidType.LAVA, result.getFluid(), "the first source's fluid");
        Check.equal(20, west.getTank().getAmount(), "pushed into the lava cell's side");
        Check.equal(100, water.getTank().getAmount());
    }

    public static void testOutputCellsOfDifferentFluidsKeepTheLastPushedFluid() {
        // N27-2: the fluid the pump last pushed stays the baseline while an output cell holds it.
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve lava = emptyValve();
        TankValve water = emptyValve();
        grid.placeValve(0, -1, lava);
        grid.placeValve(0, 1, water);
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(100));
        pump.setLastPushedFluid(FluidType.FRESHWATER);
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        TankValve east = Fluids.valve(1000);
        grid.placeValve(2, 0, east);
        grid.placePipe(-1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, -1, 0, PipeLayer.BASE, FluidType.LAVA, 20);
        TankValve west = Fluids.valve(1000);
        grid.placeValve(-2, 0, west);
        lava.getTank().insert(FluidType.LAVA, 100);
        water.getTank().insert(FluidType.FRESHWATER, 100);

        PumpResult result = Fluids.cycle(pump);
        Check.equal(FluidType.FRESHWATER, result.getFluid(), "the last pushed fluid, held by the east cell (N27-2)");
        Check.equal(20, east.getTank().getAmount(), "pushed into the water cell's side");
        Check.equal(0, west.getTank().getAmount(), "nothing into the lava cell (N13-2)");
        Check.equal(80, water.getTank().getAmount(), "pulled from the water source");
        Check.equal(100, lava.getTank().getAmount(), "the lava source is dormant (N20-3)");
        Fluids.cycle(pump);
        Check.equal(40, east.getTank().getAmount(), "still water while the east cell holds it");

        // No output cell holds water any more: chosen as with empty output cells (first source, lava).
        grid.removePipe(1, 0, PipeLayer.BASE);
        PumpResult after = Fluids.cycle(pump);
        Check.equal(FluidType.LAVA, after.getFluid(), "the first movable source in pull order (N19-1, N27-2)");
        Check.equal(20, west.getTank().getAmount(), "pushed into the lava cell's side");
        Check.equal(80, lava.getTank().getAmount());
    }

    public static void testLastPushedFluidNotInAnyOutputCellChoosesTheFirstSource() {
        // N27-2: the last pushed fluid (slime) is in no output cell: chosen as with empty output cells.
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve lava = emptyValve();
        TankValve water = emptyValve();
        grid.placeValve(0, -1, lava);
        grid.placeValve(0, 1, water);
        Pump pump = new Pump(PumpTier.ADVANCED_FIRE);
        pump.setFuelSupply(new Fluids.Logs(100));
        pump.setLastPushedFluid(FluidType.SLIME);
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        grid.placeValve(2, 0, Fluids.valve(1000));
        grid.placePipe(-1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, -1, 0, PipeLayer.BASE, FluidType.LAVA, 20);
        grid.placeValve(-2, 0, Fluids.valve(1000));
        lava.getTank().insert(FluidType.LAVA, 100);
        water.getTank().insert(FluidType.FRESHWATER, 100);
        Check.equal(FluidType.LAVA, Fluids.cycle(pump).getFluid(), "first source in pull order");
    }

    // ---------- N27-4: a valve switched off by wire is no source ----------

    public static void testWireDisabledValveBlocksPulling() {
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve source = Fluids.valve(1000);
        source.getTank().insert(FluidType.FRESHWATER, 100);
        grid.placeValve(0, -1, source);
        Pump pump = new Pump(PumpTier.FIRE);
        Fluids.Logs logs = new Fluids.Logs(100);
        pump.setFuelSupply(logs);
        grid.placePump(0, 0, pump);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(3, 0, target);

        source.applyWireSignal(true);
        Check.isFalse(source.isEnabled(), "a wire signal switches the valve off (N11-3)");
        Check.equal(0, grid.getSourceValves(pump).size(), "no source through a switched-off valve (N27-4)");
        Check.equal(0, pump.getSources().size(), "the pump sees no source");
        Check.equal(1, pump.getSourceSlots().size(), "still connected");
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus(), "nothing to pull (N27-4)");
        Check.equal(100, source.getTank().getAmount(), "nothing pulled while it is off");
        Check.equal(0, logs.consumed, "no log lit");

        source.applyWireSignal(false);
        Check.equal(1, grid.getSourceValves(pump).size(), "a source again");
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        Check.equal(80, source.getTank().getAmount(), "pulled once it is on again");
    }

    public static void testWireDisabledValveLeavesTheOtherSources() {
        // N27-4: the switched-off valve is skipped like a dormant tank; the next source is used.
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve first = Fluids.valve(1000);
        TankValve second = Fluids.valve(1000);
        first.getTank().insert(FluidType.FRESHWATER, 100);
        second.getTank().insert(FluidType.FRESHWATER, 100);
        Pump pump = pumpWithNorthAndSouth(grid, PumpTier.FIRE, first, second);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        grid.placeValve(3, 0, Fluids.valve(1000));
        first.applyWireSignal(true);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        Check.equal(100, first.getTank().getAmount(), "the switched-off first source is skipped");
        Check.equal(80, second.getTank().getAmount(), "the next source in pull order (N19-1)");
    }

    // ---------- N20-4: fluids the tier cannot move ----------

    public static void testManualPumpOnLavaPullsTheWaterSource() {
        // A manual pump standing on lava, with a tank connected while empty and filled with water
        // later: the lava tile is dormant (12-6, N20-4), the water is pulled.
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve water = emptyValve();
        grid.placeValve(0, 1, water);
        Pump pump = new Pump(PumpTier.MANUAL);
        pump.setTileSource(LiquidTileSource.infinite(FluidType.LAVA));
        grid.placePump(0, 0, pump);
        Check.equal(Arrays.asList(Pump.SourceSlot.TILE, Pump.SourceSlot.valve(Direction.SOUTH)), pump.getSourceSlots(),
                "the lava tile first, then the water tank");
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(2, 0, target);
        water.getTank().insert(FluidType.FRESHWATER, 30);
        grid.tick();

        PumpResult first = pump.click();
        Check.equal(Status.PUMPED, first.getStatus(), "the lava does not stop the manual pump");
        Check.equal(FluidType.FRESHWATER, first.getFluid());
        Check.equal(10, water.getTank().getAmount(), "20 pulled from the water tank");
        for (int i = 0; i < 20; i++) {
            Fluids.tickAll(grid, pump);
        }
        Check.equal(10, pump.click().getMoved(), "the rest of the water");
        for (int i = 0; i < 20; i++) {
            Fluids.tickAll(grid, pump);
        }
        Check.equal(Status.NO_SOURCE, pump.click().getStatus(),
                "water in the output cell, only the lava tile left: dormant");
        Check.equal(2, pump.getSourceSlots().size(), "both stay connected");
    }

    public static void testFirePumpSkipsASlimeTankAndPullsWater() {
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve slime = emptyValve();
        TankValve water = emptyValve();
        Pump pump = pumpWithNorthAndSouth(grid, PumpTier.FIRE, slime, water);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(1000));
        slime.getTank().insert(FluidType.SLIME, 50);
        water.getTank().insert(FluidType.FRESHWATER, 50);

        PumpResult result = Fluids.cycle(pump);
        Check.equal(FluidType.FRESHWATER, result.getFluid(), "slime is dormant for the fire pump (N20-4)");
        Check.equal(50, slime.getTank().getAmount());
        Check.equal(30, water.getTank().getAmount());
    }

    public static void testOnlyFluidsTheTierCannotMoveLeftStopsWithoutFuel() {
        EngineApi grid = Engines.create(Fluids.uniform(20));
        TankValve slime = emptyValve();
        TankValve water = emptyValve();
        grid.placeValve(0, -1, slime);
        grid.placeValve(0, 1, water);
        Pump pump = new Pump(PumpTier.FIRE);
        Fluids.Logs logs = new Fluids.Logs(5);
        pump.setFuelSupply(logs);
        grid.placePump(0, 0, pump);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(1000));
        slime.getTank().insert(FluidType.SLIME, 50);
        Check.equal(Status.FLUID_NOT_ALLOWED, Fluids.cycle(pump).getStatus(), "only slime: nothing to move (N20-4)");
        Check.equal(0, logs.consumed, "no log lit");
        water.getTank().insert(FluidType.FRESHWATER, 10);
        Check.equal(FluidType.FRESHWATER, Fluids.cycle(pump).getFluid(), "the water source fills: pumping again");
    }

    public static void testOutputCellOfAFluidTheTierCannotMove() {
        // Lava from elsewhere in the manual pump's output cell: the baseline is lava, which the manual
        // pump cannot move, so its water source is dormant too (N20-4, N20-5).
        EngineApi grid = Engines.create(Fluids.uniform(20));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.LAVA, 5);
        grid.placeValve(2, 0, Fluids.valve(1000));
        grid.tick();
        Check.equal(Status.FLUID_NOT_ALLOWED, pump.click().getStatus());
        Check.equal(5, grid.getPipe(1, 0, PipeLayer.BASE).getAmount(), "nothing entered");
    }

}
