package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

import java.util.Arrays;

/**
 * Pump sources: placement by fluid (N17-1), pull order (N19-1), a source that fills with another
 * fluid later (N17-3), valves placed later (N13-3, N16-3), and the liquid tile source (N19-3, N17-5).
 */
final class PumpSourceTest {

    private PumpSourceTest() {
    }

    private static TankValve valveWith(FluidType fluid, int amount) {
        TankValve valve = Fluids.valve(1000);
        if (fluid != null) {
            valve.getTank().insert(fluid, amount);
        }
        return valve;
    }

    // ---------- placement (N17-1) ----------

    public static void testSourcesOfOneFluidOrEmptyMayBeConnected() {
        Check.isTrue(Pump.canConnectSources(Arrays.<FluidType>asList()), "none");
        Check.isTrue(Pump.canConnectSources(Arrays.asList(FluidType.LAVA, null, FluidType.LAVA)), "same or empty");
        Check.isFalse(Pump.canConnectSources(Arrays.asList(FluidType.LAVA, FluidType.SEAWATER)), "mixed");
        Check.isFalse(Pump.canConnectSources(Arrays.asList(FluidType.SEAWATER, FluidType.FRESHWATER)),
                "seawater and freshwater differ (12-2)");
    }

    public static void testPlacementIsRejectedOnlyWhenFluidsWouldMix() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        grid.placeValve(1, 0, valveWith(FluidType.LAVA, 10));
        grid.placeValve(-1, 0, valveWith(null, 0));
        Check.equal(PipeGrid.Check.OK, grid.checkPumpPlacement(0, 0, null), "lava tank and empty tank");
        Check.equal(PipeGrid.Check.OK, grid.checkPumpPlacement(0, 0, FluidType.LAVA), "on lava: same fluid");
        Check.equal(PipeGrid.Check.DIFFERENT_SOURCE_FLUID, grid.checkPumpPlacement(0, 0, FluidType.SEAWATER),
                "on seawater next to a lava tank (N17-1)");
        grid.toggleSide(1, 0, PipeGrid.Part.VALVE, Direction.WEST);
        Check.equal(PipeGrid.Check.OK, grid.checkPumpPlacement(0, 0, FluidType.SEAWATER),
                "the lava valve's side is cut: it would not connect");
    }

    // ---------- pull order (N19-1) and later fluids (N17-3) ----------

    public static void testFirstConnectedSourceFirstThenTheNext() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(40));
        TankValve first = valveWith(FluidType.FRESHWATER, 30);
        TankValve second = valveWith(FluidType.FRESHWATER, 100);
        grid.placeValve(0, -1, first);
        grid.placeValve(-1, 0, second);
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(10));
        grid.placePump(0, 0, pump);
        Check.equal(Arrays.asList(Pump.SourceSlot.valve(Direction.NORTH), Pump.SourceSlot.valve(Direction.WEST)),
                pump.getSourceSlots(), "connected together: north before west");
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        grid.placeValve(2, 0, Fluids.valve(1000));
        Fluids.cycle(pump);
        Check.equal(10, first.getTank().getAmount(), "the first source gives first");
        Check.equal(100, second.getTank().getAmount(), "untouched");
        Fluids.cycle(pump);
        Check.equal(0, first.getTank().getAmount(), "emptied");
        Check.equal(90, second.getTank().getAmount(), "then the next one (N19-1)");
    }

    public static void testEmptySourceFilledWithAnotherFluidStaysConnected() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve water = valveWith(FluidType.FRESHWATER, 40);
        TankValve empty = valveWith(null, 0);
        grid.placeValve(0, -1, water);
        grid.placeValve(-1, 0, empty);
        Pump pump = new Pump(PumpTier.ADVANCED_FIRE);
        pump.setFuelSupply(new Fluids.Logs(10));
        grid.placePump(0, 0, pump);
        Check.equal(2, pump.getSourceSlots().size(), "water and empty connect (N17-1)");
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        grid.placeValve(4, 0, Fluids.valve(1000));

        Fluids.cycle(pump);
        Fluids.cycle(pump);
        Check.equal(FluidType.FRESHWATER, grid.getPipe(2, 0, PipeLayer.BASE).getFluid(), "water pushed");
        Check.equal(0, water.getTank().getAmount(), "the water source is empty");
        empty.getTank().insert(FluidType.LAVA, 500);
        Check.equal(2, pump.getSourceSlots().size(), "still connected (N17-3)");
        PumpResult lava = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, lava.getStatus(),
                "lava next: the only pipes hold water, a dead end (N17-3)");
        grid.removePipe(1, 0, PipeLayer.BASE);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(1, 1, Fluids.valve(1000));
        PumpResult again = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, again.getStatus(), "an empty pipe takes the lava");
        Check.equal(FluidType.LAVA, again.getFluid());
        Check.equal(FluidType.FRESHWATER, grid.getPipe(2, 0, PipeLayer.BASE).getFluid(), "the water pipe beyond is untouched");
    }

    // ---------- the liquid tile source (N19-3) ----------

    public static void testUniformFiveByFiveIsInfinite() {
        Fluids.Liquids lake = new Fluids.Liquids(
                "sssss",
                "sssss",
                "sssss",
                "sssss",
                "sssss");
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        Check.isTrue(tile.isInfinite(), "5x5 of one fluid (N19-3 ①)");
        Check.equal(Integer.MAX_VALUE, tile.getAvailable(FluidType.SEAWATER));
        Check.equal(40, tile.extract(FluidType.SEAWATER, 40));
        Check.equal(0, lake.consumed.size(), "nothing used up");
        Check.equal(0, tile.extract(FluidType.FRESHWATER, 40), "other fluid");
    }

    public static void testOtherwiseTilesAreUsedUpFarthestFirstOwnTileLast() {
        // A 1x4 pond: the pump stands on its west end (0,1).
        Fluids.Liquids pond = new Fluids.Liquids(
                ".....",
                "ffff.",
                ".....");
        LiquidTileSource tile = new LiquidTileSource(pond, 0, 1);
        Check.isFalse(tile.isInfinite(), "not a uniform 5x5");
        Check.equal(40, tile.getAvailable(FluidType.FRESHWATER), "4 tiles x 10 (N2-1)");
        Check.equal(15, tile.extract(FluidType.FRESHWATER, 15));
        Check.equal(2, pond.order.size(), "two tiles for 15");
        Check.equal(3L, pond.order.get(0)[0], "the farthest first");
        Check.equal(2L, pond.order.get(1)[0], "then the next farthest");
        Check.equal(5, tile.getBuffered(), "the rest of the second tile is kept");
        Check.equal(25, tile.extract(FluidType.FRESHWATER, 100), "5 kept + 2 tiles");
        Check.equal(0L, pond.order.get(3)[0], "the pump's own tile last");
        Check.isNull(tile.getSourceFluid(), "used up: no source");
        Check.equal(0, tile.getAvailable(FluidType.FRESHWATER));
    }

    public static void testOnlyConnectedTilesOfTheSameFluidAreUsed() {
        Fluids.Liquids mixed = new Fluids.Liquids(
                "ff.ff",
                "fssss",
                ".....");
        LiquidTileSource tile = new LiquidTileSource(mixed, 1, 1);
        Check.equal(FluidType.SEAWATER, tile.getSourceFluid());
        Check.equal(40, tile.getAvailable(FluidType.SEAWATER), "only the connected seawater (4 tiles)");
        tile.extract(FluidType.SEAWATER, 40);
        Check.equal(4, mixed.consumed.size());
        Check.equal(FluidType.FRESHWATER, mixed.getFluid(0, 0), "freshwater untouched");
    }

    public static void testUnloadedAreaGivesNothing() {
        Fluids.Liquids edge = new Fluids.Liquids(
                "ssssu",
                "sssss",
                "sssss",
                "sssss",
                "sssss");
        LiquidTileSource tile = new LiquidTileSource(edge, 2, 2);
        Check.isNull(tile.getSourceFluid(), "waits while part of the 5x5 is not loaded");
        Check.equal(0, tile.extract(FluidType.SEAWATER, 20));
        Check.equal(0, edge.consumed.size(), "never eats tiles at a region edge");
    }

    public static void testDeepSeaGivesCrudeOil() {
        Check.equal(FluidType.CRUDE_OIL, FluidType.fromPumpedTile("watertile", true, -4), "below -3 (N17-5)");
        Check.equal(FluidType.SEAWATER, FluidType.fromPumpedTile("watertile", true, -3), "-3 is not deep");
        Check.equal(FluidType.FRESHWATER, FluidType.fromPumpedTile("watertile", false, -10), "freshwater unchanged");
        Check.equal(FluidType.LAVA, FluidType.fromPumpedTile("lavatile", false, -10), "other liquids unchanged");
        Fluids.Liquids deep = new Fluids.Liquids(
                "ooooo",
                "ooooo",
                "ooooo",
                "ooooo",
                "ooooo");
        LiquidTileSource tile = new LiquidTileSource(deep, 2, 2);
        Check.equal(FluidType.CRUDE_OIL, tile.getSourceFluid(), "only crude oil");
        Check.isTrue(tile.isInfinite(), "open deep sea");
    }

    public static void testPumpOnAShrinkingPond() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Fluids.Liquids pond = new Fluids.Liquids("fff");
        Pump pump = new Pump(PumpTier.MANUAL);
        pump.setTileSource(new LiquidTileSource(pond, 0, 0));
        grid.placePump(0, 0, pump);
        Check.equal(java.util.Collections.singletonList(Pump.SourceSlot.TILE), pump.getSourceSlots());
        grid.placePipe(0, 1, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(0, 2, Fluids.valve(1000));
        grid.tick();
        Check.equal(20, pump.click().getMoved(), "two tiles");
        for (int i = 0; i < 20; i++) {
            Fluids.tickAll(grid, pump);
        }
        Check.equal(10, pump.click().getMoved(), "the last tile, the pump's own");
        for (int i = 0; i < 20; i++) {
            Fluids.tickAll(grid, pump);
        }
        Check.equal(Status.NO_SOURCE, pump.click().getStatus(), "no source any more (N19-3 ②)");
    }

}
