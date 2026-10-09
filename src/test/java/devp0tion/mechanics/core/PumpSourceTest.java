package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pump sources: one source by the pump's form (N36-6), the source of the other form not linked
 * (N36-20), a ground pump on land (N36-15, N36-21, N36-28), the valve behind a valve pump placed
 * later, cut, unloaded, switched off or of another fluid (N36-17, N36-23, N36-58, N27-4, N36-24,
 * N36-53), and the liquid tile source (N19-3, N17-5, N20-7), also at a level's edge and reused within
 * a cycle.
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

    // ---------- one source by form (N36-6, N36-20) ----------

    /** Pipe (x + 1, y) and a large valve (x + 2, y): the destination east of a pump at (x, y). */
    private static TankValve eastDestination(PipeGrid grid, int x, int y) {
        grid.placePipe(x + 1, y, PipeLayer.BASE, MineralTier.COPPER);
        TankValve target = Fluids.valve(100000);
        grid.placeValve(x + 2, y, target);
        return target;
    }

    public static void testGroundPumpPullsFromTheTileOnly() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve behind = valveWith(FluidType.FRESHWATER, 500);
        grid.placeValve(-1, 0, behind);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        TankValve target = eastDestination(grid, 0, 0);
        Check.equal(PumpForm.GROUND, pump.getForm());
        Check.equal(Direction.WEST, pump.back());
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "a valve behind a ground pump is not linked (N36-20)");
        Check.equal(Collections.singletonList(pump.getTileSource()), pump.getSources(), "the tile only (N36-6)");
        for (int i = 0; i < 3; i++) {
            Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "cycle " + i);
        }
        Check.equal(500, behind.getTank().getAmount(), "nothing pulled from the valve behind it");
        Check.isTrue(target.getTank().getAmount() > 0, "the tile's water arrived");
    }

    public static void testValvePumpIgnoresTheTileUnderIt() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setDirection(Direction.EAST);
        pump.setForm(PumpForm.VALVE);
        pump.setFuelSupply(new Fluids.Logs(10));
        pump.setTileSource(LiquidTileSource.infinite(FluidType.FRESHWATER));
        grid.placePump(0, 0, pump);
        eastDestination(grid, 0, 0);
        Check.equal(0, pump.getSources().size(), "the liquid under a valve pump is not linked (N36-20)");
        PumpResult none = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, none.getStatus(), "no valve behind it: no source (N36-16)");
        Check.equal(PumpResult.Detail.SOURCE_MISSING, none.getDetail());

        TankValve behind = valveWith(FluidType.FRESHWATER, 500);
        grid.placeValve(-1, 0, behind);
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "the valve behind it, linked at once (N36-17)");
        Check.equal(Collections.<FluidSource>singletonList(behind.getTank()), pump.getSources(), "its one source (N36-3)");
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        Check.equal(480, behind.getTank().getAmount(), "pulled from the tank");
    }

    public static void testGroundPumpOnLandGetsASourceWhenTheTileTurnsLiquid() {
        // N36-15, N36-21, N36-28: a ground pump placed on land has no source until its tile is liquid,
        // and again after it used the tile up, until the tile is liquid again.
        Fluids.Liquids land = new Fluids.Liquids(
                "...",
                "...",
                "...");
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        LiquidTileSource tile = new LiquidTileSource(land, 1, 1);
        tile.judgeArea();
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setDirection(Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(10));
        pump.setTileSource(tile);
        grid.placePump(1, 1, pump);
        eastDestination(grid, 1, 1);
        PumpResult dry = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, dry.getStatus(), "placed on land (N36-15)");
        Check.equal(PumpResult.Detail.SOURCE_MISSING, dry.getDetail(), "no liquid under it");

        land.set(1, 1, 'f');
        PumpResult wet = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, wet.getStatus(), "the tile is liquid now (N36-21)");
        Check.equal(10, wet.getMoved(), "one finite tile");
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus(), "used up (N19-3)");
        land.set(1, 1, 'f');
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "liquid again: a source again (N36-28)");
    }

    // ---------- the valve behind a valve pump (N36-3) ----------

    public static void testUnloadedBackValveIsNoSourceUntilItLoads() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve behind = valveWith(FluidType.FRESHWATER, 500);
        grid.placeValve(-1, 0, behind);
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(10));
        eastDestination(grid, 0, 0);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        grid.unloadValve(-1, 0);
        PumpResult unloaded = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, unloaded.getStatus(), "its region is not loaded: no source (N36-58)");
        Check.equal(PumpResult.Detail.SOURCE_MISSING, unloaded.getDetail());
        Check.equal(480, behind.getTank().getAmount());
        grid.loadValve(-1, 0, behind);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "loaded again: pulled from in place");
        Check.equal(460, behind.getTank().getAmount());
    }

    public static void testWireOffBackValveIsNoSource() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve behind = valveWith(FluidType.FRESHWATER, 500);
        grid.placeValve(-1, 0, behind);
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(10));
        eastDestination(grid, 0, 0);
        behind.setEnabled(false);
        PumpResult off = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, off.getStatus(), "switched off by a wire signal (N27-4)");
        Check.equal(PumpResult.Detail.SOURCE_OFF, off.getDetail());
        Check.equal(500, behind.getTank().getAmount());
        behind.setEnabled(true);
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
    }

    public static void testBackValvePlacedLaterIsASourceAtOnceUnlessCut() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(10));
        eastDestination(grid, 0, 0);
        TankValve later = valveWith(FluidType.FRESHWATER, 500);
        grid.placeValve(-1, 0, later);
        Check.isTrue(later.isSideOpen(Direction.EAST), "no cut start (N36-17, N36-22)");
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "a source at once");
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
        Check.equal(480, later.getTank().getAmount());

        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "cut by the wrench");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "cut");
        grid.removeValve(-1, 0);
        TankValve again = valveWith(FluidType.FRESHWATER, 500);
        grid.placeValve(-1, 0, again);
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "the pump's side stays cut (N16-4, N36-23)");
        Check.equal(Status.NO_SOURCE, Fluids.cycle(pump).getStatus());
        Check.equal(500, again.getTank().getAmount());
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "linked again");
        Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus());
    }

    public static void testBackValveOfAnotherFluidIsDormant() {
        // N36-24, N36-53: the output cell holds water; the lava tank behind the pump stays linked and
        // nothing is pulled from it.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve lava = valveWith(FluidType.LAVA, 500);
        grid.placeValve(-1, 0, lava);
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.ADVANCED_FIRE, Direction.EAST);
        pump.setFuelSupply(new Fluids.Logs(10));
        eastDestination(grid, 0, 0);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "linked, not refused (N36-24)");
        PumpResult dormant = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, dormant.getStatus(), "the baseline is the output cell's water (N20-5)");
        Check.equal(PumpResult.Detail.SOURCE_OTHER_FLUID, dormant.getDetail());
        Check.equal(500, lava.getTank().getAmount(), "nothing pulled (N20-3)");
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

    // ---------- the 5x5 judgment (N20-7) ----------

    private static Fluids.Liquids uniformLake() {
        return new Fluids.Liquids(
                "sssss",
                "sssss",
                "sssss",
                "sssss",
                "sssss");
    }

    public static void testFiveByFiveIsJudgedOnItsLoadedCells() {
        Fluids.Liquids edge = new Fluids.Liquids(
                "ssssu",
                "sssss",
                "sssss",
                "sssss",
                "sssss");
        LiquidTileSource tile = new LiquidTileSource(edge, 2, 2);
        tile.judgeArea();
        Check.equal(Boolean.TRUE, tile.getJudgment(), "the unloaded cell is left out (N20-7)");
        Check.equal(FluidType.SEAWATER, tile.getSourceFluid(), "no waiting for the unloaded cell");
        Check.equal(20, tile.extract(FluidType.SEAWATER, 20));
        Check.equal(0, edge.consumed.size(), "infinite: nothing used up");
        Fluids.Liquids shore = new Fluids.Liquids(
                "ssssu",
                "sssss",
                "sssss",
                "sssss",
                "ssss.");
        LiquidTileSource finite = new LiquidTileSource(shore, 2, 2);
        Check.isFalse(finite.isInfinite(), "a loaded land cell: finite");
    }

    public static void testInfiniteJudgmentIsKeptWhileTheAreaUnloads() {
        Fluids.Liquids lake = uniformLake();
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        tile.judgeArea();
        lake.unload(3, 0, 4, 4);
        Check.isTrue(tile.isInfinite(), "judged when placed; kept while cells are unloaded (N20-7)");
        Check.equal(Integer.MAX_VALUE, tile.getAvailable(FluidType.SEAWATER));
        Check.equal(40, tile.extract(FluidType.SEAWATER, 40));
        Check.equal(0, lake.consumed.size());
    }

    public static void testFiniteJudgmentIsKeptWhileTheCellThatMadeItUnloads() {
        Fluids.Liquids lake = uniformLake();
        lake.set(4, 4, '.');
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        tile.judgeArea();
        Check.equal(Boolean.FALSE, tile.getJudgment());
        lake.unload(4, 4, 4, 4);
        Check.isFalse(tile.isInfinite(), "no change seen: the judgment stays");
        tile.extract(FluidType.SEAWATER, 10);
        Check.equal(1, lake.consumed.size(), "finite: a tile is used up");
    }

    public static void testChangeInTheLoadedAreaJudgesAgain() {
        Fluids.Liquids lake = uniformLake();
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        tile.judgeArea();
        Check.isTrue(tile.isInfinite(), "uniform");
        lake.set(0, 0, '.');
        Check.isFalse(tile.isInfinite(), "a loaded cell changed: judged again (N20-7)");
        Check.equal(10, tile.extract(FluidType.SEAWATER, 10));
        Check.equal(1, lake.consumed.size(), "now tiles are used up");
    }

    public static void testReloadedCellsAreWatchedAgain() {
        Fluids.Liquids lake = uniformLake();
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        tile.judgeArea();
        lake.unload(4, 0, 4, 4);
        Check.isTrue(tile.isInfinite(), "unloaded: kept");
        // A change no one could watch (the cell was not loaded) is not a change seen while loaded.
        lake.set(4, 0, '.');
        lake.load(4, 0, 4, 4);
        Check.isTrue(tile.isInfinite(), "loaded again: watched from now on, not judged again");
        lake.set(4, 4, '.');
        Check.isFalse(tile.isInfinite(), "a change after loading again: judged again");
    }

    public static void testSavedJudgmentIsRestored() {
        Fluids.Liquids lake = uniformLake();
        lake.set(0, 0, '.');
        LiquidTileSource restored = new LiquidTileSource(lake, 2, 2);
        restored.setJudgment(Boolean.TRUE);
        Check.isTrue(restored.isInfinite(), "the saved judgment, not judged again on loading");
        lake.set(1, 1, '.');
        Check.isFalse(restored.isInfinite(), "a change seen after loading: judged again");
        LiquidTileSource old = new LiquidTileSource(uniformLake(), 2, 2);
        old.setJudgment(null);
        Check.isNull(old.getJudgment(), "saved before N20-7: not judged yet");
        Check.isTrue(old.isInfinite(), "judged at the first use with the loaded cells");
        Check.equal(Boolean.TRUE, old.getJudgment());
    }

    public static void testNothingIsJudgedWhileThePumpTileIsNotLoaded() {
        Fluids.Liquids lake = uniformLake();
        lake.unload(2, 2, 2, 2);
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        tile.judgeArea();
        Check.isNull(tile.getJudgment(), "no judgment without the pump's own tile");
        Check.isNull(tile.getSourceFluid(), "no source while it is not loaded");
        lake.load(2, 2, 2, 2);
        tile.judgeArea();
        Check.equal(Boolean.TRUE, tile.getJudgment());
    }

    public static void testFiniteSearchUsesLoadedTilesOnly() {
        Fluids.Liquids strip = new Fluids.Liquids(
                "fffuff",
                "......");
        LiquidTileSource tile = new LiquidTileSource(strip, 0, 0);
        Check.isFalse(tile.isInfinite(), "land around the strip");
        Check.equal(30, tile.getAvailable(FluidType.FRESHWATER), "the loaded connected tiles only (N20-7)");
        Check.equal(30, tile.extract(FluidType.FRESHWATER, 100));
        Check.equal(FluidType.FRESHWATER, strip.getFluid(4, 0), "beyond the unloaded tile: untouched");
        Check.equal(0, tile.getAvailable(FluidType.FRESHWATER), "the pump's own tile is gone");
    }

    public static void testPumpKeepsItsInfiniteSourceWhileTheAreaUnloads() {
        Fluids.Liquids lake = uniformLake();
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(10));
        LiquidTileSource tile = new LiquidTileSource(lake, 2, 2);
        tile.judgeArea();
        pump.setTileSource(tile);
        pump.setDirection(Direction.SOUTH);
        grid.placePump(2, 2, pump);
        grid.placePipe(2, 3, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 4, Fluids.valve(1000));
        lake.unload(0, 0, 4, 1);
        lake.unload(0, 2, 1, 4);
        for (int i = 0; i < 5; i++) {
            Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "cycle " + i);
        }
        Check.equal(0, lake.consumed.size(), "still infinite (N20-7)");
        Check.equal(Boolean.TRUE, tile.getJudgment());
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

    public static void testDeepSeaNeverRunsOut() {
        // N27-5: a small patch of deep seawater (crude oil) with land around it: the 5x5 is not one
        // fluid, but the deep sea is inexhaustible and never turns into dirt.
        PipeGrid grid = new PipeGrid(Fluids.uniform(40));
        Fluids.Liquids sea = new Fluids.Liquids(
                ".....",
                ".ooo.",
                ".ooo.",
                ".ooo.",
                ".....");
        LiquidTileSource tile = new LiquidTileSource(sea, 2, 2);
        tile.judgeArea();
        Check.equal(Boolean.FALSE, tile.getJudgment(), "not a uniform 5x5 (N19-3)");
        Check.equal(Integer.MAX_VALUE, tile.getAvailable(FluidType.CRUDE_OIL), "inexhaustible anyway (N27-5)");
        Pump pump = new Pump(PumpTier.ADVANCED_FIRE);
        pump.setDirection(Direction.EAST);
        pump.setTileSource(tile);
        pump.setFuelSupply(new Fluids.Logs(1000));
        grid.placePump(2, 2, pump);
        Fluids.baseLine(grid, 3, 4, 2, MineralTier.COPPER);
        TankValve target = Fluids.valve(100000);
        grid.placeValve(5, 2, target);
        for (int i = 0; i < 20; i++) {
            Check.equal(Status.PUMPED, Fluids.cycle(pump).getStatus(), "cycle " + i);
        }
        Check.equal(0, sea.consumed.size(), "no deep tile used up (N27-5)");
        Check.equal(FluidType.CRUDE_OIL, tile.getSourceFluid(), "still deep sea under the pump");
        Check.equal(0, tile.getBuffered(), "nothing buffered");
    }

    public static void testShallowSeaNextToTheDeepSeaStillRunsOut() {
        // N27-5 leaves shallow seawater as it was: a finite pond is used up, the deep tiles beside it
        // are another fluid and are never touched.
        Fluids.Liquids sea = new Fluids.Liquids(
                ".....",
                ".sso.",
                ".sso.",
                ".....");
        LiquidTileSource tile = new LiquidTileSource(sea, 1, 1);
        tile.judgeArea();
        Check.equal(40, tile.getAvailable(FluidType.SEAWATER), "four shallow tiles");
        Check.equal(40, tile.extract(FluidType.SEAWATER, 40));
        Check.equal(4, sea.consumed.size(), "the shallow tiles are used up");
        Check.isTrue(!sea.consumed.contains(PipeGrid.key(3, 1)) && !sea.consumed.contains(PipeGrid.key(3, 2)),
                "the deep tiles are untouched");
    }

    public static void testPumpOnAShrinkingPond() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Fluids.Liquids pond = new Fluids.Liquids("fff");
        Pump pump = new Pump(PumpTier.MANUAL);
        pump.setDirection(Direction.SOUTH);
        pump.setTileSource(new LiquidTileSource(pond, 0, 0));
        grid.placePump(0, 0, pump);
        Check.equal(Collections.singletonList(pump.getTileSource()), pump.getSources(), "a ground pump: its tile (N36-6)");
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


    // ---------- cut links to the valve behind (N16-4) ----------

    /** A valve whose side toward a pump east of it (its east side) is cut. */
    private static TankValve cutValveWith(FluidType fluid, int amount) {
        TankValve valve = valveWith(fluid, amount);
        valve.setLinks(LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.EAST, false));
        return valve;
    }

    /** A loaded valve pump facing east with saved link flags. */
    private static Pump loadedValvePump(PipeGrid grid, int links) {
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setDirection(Direction.EAST);
        pump.setForm(PumpForm.VALVE);
        pump.setFuelSupply(new Fluids.Logs(10));
        pump.setLinks(links);
        grid.loadPump(0, 0, pump);
        return pump;
    }

    public static void testCutBackValveGivesNothing() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve valve = cutValveWith(FluidType.FRESHWATER, 100);
        grid.loadValve(-1, 0, valve);
        Pump pump = loadedValvePump(grid, LinkFlags.ALL_OPEN);
        eastDestination(grid, 0, 0);
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "cut");
        Check.equal(0, pump.getSources().size(), "a cut valve is no source (N16-4)");
        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.NO_SOURCE, result.getStatus());
        Check.equal(PumpResult.Detail.SOURCE_MISSING, result.getDetail());
        Check.equal(100, valve.getTank().getAmount(), "nothing pulled through the cut link");
    }

    public static void testWrenchLinksTheBackValveAgain() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve valve = cutValveWith(FluidType.FRESHWATER, 100);
        grid.loadValve(-1, 0, valve);
        Pump pump = loadedValvePump(grid, LinkFlags.ALL_OPEN);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST));
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "linked again");
        Check.equal(Collections.<FluidSource>singletonList(valve.getTank()), pump.getSources());
    }

    public static void testPumpsOwnCutBackSideIsNoSource() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        grid.loadValve(-1, 0, valveWith(FluidType.FRESHWATER, 100));
        Pump pump = loadedValvePump(grid, LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.WEST, false));
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "the pump's own side is cut");
        Check.equal(0, pump.getSources().size());
    }

    // ---------- the liquid tile source at a level's edge and within a cycle ----------

    public static void testTilesOutsideTheLevelCountAsLoadedLand() {
        // The pump one tile from the level's west and north edges. The game's lookup reads tiles
        // outside the level as loaded without liquid (they never load): the 5x5 is not uniform, so
        // the pond is used up (N19-3 ②) instead of waiting forever.
        Fluids.Liquids pond = new Fluids.Liquids(
                "fff",
                "fff");
        LiquidTileSource tile = new LiquidTileSource(pond, 1, 1);
        Check.equal(FluidType.FRESHWATER, tile.getSourceFluid(), "a source, not waiting");
        Check.isFalse(tile.isInfinite(), "land outside the level: not a uniform 5x5");
        Check.equal(60, tile.getAvailable(FluidType.FRESHWATER), "6 tiles x 10");
        Check.equal(20, tile.extract(FluidType.FRESHWATER, 20));
        Check.equal(2, pond.consumed.size());
    }

    /** Counts the reads of each tile of a lookup. */
    private static final class CountingLiquids implements LiquidTileLookup {
        private final Fluids.Liquids inner;
        private final Map<Long, Integer> reads = new HashMap<>();

        CountingLiquids(Fluids.Liquids inner) {
            this.inner = inner;
        }

        int reads(int x, int y) {
            Integer count = reads.get(PipeGrid.key(x, y));
            return count == null ? 0 : count;
        }

        @Override
        public boolean isLoaded(int tileX, int tileY) {
            return inner.isLoaded(tileX, tileY);
        }

        @Override
        public FluidType getFluid(int tileX, int tileY) {
            reads.merge(PipeGrid.key(tileX, tileY), 1, Integer::sum);
            return inner.getFluid(tileX, tileY);
        }

        @Override
        public void consume(int tileX, int tileY) {
            inner.consume(tileX, tileY);
        }
    }

    public static void testOneCycleSearchesTheConnectedTilesOnce() {
        // A 1x12 strip with the pump at its west end: one advanced fire pump cycle uses 4 tiles.
        Fluids.Liquids strip = new Fluids.Liquids(
                ".............",
                "ffffffffffff.",
                ".............");
        CountingLiquids lookup = new CountingLiquids(strip);
        PipeGrid grid = new PipeGrid(Fluids.uniform(100));
        Pump pump = new Pump(PumpTier.ADVANCED_FIRE);
        pump.setDirection(Direction.SOUTH);
        pump.setFuelSupply(new Fluids.Logs(10));
        pump.setTileSource(new LiquidTileSource(lookup, 0, 1));
        grid.placePump(0, 1, pump);
        grid.placePipe(0, 2, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(0, 3, Fluids.valve(100000));
        PumpResult result = Fluids.cycle(pump);
        Check.equal(Status.PUMPED, result.getStatus());
        Check.equal(40, result.getMoved());
        Check.equal(4, strip.consumed.size(), "4 tiles of 10");
        Check.equal(11L, strip.order.get(0)[0], "the farthest first (N19-3)");
        Check.equal(8L, strip.order.get(3)[0]);
        Check.equal(1, lookup.reads(7, 1), "a tile is read by one search in the cycle, not once per step");
        Fluids.cycle(pump);
        Check.equal(8, strip.consumed.size());
        Check.equal(2, lookup.reads(7, 1), "the next cycle searches again (the level may have changed)");
    }

    /** The calls one pump cycle makes on its tile source (Pump.runCycle), inside a cycle or not. */
    private static int cycleCalls(LiquidTileSource tile, boolean inCycle, int want, List<Object> log) {
        if (inCycle) {
            tile.beginCycle();
        }
        try {
            FluidType fluid = tile.getSourceFluid();
            log.add(fluid);
            if (fluid == null) {
                return 0;
            }
            int first = tile.getAvailable(fluid);
            int second = tile.getAvailable(fluid);
            log.add(first);
            log.add(second);
            int taken = tile.extract(fluid, Math.min(want, Math.max(0, first)));
            log.add(taken);
            log.add(tile.getBuffered());
            return taken;
        } finally {
            if (inCycle) {
                tile.endCycle();
            }
        }
    }

    /** Runs cycles on two copies of a lake, one reusing its search within each cycle: same tiles, same order. */
    private static void checkSameAsSearchingEveryTime(String[] rows, int pumpX, int pumpY, int want, int maxCycles, String name) {
        Fluids.Liquids reused = new Fluids.Liquids(rows);
        Fluids.Liquids searched = new Fluids.Liquids(rows);
        LiquidTileSource a = new LiquidTileSource(reused, pumpX, pumpY);
        LiquidTileSource b = new LiquidTileSource(searched, pumpX, pumpY);
        Check.isFalse(b.isInfinite(), name + ": finite");
        List<Object> logA = new ArrayList<>();
        List<Object> logB = new ArrayList<>();
        int cycles = 0;
        while (cycles < maxCycles) {
            int takenA = cycleCalls(a, true, want, logA);
            int takenB = cycleCalls(b, false, want, logB);
            Check.equal(takenB, takenA, name + ": cycle " + cycles);
            cycles++;
            if (takenB == 0) {
                break;
            }
        }
        Check.equal(logB, logA, name + ": every call gives the same");
        Check.equal(searched.order.size(), reused.order.size(), name + ": tiles used");
        Check.isTrue(reused.order.size() > 0, name + ": something used");
        for (int i = 0; i < searched.order.size(); i++) {
            Check.equal(Arrays.toString(searched.order.get(i)), Arrays.toString(reused.order.get(i)), name + ": tile " + i);
        }
    }

    private static String[] randomLake(long seed, int width, int height, double density, int pumpX, int pumpY) {
        Random random = new Random(seed);
        String[] rows = new String[height];
        for (int y = 0; y < height; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < width; x++) {
                boolean pump = x == pumpX && y == pumpY;
                boolean gap = x == pumpX + 1 && y == pumpY;
                row.append(pump || !gap && random.nextDouble() < density ? 'f' : '.');
            }
            rows[y] = row.toString();
        }
        return rows;
    }

    public static void testReuseWithinACycleUsesTheSameTilesAsSearchingEveryTime() {
        // Small irregular lakes: the whole connected body is found, used up to the pump's own tile.
        for (long seed = 1; seed <= 6; seed++) {
            checkSameAsSearchingEveryTime(randomLake(seed, 24, 24, 0.7, 12, 12), 12, 12, 40, 1000, "small lake " + seed);
        }
        // Large lakes: the search stops at MAX_CONNECTED_TILES, so after each used tile it runs again.
        String[] big = new String[45];
        for (int y = 0; y < big.length; y++) {
            big[y] = "..fffffffffffffffffffffffffffffffffffffffffff";
        }
        checkSameAsSearchingEveryTime(big, 2, 22, 40, 12, "large lake at land");
        checkSameAsSearchingEveryTime(randomLake(11, 70, 70, 0.8, 35, 35), 35, 35, 30, 12, "large irregular lake");
    }

}
