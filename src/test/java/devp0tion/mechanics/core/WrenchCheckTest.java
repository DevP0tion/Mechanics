package devp0tion.mechanics.core;

import java.util.Arrays;
import java.util.Collections;

/**
 * {@link PipeGrid#checkToggleSide} and {@link PipeGrid#checkToggleVertical}: what a wrench click
 * would return, for the tooltip's preview (N30-1), without changing anything. Run by the wrench's
 * test runner ({@code wrenchTest}).
 */
public final class WrenchCheckTest {

    private WrenchCheckTest() {
    }

    private static PipeGrid grid() {
        return new PipeGrid(Fluids.TIERS);
    }

    public static void testPreviewsTheRefusedValveLinkAndChangesNothing() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.ADVANCED_FIRE, FluidType.FRESHWATER);
        TankValve lava = Fluids.valve(100);
        lava.getTank().insert(FluidType.LAVA, 10);
        grid.placeValve(1, 0, lava);
        int pumpLinks = pump.getLinks();
        int valveLinks = lava.getLinks();
        Check.equal(PipeGrid.Check.DIFFERENT_SOURCE_FLUID, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST), "from the pump");
        Check.equal(PipeGrid.Check.DIFFERENT_SOURCE_FLUID, grid.checkToggleSide(1, 0, PipeGrid.Part.VALVE, Direction.WEST), "from the valve");
        Check.equal(pumpLinks, pump.getLinks(), "pump flags unchanged");
        Check.equal(valveLinks, lava.getLinks(), "valve flags unchanged");
        Check.equal(1, pump.getSourceSlots().size(), "sources unchanged");
        Check.equal(PipeGrid.Check.DIFFERENT_SOURCE_FLUID, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST),
                "the click returns what was previewed (N16-3)");
    }

    public static void testNoReasonForALinkOfTheSameFluidOrACut() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        TankValve same = Fluids.valve(100);
        same.getTank().insert(FluidType.FRESHWATER, 10);
        grid.placeValve(1, 0, same);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST), "same fluid");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.EAST), "still cut: nothing changed");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST), "linked");
        same.getTank().extract(FluidType.FRESHWATER, 10);
        same.getTank().insert(FluidType.LAVA, 10);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST),
                "cutting a linked valve is never refused");
    }

    public static void testPipesAndLoneSidesAreNeverRefused() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 1, 0, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST), "pipe-pipe");
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.NORTH), "own flag only");
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.checkToggleSide(5, 5, PipeGrid.Part.BASIC_PIPE, Direction.EAST), "no part");
        Check.equal(grid.toggleSide(5, 5, PipeGrid.Part.BASIC_PIPE, Direction.EAST),
                grid.checkToggleSide(5, 5, PipeGrid.Part.BASIC_PIPE, Direction.EAST), "same as the click");
    }

    public static void testNotLoadedNeighbour() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 1, 0, MineralTier.COPPER);
        grid.setTileLoadedLookup((x, y) -> x < 1);
        Check.equal(PipeGrid.Check.NOT_LOADED, grid.checkToggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST));
        Check.equal(PipeGrid.Check.NOT_LOADED, grid.checkToggleVertical(1, 0));
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.WEST));
    }

    public static void testVerticalPreview() {
        PipeGrid grid = grid();
        Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.checkToggleVertical(0, 0), "a lone pump has no vertical link (9-9)");
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.toggleVertical(0, 0), "as the click");
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleVertical(0, 0), "an underground pipe under the pump");
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleVertical(2, 0), "a basic pipe");
        grid.placeValve(4, 0, Fluids.valve(100));
        Check.equal(PipeGrid.Check.OK, grid.checkToggleVertical(4, 0), "a valve");
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.checkToggleVertical(6, 0), "nothing");
        Check.isTrue(LinkFlags.isVerticalOpen(grid.getPipe(2, 0, PipeLayer.BASE).getLinks()), "nothing changed");
    }

    public static void testAPlainWallValveIsNoPartForTheWrench() {
        // N33-1, N33-15: a valve in a shared wall is a plain wall for the wrench too.
        PipeGrid grid = grid();
        TankValve valve = Fluids.valve(100);
        grid.placeValve(1, 0, valve);
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.setValvePlainWall(1, 0, true);
        int valveLinks = valve.getLinks();
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.checkToggleSide(1, 0, PipeGrid.Part.VALVE, Direction.WEST), "on it");
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.toggleSide(1, 0, PipeGrid.Part.VALVE, Direction.WEST), "as the click");
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.checkToggleVertical(1, 0), "no vertical link on its own");
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST), "toward it");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST), "toward it");
        Check.isFalse(grid.getPipe(0, 0, PipeLayer.BASE).isSideOpen(Direction.EAST), "the pipe's own flag flipped");
        Check.equal(valveLinks, valve.getLinks(), "the valve's flags stay");
        grid.placePipe(1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(1, 0), "the underground pipe there");
        Check.isFalse(grid.getPipe(1, 0, PipeLayer.UNDERGROUND).isVerticalOpen(), "its own flag only");
        Check.isTrue(valve.isVerticalOpen(), "the valve's stays");
    }

    public static void testAPumpsFlagTowardAPlainWallValveCutsItOrAddsItLast() {
        // N33-15: the wrench treats the plain wall as a wall, so only the pump's own flag flips. N35-1:
        // the pump's sources change as for a cut (it leaves them) or a link (the last source, N19-1),
        // with no fluid compared; skipped while it is a plain wall.
        PipeGrid grid = grid();
        TankValve north = Fluids.valve(100);
        TankValve east = Fluids.valve(100);
        grid.placeValve(0, -1, north);
        grid.placeValve(1, 0, east);
        Pump pump = new Pump(PumpTier.FIRE);
        grid.placePump(0, 0, pump);
        Pump.SourceSlot n = Pump.SourceSlot.valve(Direction.NORTH);
        Pump.SourceSlot e = Pump.SourceSlot.valve(Direction.EAST);
        Check.equal(Arrays.asList(n, e), pump.getSourceSlots(), "linked at placement");
        // Filled later (N17-3): lava north, water east.
        north.getTank().insert(FluidType.LAVA, 50);
        east.getTank().insert(FluidType.FRESHWATER, 50);
        grid.setValvePlainWall(0, -1, true);
        Check.equal(Arrays.asList(n, e), pump.getSourceSlots(), "the plain wall keeps its slot (N35-1)");

        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH), "previewed");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH), "own flag");
        Check.isFalse(pump.isSideOpen(Direction.NORTH), "cut");
        Check.isTrue(north.isSideOpen(Direction.SOUTH), "the valve's flag stays");
        Check.equal(Collections.singletonList(e), pump.getSourceSlots(), "cut: it leaves the sources");

        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH),
                "no fluid compared toward a wall");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH), "open again");
        Check.equal(Arrays.asList(e, n), pump.getSourceSlots(), "opened: the last source (N19-1)");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.NORTH), "skipped while it is a plain wall");

        grid.setValvePlainWall(0, -1, false);
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.NORTH), "linked once it is a valve again");
        Check.equal(Arrays.asList(e, n), pump.getSourceSlots(), "in that place");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH), "a valve: both cut");
        Check.equal(PipeGrid.Check.DIFFERENT_SOURCE_FLUID, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH),
                "linking a valve of another fluid is refused (N16-3)");

        // The valve's flag toward the pump cut: opening the pump's flag toward the plain wall adds nothing.
        grid.setValvePlainWall(0, -1, true);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH), "own flag");
        Check.isTrue(pump.isSideOpen(Direction.NORTH) && !north.isSideOpen(Direction.SOUTH), "the pump's flag only");
        Check.equal(Collections.singletonList(e), pump.getSourceSlots(), "no source: the valve's flag is cut");
        grid.setValvePlainWall(0, -1, false);
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.NORTH), "cut");
    }

}
