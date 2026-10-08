package devp0tion.mechanics.core;

import devp0tion.mechanics.core.TankSearchResult.Status;

import java.util.List;

/**
 * {@link TankStructure#findTank}: finding the tank around a controller; placement checks for
 * controllers (N8-1); valves in shared walls (N33-1, no placement check since N11-1 was dropped).
 */
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
        // Two 3x3 tanks share the middle column, which holds a controller that keeps no tank yet
        // (N8-1 forbids placing it there; this state arises when one change completes both tanks,
        // so neither came first). A controller that keeps a tank keeps it (TankOwnershipTest).
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

    public static void testUnownedValveInSharedWallIsFreeForBothTanks() {
        // A valve that belongs to no tank yet, between two tanks completed by the same change: each
        // controller sees it as its own until one of them takes it (N13-3; which one is TODO(design),
        // see TankSearchResult). TankOwnershipTest covers a valve that already has its tank.
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

    // ---------- Valves in shared walls (N33-1) ----------

    public static void testWallCellOfTwoTanksBelongsToBoth() {
        Grid grid = Grid.of(
                "#####",
                "CG#GC",
                "#####");
        List<TankValidation> tanks = TankStructure.findTanksWithBorderCell(2, 1, grid);
        Check.equal(2, tanks.size(), "shared column");
        Check.equal(new TankBounds(0, 0, 3, 3), tanks.get(0).getBounds());
        Check.equal(new TankBounds(2, 0, 3, 3), tanks.get(1).getBounds());
        Check.equal(1, TankStructure.findTanksWithBorderCell(0, 0, grid).size(), "left corner");
        Check.equal(0, TankStructure.findTanksWithBorderCell(1, 1, grid).size(), "interior cell");
    }

    public static void testValveInSharedWallKeepsBothTanks() {
        // N33-1 (N11-1 dropped): a valve placed in the shared column leaves both tanks valid; each
        // lists it, a plain wall for both (TankValveRole).
        Grid grid = Grid.of(
                "#####",
                "CGVGC",
                "#####");
        List<TankValidation> tanks = TankStructure.findTanksWithBorderCell(2, 1, grid);
        Check.equal(2, tanks.size(), "both tanks");
        for (TankValidation tank : tanks) {
            Check.equal(1, tank.getValveCount(), "the valve is in its border: " + tank);
            Check.equal(new GridPos(2, 1), tank.getValves().get(0));
        }
    }

    public static void testValveOnTheSharedCornerOfTwoTanksBreaksBoth() {
        // A valve may not be on a corner (4-5): on the corner both rectangles share, neither is a
        // tank once the valve is there.
        Grid grid = Grid.of(
                "C##..",
                "#G#..",
                "##V##",
                "..#G#",
                "..##C");
        Check.equal(0, TankStructure.findTanksWithBorderCell(2, 2, grid).size(), "corner of both");
        Check.equal(TankValidation.Reason.VALVE_ON_CORNER,
                TankStructure.validate(new TankBounds(0, 0, 3, 3), grid).getReason());
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
