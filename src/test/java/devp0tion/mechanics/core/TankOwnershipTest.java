package devp0tion.mechanics.core;

import devp0tion.mechanics.core.TankSearchResult.Status;
import devp0tion.mechanics.core.TankValidation.Reason;

import java.util.List;

/**
 * First come, first served (N13-3), no shared valves (N15-3) and the placement rejections judged
 * against the tanks their controllers hold now (N8-1, N11-1). See {@link Grid} for the map legend.
 */
final class TankOwnershipTest {

    private static final TankBounds LEFT = new TankBounds(0, 0, 3, 3);
    private static final TankBounds RIGHT = new TankBounds(2, 0, 3, 3);

    private TankOwnershipTest() {
    }

    // ---------- Controllers ----------

    public static void testControllerKeepsItsTankWhenASecondTankForms() {
        // The controller recognized the left tank first; walls placed later complete the right
        // rectangle around it too. It stays with the left tank.
        Grid grid = Grid.of(
                "#####",
                "#GCG#",
                "#####").set(2, 1, TankCell.controller(LEFT));
        TankSearchResult result = TankStructure.findTank(2, 1, grid);
        Check.equal(Status.FOUND, result.getStatus());
        Check.equal(LEFT, result.getTank().getBounds());
        Check.equal(1, result.getCandidates().size(), "only the kept tank");
    }

    public static void testForeignControllerDoesNotCountForTheNewTank() {
        // The shared column's controller keeps the left tank. For the right tank it is foreign and
        // does not count toward its one controller, so the right controller has its own tank.
        Grid grid = Grid.of(
                "#####",
                "#GCGC",
                "#####").set(2, 1, TankCell.controller(LEFT));
        TankValidation right = TankStructure.findTank(4, 1, grid).getTank();
        Check.equal(RIGHT, right.getBounds());
        Check.equal(new GridPos(4, 1), right.getController());
        Check.equal(LEFT, TankStructure.findTank(2, 1, grid).getTank().getBounds(), "left keeps its controller");
        TankValidation rightAlone = TankStructure.validate(RIGHT, grid);
        Check.isTrue(rightAlone.isValid(), "valid without a point of view: " + rightAlone);
        Check.equal(new GridPos(4, 1), rightAlone.getController());
    }

    public static void testForeignControllerAloneIsNoController() {
        Grid grid = Grid.of(
                "#####",
                "#GCG#",
                "#####").set(2, 1, TankCell.controller(LEFT));
        Check.equal(Reason.NO_CONTROLLER, TankStructure.validate(RIGHT, grid).getReason());
    }

    public static void testSecondControllerInOneTankStillBreaksIt() {
        // TODO(design) review #15④: a controller placed in the border of one existing tank keeps no
        // tank, so it counts as a second controller and the tank stops being valid (unchanged).
        Grid grid = Grid.of(
                "#C#",
                "#G#",
                "#C#").set(1, 0, TankCell.controller(LEFT));
        TankSearchResult result = TankStructure.findTank(1, 0, grid);
        Check.equal(Status.NOT_FOUND, result.getStatus());
        Check.equal(Reason.MULTIPLE_CONTROLLERS, TankStructure.validate(LEFT, grid, new GridPos(1, 0)).getReason());
    }

    public static void testControllerMovesToARebuiltTank() {
        // The kept 3x3 tank is no longer valid (its right wall is gone); the bigger rectangle is the
        // one valid for the controller, which takes it. Its own valve on both borders stays its own.
        Grid grid = Grid.of(
                "#C##",
                "#..#",
                "#V##")
                .set(1, 0, TankCell.controller(LEFT))
                .set(1, 2, TankCell.valve(MineralTier.COPPER, new GridPos(1, 0)));
        TankSearchResult result = TankStructure.findTank(1, 0, grid);
        Check.equal(Status.FOUND, result.getStatus());
        Check.equal(new TankBounds(0, 0, 4, 3), result.getTank().getBounds());
        Check.equal(1, result.getTank().getValveCount(), "the controller's own valve");
    }

