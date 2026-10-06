package devp0tion.mechanics.core;

import devp0tion.mechanics.core.TankSearchResult.Status;

/** {@link TankStructure#findTank}: finding the tank around a controller. */
final class TankSearchTest {

    private TankSearchTest() {
    }

    public static void testFindsTankFromBorderController() {
        Grid grid = Grid.of(
                "X.......",
                ".###C##.",
                ".V,,,,#.",
                ".#gg,g#.",
                ".######.",
                "........");
        TankSearchResult result = TankStructure.findTank(4, 1, grid);
        Check.equal(Status.FOUND, result.getStatus());
        TankValidation tank = result.getTank();
        Check.equal(new TankBounds(1, 1, 6, 4), tank.getBounds());
        Check.equal(8 * 40, tank.getCapacity());
        Check.equal(1, tank.getValveCount());
    }

    public static void testFindsTankWithControllerOnCorner() {
        Grid grid = Grid.of(
                ".....",
                ".C##.",
                ".#G#.",
                ".###.");
        TankSearchResult result = TankStructure.findTank(1, 1, grid);
        Check.equal(Status.FOUND, result.getStatus());
        Check.equal(new TankBounds(1, 1, 3, 3), result.getTank().getBounds());
    }

    public static void testFindsMaximumSizeTank() {
        Grid grid = Grid.of(
                "#######",
                "#.....#",
                "#.....#",
                "C.....#",
                "#.....#",
                "#.....#",
                "#######");
        TankSearchResult result = TankStructure.findTank(0, 3, grid);
        Check.equal(Status.FOUND, result.getStatus());
        Check.equal(new TankBounds(0, 0, 7, 7), result.getTank().getBounds());
    }

    public static void testTooWideStructureIsNotFound() {
        Grid grid = Grid.of(
                "####C###",
                "#GGGGGG#",
                "########");
        Check.equal(Status.NOT_FOUND, TankStructure.findTank(4, 0, grid).getStatus());
    }

    public static void testBrokenWallIsNotFound() {
        Grid grid = Grid.of(
                "##C##",
                "#GGG#",
                "#GGG.",
                "#####");
        Check.equal(Status.NOT_FOUND, TankStructure.findTank(2, 0, grid).getStatus());
    }

    public static void testStartMustBeController() {
        Grid grid = Grid.of(
                "#C#",
                "#G#",
                "###");
        TankSearchResult result = TankStructure.findTank(0, 0, grid);
        Check.equal(Status.NOT_A_CONTROLLER, result.getStatus());
        Check.isNull(result.getTank(), "no tank");
        Check.equal(0, result.getCandidates().size());
    }

    public static void testControllerInSharedWallIsNotRecognized() {
        // Two 3x3 tanks share the middle column, which holds the controller (N8-1 forbids it; this
        // state can only arise when something other than the controller completes the second tank).
        Grid grid = Grid.of(
                "#####",
                "#GCG#",
                "#####");
        TankSearchResult result = TankStructure.findTank(2, 1, grid);
        Check.equal(Status.CONTROLLER_IN_SHARED_WALL, result.getStatus());
        Check.equal(2, result.getCandidates().size());
        Check.isNull(result.getTank(), "no tank");
    }

    // ---------- Shared walls (N8-1) ----------

    public static void testTanksMayShareAWallColumn() {
        Grid grid = Grid.of(
                "#####",
                "CG#GC",
                "#####");
        TankSearchResult left = TankStructure.findTank(0, 1, grid);
        Check.equal(Status.FOUND, left.getStatus());
        Check.equal(new TankBounds(0, 0, 3, 3), left.getTank().getBounds());
        TankSearchResult right = TankStructure.findTank(4, 1, grid);
        Check.equal(Status.FOUND, right.getStatus());
        Check.equal(new TankBounds(2, 0, 3, 3), right.getTank().getBounds());
    }

    public static void testTanksMayShareACorner() {
        Grid grid = Grid.of(
                "C##..",
                "#G#..",
                "#####",
                "..#G#",
                "..##C");
        Check.equal(new TankBounds(0, 0, 3, 3), TankStructure.findTank(0, 0, grid).getTank().getBounds());
        Check.equal(new TankBounds(2, 2, 3, 3), TankStructure.findTank(4, 4, grid).getTank().getBounds());
    }

    public static void testValveInSharedWallCountsForBothTanks() {
        Grid grid = Grid.of(
                "#####",
                "CGVGC",
                "#####");
        Check.equal(1, TankStructure.findTank(0, 1, grid).getTank().getValveCount());
        Check.equal(1, TankStructure.findTank(4, 1, grid).getTank().getValveCount());
    }

    public static void testControllerPlacementInSharedWallIsRejected() {
        Grid grid = Grid.of(
                "#####",
                "#G#G#",
                "#####");
        Check.isFalse(TankStructure.canPlaceController(2, 1, grid), "shared wall (N8-1)");
        TankSearchResult result = TankStructure.findTankIfControllerPlaced(2, 1, grid);
        Check.equal(Status.CONTROLLER_IN_SHARED_WALL, result.getStatus());
        Check.equal(2, result.getCandidates().size());
        Check.equal(CellKind.MINERAL_WALL, grid.getCell(2, 1).getKind(), "the check places nothing");
    }

    public static void testControllerPlacementInUnsharedWallIsAllowed() {
        Grid grid = Grid.of(
                "#####",
                "#G#G#",
                "#####");
        Check.isTrue(TankStructure.canPlaceController(0, 1, grid), "left tank only");
        TankSearchResult result = TankStructure.findTankIfControllerPlaced(0, 1, grid);
        Check.equal(Status.FOUND, result.getStatus());
        Check.equal(new TankBounds(0, 0, 3, 3), result.getTank().getBounds());
    }

    public static void testControllerPlacementNextToIncompleteTankIsAllowed() {
        // The right rectangle is broken at (4, 0), so the shared column belongs to one tank only.
        Grid grid = Grid.of(
                "####.",
                "#G#G#",
                "#####");
        Check.isTrue(TankStructure.canPlaceController(2, 1, grid), "only one tank would form");
        Check.equal(new TankBounds(0, 0, 3, 3),
                TankStructure.findTankIfControllerPlaced(2, 1, grid).getTank().getBounds());
    }

    public static void testControllerPlacementWithoutTankIsAllowed() {
        Grid grid = Grid.of(
                "...",
                ".X.",
                "...");
        Check.isTrue(TankStructure.canPlaceController(1, 1, grid), "no tank at all");
        Check.equal(Status.NOT_FOUND, TankStructure.findTankIfControllerPlaced(1, 1, grid).getStatus());
    }

    public static void testEachTileIsReadOnce() {
        Grid grid = Grid.of(
                "#######",
                "#.....#",
                "#.....#",
                "#..C..#",
                "#.....#",
                "#.....#",
                "#######");
        TankStructure.findTank(3, 3, grid);
        Check.equal(1, grid.maxReadsPerTile(), "max reads per tile");
    }

}
