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

    public static void testNoReasonForAValveLinkOfAnyFluidOrACut() {
        // N36-26, N36-27: linking a valve to a pump refuses nothing for its fluid.
        PipeGrid grid = grid();
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        TankValve lava = Fluids.valve(100);
        lava.getTank().insert(FluidType.LAVA, 10);
        lava.setLinks(LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.EAST, false));
        grid.placeValve(-1, 0, lava);
        TankValve front = Fluids.valve(100);
        front.getTank().insert(FluidType.SLIME, 10);
        front.setLinks(LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.WEST, false));
        grid.placeValve(1, 0, front);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "behind: another fluid");
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(1, 0, PipeGrid.Part.VALVE, Direction.WEST), "in front: another fluid");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "still cut: nothing changed");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "linked");
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "linked");
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST),
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

    public static void testAValvePumpsFlagTowardAPlainWallBehindIt() {
        // N33-15: the wrench treats the plain wall as a wall, so only the pump's own flag flips. N35-1
        // for the valve behind a valve pump (N36-3): no source while it is a plain wall, its source
        // again once it is a valve, unless a flag between them is cut.
        PipeGrid grid = grid();
        TankValve behind = Fluids.valve(100);
        grid.placeValve(-1, 0, behind);
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        behind.getTank().insert(FluidType.LAVA, 50);
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "linked at placement");
        grid.setValvePlainWall(-1, 0, true);
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "no source while it is a plain wall (N35-1)");

        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "previewed");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "own flag");
        Check.isFalse(pump.isSideOpen(Direction.WEST), "cut");
        Check.isTrue(behind.isSideOpen(Direction.EAST), "the valve's flag stays");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "open again");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "still a plain wall");

        grid.setValvePlainWall(-1, 0, false);
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "linked once it is a valve again");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "a valve: both cut");
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST),
                "linking a valve of another fluid is not refused (N36-26)");

        // The valve's flag toward the pump cut: opening the pump's flag toward the plain wall links nothing.
        grid.setValvePlainWall(-1, 0, true);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "own flag");
        Check.isTrue(pump.isSideOpen(Direction.WEST) && !behind.isSideOpen(Direction.EAST), "the pump's flag only");
        grid.setValvePlainWall(-1, 0, false);
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "cut");
    }

    // ---------- a pump's sides that cannot be linked (N36-9, N36-25, N36-45, N36-55, N36-61) ----------

    /** Both checks: the preview and the click return the same, and no flag changes. */
    private static void checkClosed(PipeGrid grid, int x, int y, PipeGrid.Part part, Direction direction,
                                    PipeGrid.Check expected, String name) {
        int[] before = flags(grid);
        Check.equal(expected, grid.checkToggleSide(x, y, part, direction), name + ": previewed");
        Check.equal(expected, grid.toggleSide(x, y, part, direction), name + ": the click");
        int[] after = flags(grid);
        for (int i = 0; i < before.length; i++) {
            Check.equal(before[i], after[i], name + ": flags unchanged");
        }
    }

    /** The link flags of every part on the tiles around (0,0). */
    private static int[] flags(PipeGrid grid) {
        int[] result = new int[5 * 5 * 3];
        int i = 0;
        for (int y = -2; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                PipeNode base = grid.getPipe(x, y, PipeLayer.BASE);
                TankValve valve = grid.getValve(x, y);
                Pump pump = grid.getPump(x, y);
                result[i++] = base != null ? base.getLinks() : -1;
                result[i++] = valve != null ? valve.getLinks() : -1;
                result[i++] = pump != null ? pump.getLinks() : -1;
            }
        }
        return result;
    }

    public static void testAPumpsClosedSidesFromItsOwnTile() {
        // A valve pump facing east: front east, back west; north and south closed whatever is there.
        PipeGrid grid = grid();
        Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        checkClosed(grid, 0, 0, PipeGrid.Part.PUMP, Direction.NORTH, PipeGrid.Check.PUMP_SIDE_CLOSED, "a side with nothing there");
        grid.placePipe(0, 1, PipeLayer.BASE, MineralTier.COPPER);
        checkClosed(grid, 0, 0, PipeGrid.Part.PUMP, Direction.SOUTH, PipeGrid.Check.PUMP_SIDE_CLOSED, "a side with a pipe");
        grid.placeValve(0, -1, Fluids.valve(100));
        checkClosed(grid, 0, 0, PipeGrid.Part.PUMP, Direction.NORTH, PipeGrid.Check.PUMP_SIDE_CLOSED, "a side with a valve");
        grid.setValvePlainWall(0, -1, true);
        checkClosed(grid, 0, 0, PipeGrid.Part.PUMP, Direction.NORTH, PipeGrid.Check.PUMP_SIDE_CLOSED,
                "a side with a plain wall (N36-25)");
        grid.placePipe(-1, 0, PipeLayer.BASE, MineralTier.COPPER);
        checkClosed(grid, 0, 0, PipeGrid.Part.PUMP, Direction.WEST, PipeGrid.Check.PUMP_SIDE_CLOSED,
                "a basic pipe behind it (N36-2)");

        // The front and a valve pump's back toward nothing: the own flag flips (N16-4).
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST), "the front");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST));
        Check.isFalse(grid.getPump(0, 0).isSideOpen(Direction.EAST), "own flag cut");
        grid.removePipe(-1, 0, PipeLayer.BASE);
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(0, 0, PipeGrid.Part.PUMP, Direction.WEST), "a valve pump's back");
        Check.isFalse(grid.getPump(0, 0).isSideOpen(Direction.WEST), "own flag cut");

        // A ground pump's back is closed too, also toward a valve (N36-20).
        PipeGrid ground = grid();
        Fluids.pump(ground, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        checkClosed(ground, 0, 0, PipeGrid.Part.PUMP, Direction.WEST, PipeGrid.Check.PUMP_SIDE_CLOSED, "a ground pump's back");
        ground.placeValve(-1, 0, Fluids.valve(100));
        checkClosed(ground, 0, 0, PipeGrid.Part.PUMP, Direction.WEST, PipeGrid.Check.PUMP_SIDE_CLOSED,
                "a valve behind a ground pump");
        Check.equal(PipeGrid.Check.OK, ground.checkToggleSide(0, 0, PipeGrid.Part.PUMP, Direction.EAST), "its front");
    }

    public static void testAPumpsClosedSidesFromTheNeighbour() {
        // N36-55: from a pipe or a valve next to the pump, toward a side of it that cannot be linked.
        PipeGrid grid = grid();
        Pump pump = Fluids.valvePump(grid, 0, 0, PumpTier.FIRE, Direction.EAST);
        grid.placePipe(0, -1, PipeLayer.BASE, MineralTier.COPPER);
        grid.placeValve(0, 1, Fluids.valve(100));
        grid.placePipe(-1, 0, PipeLayer.BASE, MineralTier.COPPER);
        checkClosed(grid, 0, -1, PipeGrid.Part.BASIC_PIPE, Direction.SOUTH, PipeGrid.Check.PUMP_SIDE_CLOSED_NEIGHBOUR,
                "a pipe at its side");
        checkClosed(grid, 0, 1, PipeGrid.Part.VALVE, Direction.NORTH, PipeGrid.Check.PUMP_SIDE_CLOSED_NEIGHBOUR,
                "a valve at its side (N36-5)");
        checkClosed(grid, -1, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST, PipeGrid.Check.PUMP_SIDE_CLOSED_NEIGHBOUR,
                "a pipe behind a valve pump (N36-2)");

        // A flag already cut stays cut (N16-4, N36-55).
        PipeNode side = grid.getPipe(0, -1, PipeLayer.BASE);
        side.setSideOpen(Direction.SOUTH, false);
        checkClosed(grid, 0, -1, PipeGrid.Part.BASIC_PIPE, Direction.SOUTH, PipeGrid.Check.PUMP_SIDE_CLOSED_NEIGHBOUR,
                "a cut flag");
        Check.isFalse(side.isSideOpen(Direction.SOUTH), "stays cut");

        // Toward the sides it links, the wrench works as before.
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(1, 0, PipeGrid.Part.BASIC_PIPE, Direction.WEST), "the pipe in front");
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(1, 0, PipeGrid.Part.BASIC_PIPE, Direction.WEST));
        Check.equal(0, grid.getPumpEntries(pump).size(), "cut from the pipe");
        grid.removePipe(-1, 0, PipeLayer.BASE);
        grid.placeValve(-1, 0, Fluids.valve(100));
        Check.equal(PipeGrid.Check.OK, grid.toggleSide(-1, 0, PipeGrid.Part.VALVE, Direction.EAST), "the valve behind it");
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "cut from the valve");

        // Underground pipes never link pumps (9-9): no pump gate there.
        grid.placePipe(0, 2, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.placePipe(0, 3, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(PipeGrid.Check.OK, grid.checkToggleSide(0, 2, PipeGrid.Part.UNDERGROUND_PIPE, Direction.NORTH));

        // A valve behind a ground pump, from the valve.
        PipeGrid ground = grid();
        Fluids.pump(ground, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        ground.placeValve(-1, 0, Fluids.valve(100));
        checkClosed(ground, -1, 0, PipeGrid.Part.VALVE, Direction.EAST, PipeGrid.Check.PUMP_SIDE_CLOSED_NEIGHBOUR,
                "a valve behind a ground pump (N36-20)");
    }

}