    public static void testInvalidKeptTankIsStillRemembered() {
        // A broken kept tank leaves the controller without a tank, but it is still foreign to
        // other rectangles: it keeps its tank (N13-3).
        Grid grid = Grid.of(
                "#####",
                "#.C.C",
                "##.##").set(2, 1, TankCell.controller(LEFT));
        Check.equal(Status.NOT_FOUND, TankStructure.findTank(2, 1, grid).getStatus());
        Grid rightClosed = Grid.of(
                "#####",
                "..C.C",
                "#####").set(2, 1, TankCell.controller(LEFT));
        Check.equal(RIGHT, TankStructure.findTank(4, 1, rightClosed).getTank().getBounds());
    }

    // ---------- Valves (N13-3, N15-3) ----------

    private static Grid sharedValve(TankCell left, TankCell valve) {
        return Grid.of(
                "#####",
                "CGVGC",
                "#####").set(0, 1, left).set(2, 1, valve);
    }

    public static void testValveStaysWithItsFirstTank() {
        Grid grid = sharedValve(TankCell.controller(LEFT), TankCell.valve(MineralTier.COPPER, new GridPos(0, 1)));
        TankValidation left = TankStructure.findTank(0, 1, grid).getTank();
        Check.equal(LEFT, left.getBounds());
        Check.equal(1, left.getValveCount());
        Check.equal(new GridPos(2, 1), left.getValves().get(0));
        // N15-3: the valve is not shared, so the right rectangle is no tank.
        TankSearchResult right = TankStructure.findTank(4, 1, grid);
        Check.equal(Status.NOT_FOUND, right.getStatus());
        Check.equal(Reason.FOREIGN_VALVE, TankStructure.validate(RIGHT, grid, new GridPos(4, 1)).getReason());
        Check.equal(Reason.FOREIGN_VALVE, TankStructure.validate(RIGHT, grid).getReason());
    }

    public static void testValveOfABrokenTankStaysWithIt() {
        Grid grid = Grid.of(
                "#####",
                "CGVGC",
                "##.##")
                .set(0, 1, TankCell.controller(LEFT))
                .set(2, 1, TankCell.valve(MineralTier.COPPER, new GridPos(0, 1)));
        Check.equal(Status.NOT_FOUND, TankStructure.findTank(0, 1, grid).getStatus(), "left is broken");
        Check.equal(new GridPos(0, 1), TankStructure.effectiveValveOwner(2, 1, grid.getCell(2, 1), grid));
    }

    public static void testValveIsFreeWhenItsControllerIsGone() {
        Grid grid = sharedValve(TankCell.mineralWall(MineralTier.COPPER),
                TankCell.valve(MineralTier.COPPER, new GridPos(0, 1)));
        Check.isNull(TankStructure.effectiveValveOwner(2, 1, grid.getCell(2, 1), grid), "owner gone");
        TankValidation right = TankStructure.findTank(4, 1, grid).getTank();
        Check.equal(RIGHT, right.getBounds());
        Check.equal(1, right.getValveCount(), "the right tank may take it now");
    }

    public static void testValveIsFreeWhenItsControllerKeepsAnotherTank() {
        // The remembered controller now keeps a tank without the valve in its border.
        Grid grid = sharedValve(TankCell.controller(new TankBounds(-2, 0, 3, 3)),
                TankCell.valve(MineralTier.COPPER, new GridPos(0, 1)));
        Check.isNull(TankStructure.effectiveValveOwner(2, 1, grid.getCell(2, 1), grid), "not in its tank");
        Check.equal(RIGHT, TankStructure.findTank(4, 1, grid).getTank().getBounds());
    }

    public static void testValveWithAnUnreadableOwnerStaysOwned() {
        // The remembered controller's tile is not loaded (D5): the valve still counts as owned.
        Grid grid = sharedValve(TankCell.controller(LEFT), TankCell.valve(MineralTier.COPPER, new GridPos(0, 1)))
                .set(0, 1, null);
        Check.equal(new GridPos(0, 1), TankStructure.effectiveValveOwner(2, 1, grid.getCell(2, 1), grid));
        Check.equal(Status.NOT_FOUND, TankStructure.findTank(4, 1, grid).getStatus());
    }

