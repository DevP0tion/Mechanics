package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Links: auto-connect (9-4), layers (9-3, 9-9, 11-5), the wrench (12-8, 13-4, 13-5, N16-3), cut
 * flags that stay (N16-4), the vertical link only by wrench (N16-4) and placement never refused for
 * fluids (N13-2).
 */
final class PipeLinkTest {

    private PipeLinkTest() {
    }

    private static PipeGrid grid() {
        return new PipeGrid(Fluids.TIERS);
    }

    private static PipeNode base(PipeGrid grid, int x, int y) {
        return grid.getPipe(x, y, PipeLayer.BASE);
    }

    private static PipeNode under(PipeGrid grid, int x, int y) {
        return grid.getPipe(x, y, PipeLayer.UNDERGROUND);
    }

    // ---------- auto-connect and layers ----------

    public static void testAdjacentPipesConnectAutomatically() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 3, 0, MineralTier.COPPER);
        grid.placePipe(3, 1, PipeLayer.BASE, MineralTier.IRON);
        Check.isTrue(grid.areLinked(base(grid, 0, 0), base(grid, 1, 0)), "9-4");
        Check.isTrue(grid.areLinked(base(grid, 3, 0), base(grid, 3, 1)), "different tiers link too");
        Check.equal(0, grid.getNetworks().size(), "empty pipes belong to no network (N13-1)");
    }

    public static void testDiagonalPipesDoNotConnect() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(1, 1, PipeLayer.BASE, MineralTier.COPPER);
        Check.isFalse(grid.areLinked(base(grid, 0, 0), base(grid, 1, 1)), "diagonal");
    }

    public static void testAdjacentPipesOfDifferentLayersStayApart() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.isFalse(grid.areLinked(base(grid, 0, 0), under(grid, 1, 0)), "only on the same tile");
    }

    public static void testVerticalLinkStartsCutEitherOrder() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.isFalse(grid.areLinked(base(grid, 0, 0), under(grid, 0, 0)), "basic over underground starts cut (N16-4)");
        Check.isFalse(base(grid, 0, 0).isVerticalOpen(), "the pipe placed second is cut");

        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.isFalse(grid.areLinked(base(grid, 1, 0), under(grid, 1, 0)), "underground under basic starts cut (N16-4)");

        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(0, 0));
        Check.isTrue(grid.areLinked(base(grid, 0, 0), under(grid, 0, 0)), "linked by the wrench only");
    }

    public static void testUndergroundPipeLinksToTheValveOnItsTileOnly() {
        PipeGrid grid = grid();
        TankValve above = Fluids.valve(100);
        TankValve beside = Fluids.valve(100);
        grid.placeValve(0, 0, above);
        grid.placeValve(1, 0, beside);
        PipeNode pipe = grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(Collections.singletonList(above), grid.getLinkedValves(pipe), "the valve above, automatically (9-9)");
    }

    public static void testBasicPipeLinksToAdjacentValves() {
        PipeGrid grid = grid();
        grid.placeValve(1, 0, Fluids.valve(100));
        grid.placeValve(0, 1, Fluids.valve(100));
        PipeNode pipe = grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(2, grid.getLinkedValves(pipe).size(), "both adjacent valves (2-3)");
    }

    public static void testPumpPushesOnlyIntoAdjacentBasicPipes() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(-1, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(Collections.singletonList(base(grid, 1, 0)), grid.getPumpEntries(pump),
                "underground pipes never link to pumps (9-9)");
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
        Check.equal(PipeGrid.Check.OCCUPIED, grid.checkPumpPlacement(0, 0, null));
    }

    // ---------- fluids never block placement or links (N13-2) ----------

    public static void testPlacingAPipeBetweenDifferentFluidsIsAllowed() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.set(grid, 0, 0, PipeLayer.BASE, FluidType.SEAWATER, 5);
        Fluids.set(grid, 2, 0, PipeLayer.BASE, FluidType.FRESHWATER, 5);
        Check.equal(PipeGrid.Check.OK, grid.checkPipePlacement(1, 0, PipeLayer.BASE), "N13-2");
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(2, grid.getNetworks().size(), "the two fluids stay apart");
    }

    public static void testLinkingDifferentFluidsIsAllowedButTheyStayApart() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 1, 0, MineralTier.COPPER);
        grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Fluids.set(grid, 0, 0, PipeLayer.BASE, FluidType.FRESHWATER, 5);
        Fluids.set(grid, 1, 0, PipeLayer.BASE, FluidType.LAVA, 5);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST), "N13-2");
        Check.isTrue(grid.areLinked(base(grid, 0, 0), base(grid, 1, 0)), "linked");
        Check.isTrue(grid.getNetwork(0, 0, PipeLayer.BASE) != grid.getNetwork(1, 0, PipeLayer.BASE),
                "the face between two fluids is not used (N13-2)");
    }

    // ---------- wrench ----------

    public static void testWrenchCutsAndRejoinsASide() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 1, 0, MineralTier.COPPER);
        PipeNode a = base(grid, 0, 0);
        PipeNode b = base(grid, 1, 0);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST));
        Check.isFalse(grid.areLinked(a, b), "cut (12-8)");
        Check.isFalse(a.isSideOpen(Direction.EAST), "a side");
        Check.isFalse(b.isSideOpen(Direction.WEST), "b side");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(1, 0, PipeGrid.Part.BASIC_PIPE, Direction.WEST));
        Check.isTrue(grid.areLinked(a, b), "joined again from the other pipe");
    }

    public static void testCutSideStaysCutForANewNeighbour() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.isFalse(grid.areLinked(base(grid, 0, 0), base(grid, 1, 0)), "the flag was set before the neighbour came");
    }

    public static void testCutStaysWhenOneSideIsRemovedAndPlacedAgain() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 1, 0, MineralTier.COPPER);
        grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        grid.removePipe(1, 0, PipeLayer.BASE);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.GOLD);
        Check.isTrue(base(grid, 1, 0).isSideOpen(Direction.WEST), "the new pipe starts open");
        Check.isFalse(grid.areLinked(base(grid, 0, 0), base(grid, 1, 0)), "the remaining side stays cut (N16-4)");

        TankValve valve = Fluids.valve(10);
        grid.placeValve(0, 1, valve);
        grid.toggleSide(0, 1, PipeGrid.Part.VALVE, Direction.NORTH);
        grid.removePipe(0, 0, PipeLayer.BASE);
        PipeNode again = grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(0, grid.getLinkedValves(again).size(), "the valve's cut side stays too (N16-4)");
    }

    public static void testWrenchCutsTheVerticalLink() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.toggleVertical(0, 0);
        Check.isTrue(grid.areLinked(base(grid, 0, 0), under(grid, 0, 0)), "linked");
        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(0, 0));
        Check.isFalse(grid.areLinked(base(grid, 0, 0), under(grid, 0, 0)), "cut again (12-8)");
    }

    public static void testVerticalCutStaysWhenTheBasicPipeIsReplaced() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.toggleVertical(0, 0);
        grid.removePipe(0, 0, PipeLayer.BASE);
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.isFalse(grid.areLinked(base(grid, 0, 0), under(grid, 0, 0)), "placed again over it: starts cut (N16-4)");
    }

    public static void testWrenchCutsTheUndergroundValveLink() {
        PipeGrid grid = grid();
        grid.placeValve(0, 0, Fluids.valve(100));
        PipeNode pipe = grid.placePipe(0, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.toggleVertical(0, 0));
        Check.equal(0, grid.getLinkedValves(pipe).size(), "cut (13-5)");
        grid.toggleVertical(0, 0);
        Check.equal(1, grid.getLinkedValves(pipe).size(), "linked again");
    }

    public static void testWrenchCutsPipeToValveAndPipeToPump() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.MANUAL, FluidType.FRESHWATER);
        PipeNode pipe = grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(2, 0, Fluids.valve(100));
        grid.toggleSide(1, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.equal(0, grid.getLinkedValves(pipe).size(), "pipe-valve cut (13-4)");
        grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST);
        Check.equal(0, grid.getPumpEntries(pump).size(), "pump-pipe cut from the pump (13-4)");
        grid.toggleSide(1, 0, PipeGrid.Part.BASIC_PIPE, Direction.WEST);
        Check.equal(1, grid.getPumpEntries(pump).size(), "linked again from the pipe");
    }

    public static void testWrenchOnNothing() {
        PipeGrid grid = grid();
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.NORTH));
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.NORTH));
        Check.equal(PipeGrid.Check.NOTHING_THERE, grid.toggleVertical(0, 0));
    }

    public static void testListenerHearsEveryFlagChange() {
        PipeGrid grid = grid();
        final List<String> heard = new ArrayList<>();
        grid.setListener((x, y, part) -> heard.add(part + "@" + x + "," + y));
        Fluids.baseLine(grid, 0, 1, 0, MineralTier.COPPER);
        grid.toggleSide(0, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.equal("[BASIC_PIPE@0,0, BASIC_PIPE@1,0]", heard.toString());
    }

    // ---------- pump and valve links (N13-3, N16-3) ----------

    public static void testPumpPlacedNextToAValveConnectsIt() {
        PipeGrid grid = grid();
        TankValve valve = Fluids.valve(100);
        grid.placeValve(0, 1, valve);
        Pump pump = new Pump(PumpTier.FIRE);
        grid.placePump(0, 0, pump);
        Check.equal(Collections.singletonList(valve), grid.getSourceValves(pump), "attached directly (11-5)");
        Check.equal(0, grid.getPumpEntries(pump).size(), "not a pipe");
    }

    public static void testValvePlacedLaterNextToAPumpStartsCut() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        TankValve valve = Fluids.valve(100);
        grid.placeValve(1, 0, valve);
        Check.isFalse(valve.isSideOpen(Direction.WEST), "cut toward the pump (N13-3, N16-3)");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.EAST), "not linked");
        Check.equal(Collections.singletonList(Pump.SourceSlot.TILE), pump.getSourceSlots(), "the pump keeps its source");
    }

    public static void testWrenchLinksAValveOfTheSameFluidOrEmpty() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        TankValve same = Fluids.valve(100);
        same.getTank().insert(FluidType.FRESHWATER, 10);
        TankValve empty = Fluids.valve(100);
        grid.placeValve(1, 0, same);
        grid.placeValve(0, 1, empty);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST), "same fluid");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 1, PipeGrid.Part.VALVE, Direction.NORTH), "empty, from the valve");
        Check.equal(java.util.Arrays.asList(Pump.SourceSlot.TILE, Pump.SourceSlot.valve(Direction.EAST),
                Pump.SourceSlot.valve(Direction.SOUTH)), pump.getSourceSlots(), "appended in link order (N19-1)");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST), "cut again");
        Check.equal(java.util.Arrays.asList(Pump.SourceSlot.TILE, Pump.SourceSlot.valve(Direction.SOUTH)),
                pump.getSourceSlots(), "the cut valve is no source");
    }

    public static void testWrenchRefusesAValveOfAnotherFluid() {
        PipeGrid grid = grid();
        Pump pump = Fluids.pump(grid, 0, 0, PumpTier.ADVANCED_FIRE, FluidType.FRESHWATER);
        TankValve lava = Fluids.valve(100);
        lava.getTank().insert(FluidType.LAVA, 10);
        grid.placeValve(1, 0, lava);
        Check.equal(PipeGrid.Check.DIFFERENT_SOURCE_FLUID, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST),
                "N16-3");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.EAST), "nothing changed");
        Check.isFalse(pump.isSideOpen(Direction.EAST) && lava.isSideOpen(Direction.WEST), "flags unchanged");
    }

    public static void testRemovedValveIsNoSourceButAnUnloadedOneStays() {
        PipeGrid grid = grid();
        grid.placeValve(1, 0, Fluids.valve(100));
        grid.placeValve(-1, 0, Fluids.valve(100));
        Pump pump = new Pump(PumpTier.FIRE);
        grid.placePump(0, 0, pump);
        Check.equal(2, pump.getSourceSlots().size(), "both valves (east, west)");
        grid.unloadValve(1, 0);
        Check.equal(2, pump.getSourceSlots().size(), "unloaded: still connected");
        grid.removeValve(-1, 0);
        Check.equal(Collections.singletonList(Pump.SourceSlot.valve(Direction.EAST)), pump.getSourceSlots(),
                "removed: gone");
    }

}
