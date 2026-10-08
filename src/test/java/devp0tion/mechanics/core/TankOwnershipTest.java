package devp0tion.mechanics.core;

import devp0tion.mechanics.core.TankSearchResult.Status;
import devp0tion.mechanics.core.TankValidation.Reason;

import java.util.List;

/**
 * First come, first served for controllers (N13-3) and the controller placement rejection judged
 * against the tanks the controllers hold now (N8-1). Valves in shared walls: {@link TankSharedWallTest}
 * (N33-1). See {@link Grid} for the map legend.
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
        // one valid for the controller, which takes it, with the valve on both borders.
        Grid grid = Grid.of(
                "#C##",
                "#..#",
                "#V##")
                .set(1, 0, TankCell.controller(LEFT));
        TankSearchResult result = TankStructure.findTank(1, 0, grid);
        Check.equal(Status.FOUND, result.getStatus());
        Check.equal(new TankBounds(0, 0, 4, 3), result.getTank().getBounds());
        Check.equal(1, result.getTank().getValveCount(), "the valve");
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

    // ---------- Placement checks against the held tanks (N8-1) ----------

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

    public static void testHeldTanksIncludeAForeignControllersWall() {
        Grid grid = Grid.of(
                "#####",
                "#GCGC",
                "#####").set(2, 1, TankCell.controller(LEFT));
        List<TankValidation> tanks = TankStructure.findTanksWithBorderCell(2, 1, grid);
        Check.equal(2, tanks.size(), "left (its own) and right (foreign there)");
    }

}
