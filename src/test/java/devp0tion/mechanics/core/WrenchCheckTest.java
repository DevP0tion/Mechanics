package devp0tion.mechanics.core;

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

}
