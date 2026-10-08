package devp0tion.mechanics.core;

import devp0tion.mechanics.core.TankJudgment.Kind;
import devp0tion.mechanics.core.TankJudgment.Mode;
import devp0tion.mechanics.core.TankJudgment.Prior;
import devp0tion.mechanics.core.TankValidation.Reason;

import java.util.Arrays;
import java.util.Collections;

/**
 * The controller's judgment of its tank (N20-7 tank part, N20-8, N21-3, N22-7, N23-4, N26-3):
 * loaded cells only, the saved judgment while loading, natural growth broken inside active tanks.
 * See {@link Grid} for the map legend; {@link Partial} leaves tiles unloaded.
 */
final class TankJudgmentTest {

    /** The 5x3 tank of {@link #tank()}: controller at (0, 1), valves at (2, 0) and (2, 2). */
    private static final TankBounds TANK = new TankBounds(0, 0, 5, 3);
    private static final GridPos CONTROLLER = new GridPos(0, 1);

    private TankJudgmentTest() {
    }

    /**
     * A {@link Grid} where the tiles with x >= {@code unloadedFromX} are not loaded: they read as
     * {@code null} like the game's unloaded tiles.
     */
    private static final class Partial implements TankCellLookup {
        private final TankCellLookup grid;
        private final int unloadedFromX;

        Partial(TankCellLookup grid, int unloadedFromX) {
            this.grid = grid;
            this.unloadedFromX = unloadedFromX;
        }

        @Override
        public TankCell getCell(int tileX, int tileY) {
            return isLoaded(tileX, tileY) ? grid.getCell(tileX, tileY) : null;
        }

        @Override
        public boolean isLoaded(int tileX, int tileY) {
            return tileX < unloadedFromX;
        }
    }

    /** A copper tank with two valves around an empty 3x1 interior that its controller keeps. */
    private static Grid tank(String interior) {
        return Grid.of(
                "##V##",
                "C" + interior + "#",
                "##V##")
                .set(0, 1, TankCell.controller(TANK));
    }

    private static TankCell grass() {
        return TankCell.of(CellKind.OTHER).withNaturalGrowth(true);
    }

    // ---------- Loaded cells only (N20-7, N21-3) ----------

    public static void testUnloadedBorderCellsAreLeftOut() {
        // The east column (x = 4) is in an unloaded region: left out, the rest is a valid tank.
        TankCellLookup lookup = new Partial(tank("..."), 4);
        TankValidation loaded = TankStructure.validateLoaded(TANK, lookup, CONTROLLER);
        Check.isTrue(loaded.isValid(), "loaded cells only: " + loaded);
        Check.equal(3 * 40, loaded.getCapacity(), "every interior cell counts");
        Check.equal(Reason.INVALID_BORDER_CELL, TankStructure.validate(TANK, lookup, CONTROLLER).getReason(),
                "the full check reads unloaded cells as something else");
    }

