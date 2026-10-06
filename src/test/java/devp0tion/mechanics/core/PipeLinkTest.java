package devp0tion.mechanics.core;

/** Pipe links: auto-connect (9-4, 9-5), layer rules (9-3, 9-9, 11-5) and wrench toggles (12-8, 13-4, 13-5). */
final class PipeLinkTest {

    private PipeLinkTest() {
    }

    private static PipeGrid grid() {
        return new PipeGrid(Fluids.TIERS);
    }

    // ---------- auto-connect and layers ----------

    public static void testAdjacentPipesConnectAutomatically() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 3, 0, MineralTier.COPPER);
        grid.placePipe(3, 1, PipeLayer.BASE, MineralTier.IRON);
        Check.equal(1, grid.getNetworks().size(), "one network (9-4)");
        Check.equal(5, grid.getNetwork(0, 0, PipeLayer.BASE).size());
        Check.isTrue(grid.areLinked(grid.getPipe(3, 0, PipeLayer.BASE), grid.getPipe(3, 1, PipeLayer.BASE)),
                "different tiers link too (9-10)");
    }

    public static void testDiagonalPipesDoNotConnect() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(1, 1, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(2, grid.getNetworks().size());
    }

    public static void testLayersOnlyMeetOnTheSameTile() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(2, grid.getNetworks().size(), "adjacent pipes of different layers stay apart");
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(1, grid.getNetworks().size(), "basic and underground pipe on one tile link (9-5)");
        Check.isTrue(grid.areLinked(grid.getPipe(1, 0, PipeLayer.BASE), grid.getPipe(1, 0, PipeLayer.UNDERGROUND)),
                "vertical link");
    }

    public static void testUndergroundPipeOnlyLinksToTheValveOnItsTile() {
        PipeGrid grid = grid();
        TankValve above = Fluids.valve(100);
        TankValve beside = Fluids.valve(100);
        grid.placeValve(0, 0, above);
        grid.placeValve(1, 0, beside);
        PipeNode under = grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(1, grid.getLinkedValves(under).size(), "only the valve above (9-9)");
        Check.isTrue(grid.getLinkedValves(under).get(0) == above, "the valve on the same tile");
    }

    public static void testBasicPipeLinksToAdjacentValves() {
        PipeGrid grid = grid();
        TankValve east = Fluids.valve(100);
        TankValve south = Fluids.valve(100);
        grid.placeValve(1, 0, east);
        grid.placeValve(0, 1, south);
        PipeNode pipe = grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(2, grid.getLinkedValves(pipe).size(), "both adjacent valves (2-3)");
    }

    public static void testPumpPushesOnlyIntoAdjacentBasicPipes() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(-1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(1, grid.getPumpEntries(pump).size(), "underground pipes never link to pumps (9-9)");
        Check.isTrue(grid.getPumpEntries(pump).get(0) == grid.getPipe(1, 0, PipeLayer.BASE), "the basic pipe (9-3)");
    }

    public static void testValveNextToPumpIsItsSource() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        TankValve valve = Fluids.valve(100);
        grid.placeValve(0, 1, valve);
        Check.equal(1, grid.getAttachedValves(pump).size(), "attached directly (11-5)");
        Check.equal(0, grid.getPumpEntries(pump).size(), "not a pipe");
    }

    public static void testBaseLayerHoldsOneObject() {
        PipeGrid grid = grid();
        grid.placeValve(0, 0, Fluids.valve(10));
        Check.equal(PipeGrid.Check.OCCUPIED, grid.checkPipePlacement(0, 0, PipeLayer.BASE), "valve there");
        Check.equal(PipeGrid.Check.OK, grid.checkPipePlacement(0, 0, PipeLayer.UNDERGROUND), "under the valve (10-4)");
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OCCUPIED, grid.checkPipePlacement(0, 0, PipeLayer.UNDERGROUND), "pipe there");
        Check.throwsException(IllegalStateException.class,
                () -> grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER));
        Check.throwsException(IllegalStateException.class, () -> grid.placePump(0, 0, new Pump(PumpTier.MANUAL)));
    }

    // ---------- wrench ----------

    public static void testWrenchCutsAndRejoinsASide() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 1, 0, MineralTier.COPPER);
        PipeNode a = grid.getPipe(0, 0, PipeLayer.BASE);
        PipeNode b = grid.getPipe(1, 0, PipeLayer.BASE);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeLayer.BASE, Direction.EAST));
        Check.isFalse(grid.areLinked(a, b), "cut (12-8)");
        Check.isFalse(a.isSideOpen(Direction.EAST), "a side");
        Check.isFalse(b.isSideOpen(Direction.WEST), "b side");
        Check.equal(2, grid.getNetworks().size());
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(1, 0, PipeLayer.BASE, Direction.WEST));
        Check.isTrue(grid.areLinked(a, b), "joined again from the other pipe");
        Check.equal(1, grid.getNetworks().size());
    }

    public static void testCutSideStaysCutForANewNeighbour() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.toggleSide(0, 0, PipeLayer.BASE, Direction.EAST);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(2, grid.getNetworks().size(), "the cut flag is kept");
    }

    public static void testWrenchCutsTheVerticalLink() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(0, 0));
        Check.equal(2, grid.getNetworks().size(), "basic and underground cut (12-8)");
        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(0, 0));
        Check.equal(1, grid.getNetworks().size(), "joined again");
    }

    public static void testWrenchCutsTheUndergroundValveLink() {
        PipeGrid grid = grid();
        grid.placeValve(0, 0, Fluids.valve(100));
        PipeNode under = grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(0, 0));
        Check.equal(0, grid.getLinkedValves(under).size(), "cut (13-5)");
        grid.toggleVertical(0, 0);
        Check.equal(1, grid.getLinkedValves(under).size(), "linked again");
    }

    public static void testWrenchCutsPipeToValveAndPipeToPump() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        PipeNode pipe = grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        grid.toggleSide(1, 0, PipeLayer.BASE, Direction.EAST);
        Check.equal(0, grid.getLinkedValves(pipe).size(), "pipe-valve cut (13-4)");
        grid.toggleSide(1, 0, PipeLayer.BASE, Direction.WEST);
        Check.equal(0, grid.getPumpEntries(pump).size(), "pipe-pump cut (13-4)");
        grid.toggleSide(1, 0, PipeLayer.BASE, Direction.WEST);
        Check.equal(1, grid.getPumpEntries(pump).size(), "pipe-pump linked again");
    }

    public static void testWrenchOnNothing() {
        PipeGrid grid = grid();
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.toggleSide(0, 0, PipeLayer.BASE, Direction.NORTH));
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.toggleVertical(0, 0));
    }

    public static void testLinkingDifferentFluidsIsRefused() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.toggleSide(0, 0, PipeLayer.BASE, Direction.EAST);
        grid.getPipe(0, 0, PipeLayer.BASE).setContents(FluidType.FRESHWATER, 5);
        grid.getPipe(1, 0, PipeLayer.BASE).setContents(FluidType.LAVA, 5);
        Check.equal(PipeGrid.Check.WOULD_MIX_FLUIDS, grid.toggleSide(0, 0, PipeLayer.BASE, Direction.EAST), "12-7");
        Check.isFalse(grid.getPipe(0, 0, PipeLayer.BASE).isSideOpen(Direction.EAST), "flag unchanged");
        Check.equal(2, grid.getNetworks().size());

        grid.placePipe(1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.toggleVertical(1, 0);
        grid.getPipe(1, 0, PipeLayer.UNDERGROUND).setContents(FluidType.SLIME, 5);
        Check.equal(PipeGrid.Check.WOULD_MIX_FLUIDS, grid.toggleVertical(1, 0), "vertical link, 12-7");
    }

}
