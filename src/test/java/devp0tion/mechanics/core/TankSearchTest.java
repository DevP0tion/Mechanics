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

    public static void testControllerSharedByTwoTanksIsAmbiguous() {
        // Two 3x3 tanks share the middle column, which holds the controller.
        Grid grid = Grid.of(
                "#####",
                "#GCG#",
                "#####");
        TankSearchResult result = TankStructure.findTank(2, 1, grid);
        Check.equal(Status.AMBIGUOUS, result.getStatus());
        Check.equal(2, result.getCandidates().size());
        Check.isNull(result.getTank(), "no single tank");
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