    public static void testUnloadedInteriorCellsAreLeftOut() {
        // x >= 3: the last interior cell and the east wall are not loaded; the loaded interior is empty.
        Grid grid = tank("..X");
        Check.isTrue(TankStructure.validateLoaded(TANK, new Partial(grid, 3), CONTROLLER).isValid(),
                "the object on the unloaded cell is not seen");
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, TankStructure.validateLoaded(TANK, new Partial(grid, 5), CONTROLLER).getReason(),
                "once loaded it counts");
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, TankStructure.validateLoaded(TANK, new Partial(tank("X.."), 3), CONTROLLER).getReason(),
                "a loaded cell still counts");
    }

    public static void testLoadedOnlyInteriorConditions() {
        // Loaded glass and empty mixed is still mixed (5-5); all loaded glass is all glass.
        Check.equal(Reason.MIXED_INTERIOR, TankStructure.validateLoaded(TANK, new Partial(tank("G.X"), 3), CONTROLLER).getReason());
        TankValidation glass = TankStructure.validateLoaded(TANK, new Partial(tank("GG."), 3), CONTROLLER);
        Check.equal(TankValidation.InteriorCondition.ALL_GLASS, glass.getInteriorCondition());
    }

    public static void testUnloadedBorderCellsCountWithTheJudgedTier() {
        // N29-1: the unloaded copper wall (x = 4) counts with the tier of the last judgment; the lower
        // of it and the loaded cells' wins, so the capacity stays as judged with every cell loaded.
        Grid grid = Grid.of(
                "55V55",
                "C...#",
                "55V55")
                .set(0, 1, TankCell.controller(TANK))
                .set(2, 0, TankCell.valve(MineralTier.values()[5]))
                .set(2, 2, TankCell.valve(MineralTier.values()[5]));
        int multiplier = MineralTier.values()[5].getCapacityMultiplier();
        int copper = 3 * 40 * MineralTier.COPPER.getCapacityMultiplier();
        Check.equal(copper, TankStructure.validate(TANK, grid, CONTROLLER).getCapacity(), "every cell loaded");
        Check.equal(3 * 40 * multiplier, TankStructure.validateLoaded(TANK, new Partial(grid, 4), CONTROLLER).getCapacity(),
                "without a judged tier: loaded cells only");
        Check.equal(copper, TankStructure.validateLoaded(TANK, new Partial(grid, 4), CONTROLLER, MineralTier.COPPER).getCapacity(),
                "the judged tier for the unloaded wall: as with every cell loaded");
        Check.equal(3 * 40 * multiplier, TankStructure.validateLoaded(TANK, new Partial(grid, 4), CONTROLLER,
                MineralTier.highest()).getCapacity(), "a higher judged tier: the loaded cells' lower one wins");
        TankJudgment.Result change = TankJudgment.judge(0, 1, new Partial(grid, 4), Mode.CHANGE, Prior.ACTIVE, MineralTier.COPPER);
        Check.equal(Kind.KEPT_VALID, change.getKind());
        Check.equal(copper, change.getTank().getCapacity(), "the judgment uses it (N29-1)");
        Check.equal(MineralTier.COPPER, change.getTank().getLowestTier());
    }

    public static void testLoadedOnlyKeepsTheOtherRules() {
        // The size rule still applies with cells left out; a valve on a loaded corner still breaks it (4-5).
        Grid grid = tank("...");
        Check.equal(Reason.TOO_LARGE, TankStructure.validateLoaded(new TankBounds(0, 0, 8, 3), new Partial(grid, 4), CONTROLLER).getReason());
        Check.equal(Reason.VALVE_ON_CORNER, TankStructure.validateLoaded(TANK, new Partial(grid.set(0, 0, TankCell.valve(MineralTier.COPPER)), 4),
                CONTROLLER).getReason());
    }

    // ---------- Loading with a saved judgment (N22-7) ----------

    public static void testLoadingWhilePartlyUnloadedKeepsTheSavedJudgment() {
        // The east wall is not loaded: an active tank stays active as saved, its capacity too.
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(tank("..."), 4), Mode.LOAD, Prior.ACTIVE);
        Check.equal(Kind.SAVED, result.getKind());
        Check.isFalse(result.appliesJudgment(), "the saved judgment stays");
        Check.isTrue(result.isSettled(), "nothing waits");
    }

    public static void testLoadingAnInactiveTankWhilePartlyUnloadedWaitsForTheSearch() {
        // A loaded part that looks valid does not activate a tank saved as inactive; it is searched
        // once the area is loaded, as before.
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(tank("..."), 4), Mode.LOAD, Prior.INACTIVE);
        Check.equal(Kind.SAVED, result.getKind());
        Check.isFalse(result.appliesJudgment(), "stays inactive");
        Check.isFalse(result.isSettled(), "the search waits for the area");
        TankJudgment.Result waiting = TankJudgment.judge(0, 1, new Partial(tank("..."), 4), Mode.SEARCH, Prior.INACTIVE);
        Check.equal(Kind.WAITING, waiting.getKind());
        TankJudgment.Result searched = TankJudgment.judge(0, 1, tank("..."), Mode.SEARCH, Prior.INACTIVE);
        Check.equal(Kind.SEARCHED, searched.getKind());
        Check.equal(TANK, searched.getTank().getBounds(), "found once everything is loaded");
    }

    public static void testLoadingWithTheWholeTankLoadedJudgesIt() {
        // The tank itself is loaded (only cells beyond it are not): judged at once, no need to wait
        // for the whole search area. A wall broken while the controller was not loaded counts.
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(tank("..."), 5), Mode.LOAD, Prior.ACTIVE);
        Check.equal(Kind.KEPT_VALID, result.getKind());
        Check.equal(TANK, result.getTank().getBounds());
        Grid broken = tank("...").set(4, 1, TankCell.of(CellKind.EMPTY));
        TankJudgment.Result invalid = TankJudgment.judge(0, 1, new Partial(broken, 5), Mode.LOAD, Prior.ACTIVE);
        Check.equal(Kind.KEPT_INVALID, invalid.getKind());
        Check.isTrue(invalid.appliesJudgment(), "inactive now");
        Check.isNull(invalid.getTank(), "no tank");
        Check.isFalse(invalid.isSettled(), "a search waits for the area");
    }

    public static void testLoadingWithEverythingLoadedSearches() {
        TankJudgment.Result result = TankJudgment.judge(0, 1, tank("..."), Mode.LOAD, Prior.ACTIVE);
        Check.equal(Kind.SEARCHED, result.getKind());
        Check.equal(TANK, result.getTank().getBounds());
        Check.isTrue(result.isSettled(), "settled");
    }

    // ---------- A search while cells are not loaded (N29-2) ----------

    /** {@link #tank} with a controller that keeps no tank yet. */
    private static Grid newTank() {
        return tank("...").set(0, 1, TankCell.controller()).set(2, 0, TankCell.valve(MineralTier.COPPER))
                .set(2, 2, TankCell.valve(MineralTier.COPPER));
    }

    public static void testAFullyLoadedRectangleIsRecognizedAtOnce() {
        // The search area reaches x = 6, which is not loaded; the tank (x 0..4) is all loaded.
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(newTank(), 6), Mode.CHANGE, Prior.INACTIVE);
        Check.equal(Kind.FOUND_LOADED, result.getKind());
        Check.equal(TANK, result.getTank().getBounds());
        Check.isTrue(result.appliesJudgment(), "recognized now");
    }

    public static void testARectangleTouchingUnloadedCellsWaitsForThem() {
        // x >= 4 not loaded: the tank's east wall is not loaded, so it is not recognized yet.
        TankJudgment.Result waiting = TankJudgment.judge(0, 1, new Partial(newTank(), 4), Mode.CHANGE, Prior.INACTIVE);
        Check.equal(Kind.WAITING, waiting.getKind());
        Check.isFalse(waiting.isSettled(), "tried again when cells load");
        TankJudgment.Result loaded = TankJudgment.judge(0, 1, new Partial(newTank(), 5), Mode.SEARCH, Prior.INACTIVE);
        Check.equal(Kind.FOUND_LOADED, loaded.getKind(), "its cells loaded");
    }

    public static void testAnInvalidKeptTankLooksForAFullyLoadedOne() {
        // The controller keeps a 7x3 rectangle that is no tank (its loaded cells at x = 5 are empty);
        // the 5x3 tank (x 0..4) is all loaded: recognized now, though x >= 6 is not loaded.
        Grid grid = tank("...").set(0, 1, TankCell.controller(new TankBounds(0, 0, 7, 3)));
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(grid, 6), Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Kind.FOUND_LOADED, result.getKind());
        Check.equal(TANK, result.getTank().getBounds());
    }

    // ---------- Two tanks sharing a wall with a valve (N33-1) ----------

    private static final TankBounds LEFT = new TankBounds(0, 0, 5, 3);
    private static final TankBounds RIGHT = new TankBounds(4, 0, 5, 3);
    private static final GridPos SHARED_VALVE = new GridPos(4, 1);

    /** Two 5x3 tanks sharing the wall x = 4 with a valve (4, 1). */
    private static Grid twoTanks() {
        return Grid.of(
                "#########",
                "C...V...C",
                "#########");
    }

    public static void testTwoTanksSharingAValveAreBothRecognized() {
        // N33-1 (N29-8, N29-10 replaced): one change makes both valid at once; whichever judges
        // first, both are recognized, each with the valve in its border (a plain wall for both).
        Grid grid = twoTanks();
        TankJudgment.Result left = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.INACTIVE);
        TankJudgment.Result right = TankJudgment.judge(8, 1, grid, Mode.CHANGE, Prior.INACTIVE);
        Check.equal(Kind.SEARCHED, left.getKind(), "left");
        Check.equal(Kind.SEARCHED, right.getKind(), "right");
        Check.equal(LEFT, left.getTank().getBounds());
        Check.equal(RIGHT, right.getTank().getBounds());
        Check.equal(Collections.singletonList(SHARED_VALVE), left.getTank().getValves());
        Check.equal(Collections.singletonList(SHARED_VALVE), right.getTank().getValves());
        Check.isTrue(left.appliesJudgment() && right.appliesJudgment(), "both recognized");
        Check.isTrue(left.isSettled() && right.isSettled(), "nothing waits");
    }

    public static void testAValveOfATankStaysInItWhenASecondTankIsBuilt() {
        // The left tank has the valve in its border; the right tank built later shares that wall:
        // the left tank keeps its tank, and the right one is recognized too (N33-1, N15-3 replaced).
        Grid grid = twoTanks().set(0, 1, TankCell.controller(LEFT));
        TankJudgment.Result left = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Kind.SEARCHED, left.getKind());
        Check.equal(LEFT, left.getTank().getBounds());
        TankJudgment.Result right = TankJudgment.judge(8, 1, grid, Mode.CHANGE, Prior.INACTIVE);
        Check.equal(RIGHT, right.getTank().getBounds(), "the right tank is recognized");
    }

    public static void testATankNextToOneTouchingUnloadedCellsIsRecognized() {
        // The right tank's east part (x >= 6) is not loaded: the left tank is recognized as always.
        TankJudgment.Result left = TankJudgment.judge(0, 1, new Partial(twoTanks(), 6), Mode.CHANGE, Prior.INACTIVE);
        Check.equal(Kind.FOUND_LOADED, left.getKind());
        Check.equal(LEFT, left.getTank().getBounds());
    }

    // ---------- Changes: loaded cells only (N20-7, N21-3) ----------

    public static void testChangeJudgesWithTheLoadedCellsOnly() {
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(tank("..."), 3), Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Kind.KEPT_VALID, result.getKind());
        Check.equal(Arrays.asList(new GridPos(2, 0), new GridPos(2, 2)), result.getTank().getValves());
        Check.isTrue(result.isSettled(), "settled");
    }

    public static void testChangeMakingTheLoadedPartInvalid() {
        Grid grid = tank("X..");
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(grid, 3), Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Kind.KEPT_INVALID, result.getKind());
        Check.isTrue(result.appliesJudgment(), "inactive");
        Check.isNull(result.getTank(), "no tank");
        Check.isFalse(result.isSettled(), "a search for another tank waits for the area");
    }

    public static void testChangeLeavesOutWhatIsNotLoaded() {
        // N21-3, the same as the pump's 5x5: an object on a cell that is not loaded is not seen.
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(tank("..X"), 3), Mode.CHANGE, Prior.INACTIVE);
        Check.equal(Kind.KEPT_VALID, result.getKind());
    }

    public static void testWithoutAKeptTankTheSearchWaits() {
        Grid grid = Grid.of(
                "#####",
                "C...#",
                "#####");
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(grid, 4), Mode.CHANGE, Prior.INACTIVE);
        Check.equal(Kind.WAITING, result.getKind());
        Check.isFalse(result.appliesJudgment(), "nothing changes");
        Check.isFalse(result.isSettled(), "waits");
        TankJudgment.Result searched = TankJudgment.judge(0, 1, grid, Mode.SEARCH, Prior.INACTIVE);
        Check.equal(TANK, searched.getTank().getBounds(), "a new tank once the area is loaded");
    }

    public static void testFullyLoadedChangeIsTheFullSearch() {
        // The kept tank is gone (its east wall broken); the controller sits in a bigger valid one.
        Grid grid = Grid.of(
                "##V###",
                "C....#",
                "##V###")
                .set(0, 1, TankCell.controller(TANK));
        TankJudgment.Result result = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Kind.SEARCHED, result.getKind());
        Check.equal(new TankBounds(0, 0, 6, 3), result.getTank().getBounds());
        Check.equal(TankStructure.findTank(0, 1, grid).getTank().getBounds(), result.getTank().getBounds(), "same as findTank");
    }

    // ---------- Natural growth (N23-4, N26-3) ----------

    public static void testNaturalGrowthInAnActiveTankIsBroken() {
        Grid grid = tank("...").set(2, 1, grass());
        TankJudgment.Result result = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Collections.singletonList(new GridPos(2, 1)), result.getNaturalGrowth());
        Check.equal(TANK, result.getTank().getBounds(), "the tank stays valid");
        Check.equal(TankValidation.InteriorCondition.ALL_EMPTY, result.getTank().getInteriorCondition());
    }

    public static void testNaturalGrowthBrokenOnLoad() {
        // The saved judgment stays (part not loaded); the loaded growth is broken, the unloaded is not seen.
        Grid grid = tank("...").set(1, 1, grass()).set(3, 1, grass());
        TankJudgment.Result result = TankJudgment.judge(0, 1, new Partial(grid, 3), Mode.LOAD, Prior.ACTIVE);
        Check.equal(Kind.SAVED, result.getKind());
        Check.equal(Collections.singletonList(new GridPos(1, 1)), result.getNaturalGrowth());
        TankJudgment.Result change = TankJudgment.judge(0, 1, new Partial(grid, 3), Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Kind.KEPT_VALID, change.getKind());
        Check.equal(Collections.singletonList(new GridPos(1, 1)), change.getNaturalGrowth());
    }

    public static void testNaturalGrowthInAnInactiveTankStillCounts() {
        // N20-1 covers recognized tanks only: inside an inactive one, growth is not broken.
        Grid grid = tank("...").set(2, 1, grass());
        TankJudgment.Result result = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.INACTIVE);
        Check.equal(0, result.getNaturalGrowth().size());
        Check.isNull(result.getTank(), "invalid");
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, TankStructure.validate(TANK, grid, CONTROLLER).getReason());
    }

    public static void testOtherObjectsAreNotBroken() {
        // Not natural growth (a player's object, or anything else): the tank is invalid as before.
        Grid grid = tank(".X.");
        TankJudgment.Result result = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(0, result.getNaturalGrowth().size());
        Check.isNull(result.getTank(), "invalid");
        // Growth on top of an object of another layer: the other layer still counts.
        Grid carpet = tank("...").set(2, 1, grass().withOtherLayerObject(true));
        TankJudgment.Result withCarpet = TankJudgment.judge(0, 1, carpet, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Collections.singletonList(new GridPos(2, 1)), withCarpet.getNaturalGrowth());
        Check.isNull(withCarpet.getTank(), "the carpet still makes it invalid");
    }

    public static void testNaturalGrowthBrokenAlsoWhenTheTankIsInvalidForAnotherReason() {
        Grid grid = tank("...").set(2, 1, grass()).set(4, 1, TankCell.of(CellKind.EMPTY));
        TankJudgment.Result result = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Collections.singletonList(new GridPos(2, 1)), result.getNaturalGrowth());
        Check.isNull(result.getTank(), "the broken wall still makes it invalid");
    }

    public static void testNaturalGrowthOnlyInsideTheKeptTank() {
        // Growth outside the kept tank's interior (and on its border) is not touched.
        Grid grid = tank("...").set(4, 1, grass()).set(6, 1, grass());
        TankJudgment.Result result = TankJudgment.judge(0, 1, grid, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(0, result.getNaturalGrowth().size());
        Check.isNull(result.getTank(), "growth on the border is not a wall");
    }

    public static void testNaturalGrowthDoesNotMakeAnotherRectangleValid() {
        // The kept 4x3 tank is invalid (its east wall at (3, 1) is gone). The 5x3 rectangle around it
        // would be valid only if the growth in it were broken: it is not taken. The break covers the
        // kept tank's interior; other rectangles see the cells as they are.
        TankBounds kept = new TankBounds(0, 0, 4, 3);
        Grid grid = Grid.of(
                "#C###",
                "#...#",
                "#####")
                .set(1, 0, TankCell.controller(kept))
                .set(2, 1, grass());
        TankJudgment.Result result = TankJudgment.judge(1, 0, grid, Mode.CHANGE, Prior.ACTIVE);
        Check.equal(Kind.SEARCHED, result.getKind());
        Check.equal(Collections.singletonList(new GridPos(2, 1)), result.getNaturalGrowth());
        Check.isNull(result.getTank(), "no tank");
        Check.isTrue(TankStructure.validate(new TankBounds(0, 0, 5, 3), grid.set(2, 1, TankCell.of(CellKind.EMPTY)),
                new GridPos(1, 0)).isValid(), "the bigger rectangle without the growth");
    }

    // ---------- Cells and storage ----------

    public static void testNaturalGrowthFlagOnCells() {
        Check.isTrue(grass().isNaturalGrowth(), "other object");
        Check.isFalse(TankCell.of(CellKind.GLASS).withNaturalGrowth(true).isNaturalGrowth(), "glass is never growth");
        Check.isFalse(TankCell.mineralWall(MineralTier.COPPER).withNaturalGrowth(true).isNaturalGrowth(), "walls neither");
        TankCell onFloor = grass().withTankFloor(true).withOtherLayerObject(true);
        TankCell broken = onFloor.withNaturalGrowthBroken();
        Check.equal(CellKind.EMPTY, broken.getKind());
        Check.isTrue(broken.isTankFloor(), "floor kept");
        Check.isTrue(broken.hasOtherLayerObject(), "other layers kept");
        Check.isFalse(broken.isNaturalGrowth(), "gone");
        TankCell other = TankCell.of(CellKind.OTHER);
        Check.isTrue(other == other.withNaturalGrowthBroken(), "unchanged without growth");
        Check.isFalse(grass().equals(TankCell.of(CellKind.OTHER)), "the flag is part of the cell");
    }

    public static void testStorageRestoresTheSavedJudgment() {
        TankStorage storage = new TankStorage();
        storage.setContents(FluidType.FRESHWATER, 100);
        storage.restoreJudgment(true, 120);
        Check.isTrue(storage.isActive(), "active as saved");
        Check.equal(120, storage.getCapacity());
        Check.equal(100, storage.getAmount());
        Check.equal(20, storage.getSpaceFor(FluidType.FRESHWATER));
        storage.restoreJudgment(false, 40);
        Check.isFalse(storage.isActive(), "inactive as saved");
        Check.equal(100, storage.getAmount(), "nothing discarded: only a judgment loses fluid (N11-2)");
        storage.release();
        storage.restoreJudgment(true, 120);
        Check.isFalse(storage.isActive(), "a released storage stays inactive");
    }

}