    public static void testForeignValveCountsWhereItIsOwnedEvenInAnotherTanksBorder() {
        // The valve's own tank counts it among its valves and its tier.
        Grid grid = sharedValve(TankCell.controller(LEFT), TankCell.valve(MineralTier.COPPER, new GridPos(0, 1)));
        Grid spiderite = Grid.of(
                "aaaaa",
                "CGVGC",
                "aaaaa")
                .set(0, 1, TankCell.controller(LEFT))
                .set(2, 1, TankCell.valve(MineralTier.IRON, new GridPos(0, 1)));
        Check.equal(MineralTier.IRON, TankStructure.findTank(0, 1, spiderite).getTank().getLowestTier());
        Check.equal(1, TankStructure.findTank(0, 1, grid).getTank().getValveCount());
    }

    // ---------- Placement checks against the held tanks (N8-1, N11-1) ----------

    public static void testControllerInWallOfTwoExistingTanksIsRejected() {
        // Round 3 allowed this, because the new controller made both tanks invalid. Under
        // first come, first served both tanks keep their controller, so it is a shared wall (N8-1).
        Grid grid = Grid.of(
                "#####",
                "CG#GC",
                "#####").set(0, 1, TankCell.controller(LEFT)).set(4, 1, TankCell.controller(RIGHT));
        Check.isFalse(TankStructure.canPlaceController(2, 1, grid), "shared wall of two tanks");
        Grid notKeptYet = Grid.of(
                "#####",
                "CG#GC",
                "#####");
        Check.isFalse(TankStructure.canPlaceController(2, 1, notKeptYet), "tanks about to be recognized");
    }

    public static void testControllerInWallOfAnExistingAndANewTankIsRejected() {
        // The left tank exists; the right rectangle would be a tank for the new controller.
        Grid grid = Grid.of(
                "#####",
                "CG#G#",
                "#####").set(0, 1, TankCell.controller(LEFT));
        Check.isFalse(TankStructure.canPlaceController(2, 1, grid), "existing + new tank");
    }

    public static void testControllerInBorderOfOneExistingTankIsAllowed() {
        // TODO(design) review #15④: allowed (not a shared wall); the tank then has two controllers.
        Grid grid = Grid.of(
                "#C#",
                "#G#",
                "###").set(1, 0, TankCell.controller(LEFT));
        Check.isTrue(TankStructure.canPlaceController(1, 2, grid), "one tank only");
    }

    public static void testControllerNextToAForeignControllerIsAllowed() {
        // The shared column's controller keeps the left tank; a new controller on the right
        // rectangle's far wall is part of the right tank only.
        Grid grid = Grid.of(
                "#####",
                "#GCG#",
                "#####").set(2, 1, TankCell.controller(LEFT));
        Check.isTrue(TankStructure.canPlaceController(4, 1, grid), "right tank only");
        Check.equal(RIGHT, TankStructure.findTankIfControllerPlaced(4, 1, grid).getTank().getBounds());
    }

    public static void testValveCompletingTwoTanksIsRejected() {
        Grid grid = Grid.of(
                "#####",
                "CG.GC",
                "#####").set(0, 1, TankCell.controller(LEFT));
        Check.isFalse(TankStructure.canPlaceValve(2, 1, grid), "would be part of two tanks (N11-1)");
    }

    public static void testValveInWallOfOneTankNextToAForeignValveRectangleIsAllowed() {
        // The right rectangle has a valve of the left tank, so it is no tank (N15-3): a new valve in
        // its far wall is part of no tank, and one in the left tank's wall is part of one tank only.
        Grid grid = Grid.of(
                "#####",
                "CGVGC",
                "#####")
                .set(0, 1, TankCell.controller(LEFT))
                .set(2, 1, TankCell.valve(MineralTier.COPPER, new GridPos(0, 1)));
        Check.isTrue(TankStructure.canPlaceValve(3, 0, grid), "right rectangle is no tank");
        Check.equal(0, TankStructure.findTanksIfValvePlaced(3, 0, grid).size());
        List<TankValidation> left = TankStructure.findTanksIfValvePlaced(1, 0, grid);
        Check.equal(1, left.size());
        Check.equal(2, left.get(0).getValveCount(), "its own valve and the placed one");
    }

    public static void testHeldTanksIncludeAForeignControllersWall() {
        Grid grid = Grid.of(
                "#####",
                "#GCGC",
                "#####").set(2, 1, TankCell.controller(LEFT));
        List<TankValidation> tanks = TankStructure.findTanksWithBorderCell(2, 1, grid);
        Check.equal(2, tanks.size(), "left (its own) and right (foreign there)");
    }

}
