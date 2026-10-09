package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

/**
 * The baseline fluid of a pump and its dormant source (N20-3~N20-5, N36-53): the fluid in the output
 * cell, the basic pipe in front of the pump, decides what is pulled; with an empty output cell, the
 * source's fluid. A source of another fluid, or of a fluid the tier cannot move, stays linked and
 * gives nothing. One source per pump (N36-6): the several sources of N16-3 and N17-1 are gone (N36-8).
 */
final class PumpBaselineTest {

    private PumpBaselineTest() {
    }

    /** A valve of a new, empty tank. */
    private static TankValve emptyValve() {
        return Fluids.valve(1000);
    }

    /** A valve pump at (0,0) facing east whose source is {@code behind}, placed at (-1,0) (N36-3). */
    private static Pump valvePump(PipeGrid grid, PumpTier tier, TankValve behind) {
        grid.placeValve(-1, 0, behind);
        Pump pump = Fluids.valvePump(grid, 0, 0, tier, Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(100));
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "the valve behind it is its source");
        return pump;
    }

    // ---------- N20-3, N20-5: the output cell's fluid ----------

    public static void testSourceRefilledWithAnotherFluidIsDormant() {
        // Water in the output cell; the tank behind the pump gives its water, then is filled with lava.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve tank = emptyValve();
        Pump pump = valvePump(grid, PumpTier.FIRE, tank);
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER, 5);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(4, 0, target);
        tank.getTank().insert(FluidType.FRESHWATER, 40);

        PumpResult first = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, first.getStatus());
        Check.equal(FluidType.FRESHWATER, first.getFluid(), "the output cell holds water: water is the baseline");
        Check.equal(20, first.getMoved());
        Fluids.cycle(pump);
        Check.equal(0, tank.getTank().getAmount(), "the water is used up");
        PumpResult empty = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, empty.getStatus());
        Check.equal(PumpResult.Detail.SOURCE_EMPTY, empty.getDetail());

        tank.getTank().insert(FluidType.LAVA, 40);
        PumpResult lava = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, lava.getStatus(), "the lava source is dormant (N20-3, N17-3)");
        Check.equal(PumpResult.Detail.SOURCE_OTHER_FLUID, lava.getDetail());
        Check.equal(40, tank.getTank().getAmount(), "nothing pulled from it");
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "the dormant source stays linked (N20-3)");
        Check.equal(FluidType.FRESHWATER, grid.getPipe(1, 0, PipeLayer.BASE).getFluid(), "no lava entered");
    }

    public static void testEmptyOutputCellTakesTheSourcesFluidAsTheBaseline() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve tank = emptyValve();
        Pump pump = valvePump(grid, PumpTier.FIRE, tank);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(2, 0, target);
        tank.getTank().insert(FluidType.LAVA, 30);

        PumpResult first = Fluids.cycle(pump);
        Check.equal(FluidType.LAVA, first.getFluid(), "empty output cell: the source's fluid (N20-5, N36-53)");
        Check.equal(FluidType.LAVA, grid.getPipe(1, 0, PipeLayer.BASE).getFluid(), "lava entered the output cell");
        Check.equal(10, Fluids.cycle(pump).getMoved(), "the rest of the lava");
        Check.equal(0, tank.getTank().getAmount());
        tank.getTank().insert(FluidType.FRESHWATER, 100);
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus(),
                "the output cell holds lava: the water in the tank is dormant");
        Check.equal(100, tank.getTank().getAmount(), "untouched");
        Check.equal(30, target.getTank().getAmount() + grid.getPipe(1, 0, PipeLayer.BASE).getAmount(), "only lava moved");
    }

    public static void testBaselineIsReadFromTheOutputCellNotFromTheNetwork() {
        // The output cell (1,0) is empty, the pipe after it holds water and the pump last pushed
        // water: the baseline is still the source's lava, which enters the empty cell and the valve
        // linked to it.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve lava = emptyValve();
        grid.placeValve(-1, 0, lava);
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setDirection(Direction.EAST);
        pump.setForm(PumpForm.VALVE);
        pump.setFuelSupply(new Fluids.Logs(100));
        pump.setLastPushedFluid(FluidType.FRESHWATER);
        grid.placePump(0, 0, pump);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        Fluids.set(grid, 2, 0, PipeLayer.BASE, FluidType.FRESHWATER, 20);
        grid.placeValve(3, 0, Fluids.valve(1000));
        TankValve side = Fluids.valve(1000);
        grid.placeValve(1, 1, side);
        lava.getTank().insert(FluidType.LAVA, 100);

        PumpResult first = Fluids.cycle(pump);
        Check.equal(FluidType.LAVA, first.getFluid(), "per cell: the empty output cell gives no baseline (N20-5)");
        Check.equal(FluidType.LAVA, grid.getPipe(1, 0, PipeLayer.BASE).getFluid());
        Check.equal(FluidType.FRESHWATER, grid.getPipe(2, 0, PipeLayer.BASE).getFluid(), "the water pipe is a dead end for lava");
        Fluids.cycle(pump);
        Check.equal(20, side.getTank().getAmount(), "the lava reaches the valve of the empty cell");
    }

    // ---------- N27-4: a valve switched off by wire is no source ----------

    public static void testWireDisabledValveBlocksPulling() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve source = Fluids.valve(1000);
        source.getTank().insert(FluidType.FRESHWATER, 100);
        grid.placeValve(-1, 0, source);
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        Fluids.Logs logs = new Fluids.Logs(100);
        pump.setFuelSupply(logs);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        TankValve target = Fluids.valve(1000);
        grid.placeValve(3, 0, target);

        source.applyWireSignal(true);
        Check.isFalse(source.isEnabled(), "a wire signal switches the valve off (N11-3)");
        Check.equal(0, pump.getSources().size(), "no source through a switched-off valve (N27-4)");
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "still linked");
        PumpResult off = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, off.getStatus(), "nothing to pull (N27-4)");
        Check.equal(PumpResult.Detail.SOURCE_OFF, off.getDetail());
        Check.equal(100, source.getTank().getAmount(), "nothing pulled while it is off");
        Check.equal(0, logs.consumed, "no log lit");

        source.applyWireSignal(false);
        Check.equal(1, pump.getSources().size(), "a source again");
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        Check.equal(80, source.getTank().getAmount(), "pulled once it is on again");
    }

    // ---------- N20-4: fluids the tier cannot move ----------

    public static void testManualValvePumpOnLavaPullsTheWaterTank() {
        // A manual valve pump standing on lava, with a water tank behind it: the lava tile is not its
        // source (N36-20), so it never stops the pump; the water is pulled.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve water = emptyValve();
        grid.placeValve(-1, 0, water);
        Pump pump = new Pump(PumpTier.MANUAL);
        pump.setDirection(Direction.EAST);
        pump.setForm(PumpForm.VALVE);
        pump.setTileSource(LiquidTileSource.infinite(FluidType.LAVA));
        grid.placePump(0, 0, pump);
        Check.equal(1, pump.getSources().size(), "the water tank only");
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
        PumpResult empty = pump.click();
        Check.equal(Status.NO_SOURCE, empty.getStatus(), "the tank is empty; the lava under it is no source");
        Check.equal(PumpResult.Detail.SOURCE_EMPTY, empty.getDetail());
    }

    public static void testGroundFirePumpIgnoresASlimeValveBehindIt() {
        // N36-20: the slime tank behind a ground pump is not linked; the water tile under it is pulled.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve slime = emptyValve();
        grid.placeValve(-1, 0, slime);
        slime.getTank().insert(FluidType.SLIME, 50);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(1000));

        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, result.getStatus());
        Check.equal(FluidType.FRESHWATER, result.getFluid(), "the tile's water");
        Check.equal(50, slime.getTank().getAmount(), "the slime tank is not linked");
    }

    public static void testOnlyAFluidTheTierCannotMoveStopsWithoutFuel() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve tank = emptyValve();
        grid.placeValve(-1, 0, tank);
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        Fluids.Logs logs = new Fluids.Logs(5);
        pump.setFuelSupply(logs);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(1000));
        tank.getTank().insert(FluidType.SLIME, 50);
        PumpResult slime = Fluids.cycle(pump);
        Check.equal(Status.FLUID_NOT_ALLOWED, slime.getStatus(), "only slime: nothing to move (N20-4)");
        Check.equal(PumpResult.Detail.NONE, slime.getDetail());
        Check.equal(0, logs.consumed, "no log lit");
        tank.getTank().extract(FluidType.SLIME, 50);
        tank.getTank().insert(FluidType.FRESHWATER, 10);
        Check.equal(FluidType.FRESHWATER, Fluids.cycle(pump).getFluid(), "the tank holds water: pumping again");
    }

    public static void testOutputCellOfAFluidTheTierCannotMove() {
        // Lava from elsewhere in the manual pump's output cell: the baseline is lava, which the manual
        // pump cannot move, so its water source is dormant too (N20-4, N20-5).
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.LAVA, 5);
        grid.placeValve(2, 0, Fluids.valve(1000));
        grid.tick();
        Check.equal(Status.FLUID_NOT_ALLOWED, pump.click().getStatus());
        Check.equal(5, grid.getPipe(1, 0, PipeLayer.BASE).getAmount(), "nothing entered");
    }

}
