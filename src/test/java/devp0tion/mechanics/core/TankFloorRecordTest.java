package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The controller's record of its tank's interior floors (N29-9): recorded at recognition, the cells
 * grass or snow spread onto since are set back, natural grass or snow there before stays, and a
 * tank without a record (saved before it existed) records at its next judgment without reverting.
 */
final class TankFloorRecordTest {

    private static final int DIRT = 1;
    private static final int GRASS = 2;
    private static final int SNOW = 3;
    private static final int STONE = 4;

    /** A 5x4 tank: interior x 1..3, y 1..2 (six cells). */
    private static final TankBounds TANK = new TankBounds(0, 0, 5, 4);

    private TankFloorRecordTest() {
    }

    /** Floors on a map (dirt by default); grass and snow are the natural spread floors. */
    private static final class Floors implements TankFloorRecord.FloorLookup {
        final Map<Long, Integer> floors = new HashMap<>();
        final Set<Long> unloaded = new HashSet<>();

        Floors set(int x, int y, int floor) {
            floors.put(PipeGrid.key(x, y), floor);
            return this;
        }

        Floors unload(int x, int y) {
            unloaded.add(PipeGrid.key(x, y));
            return this;
        }

        Floors load(int x, int y) {
            unloaded.remove(PipeGrid.key(x, y));
            return this;
        }

        @Override
        public boolean isLoaded(int tileX, int tileY) {
            return !unloaded.contains(PipeGrid.key(tileX, tileY));
        }

        @Override
        public int getFloor(int tileX, int tileY) {
            Integer floor = floors.get(PipeGrid.key(tileX, tileY));
            return floor == null ? DIRT : floor;
        }

        @Override
        public boolean isNaturalSpread(int floor) {
            return floor == GRASS || floor == SNOW;
        }

        /** Applies the reverts as the game does. */
        void apply(List<TankFloorRecord.Revert> reverts) {
            for (TankFloorRecord.Revert revert : reverts) {
                set(revert.tileX, revert.tileY, revert.floor);
            }
        }
    }

    private static TankFloorRecord.Revert revert(int x, int y, int floor) {
        return new TankFloorRecord.Revert(x, y, floor);
    }

    public static void testRecordsEveryInteriorCellInReadingOrder() {
        Floors floors = new Floors().set(1, 1, GRASS).set(3, 2, STONE);
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        Check.equal(Arrays.toString(new int[]{GRASS, DIRT, DIRT, DIRT, DIRT, STONE}), Arrays.toString(record.getFloors()));
        Check.equal(GRASS, record.getFloor(1, 1));
        Check.equal(STONE, record.getFloor(3, 2));
        Check.equal(TankFloorRecord.UNKNOWN, record.getFloor(0, 0), "the border is not recorded");
    }

    public static void testSpreadGrassAndSnowGoBack() {
        Floors floors = new Floors();
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        floors.set(1, 1, GRASS).set(2, 2, SNOW);
        Check.equal(Arrays.asList(revert(1, 1, DIRT), revert(2, 2, DIRT)), record.check(floors), "N29-9");
    }

    public static void testPreExistingNaturalFloorsStay() {
        // Grass recorded at the recognition stays, also when grass spread next to it since.
        Floors floors = new Floors().set(1, 1, GRASS).set(3, 1, SNOW);
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        floors.set(2, 1, GRASS);
        Check.equal(Collections.singletonList(revert(2, 1, DIRT)), record.check(floors), "only the spread cell");
    }

    public static void testOtherDifferencesAreLeftAsTheyAre() {
        Floors floors = new Floors().set(1, 1, GRASS);
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        floors.set(1, 1, DIRT).set(2, 1, STONE);
        Check.equal(0, record.check(floors).size(), "not grass or snow: nothing goes back");
        Check.equal(GRASS, record.getFloor(1, 1), "the record stays");
        Check.equal(DIRT, record.getFloor(2, 1));
    }

    public static void testGrassOnAnotherRecordedFloorGoesBackToIt() {
        Floors floors = new Floors().set(1, 1, SNOW).set(2, 1, STONE);
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        floors.set(1, 1, GRASS).set(2, 1, GRASS);
        Check.equal(Arrays.asList(revert(1, 1, SNOW), revert(2, 1, STONE)), record.check(floors));
    }

    public static void testUnloadedCellsAreRecordedWhenTheyLoadWithoutGoingBack() {
        Floors floors = new Floors().unload(3, 1).unload(3, 2);
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        Check.equal(TankFloorRecord.UNKNOWN, record.getFloor(3, 1));
        floors.set(3, 1, GRASS).set(2, 1, GRASS).load(3, 1);
        List<TankFloorRecord.Revert> reverts = record.check(floors);
        Check.equal(Collections.singletonList(revert(2, 1, DIRT)), reverts, "(3, 1) has no record yet");
        floors.apply(reverts);
        Check.equal(GRASS, record.getFloor(3, 1), "recorded now");
        Check.equal(TankFloorRecord.UNKNOWN, record.getFloor(3, 2), "still not loaded");
        floors.set(3, 1, DIRT);
        Check.equal(0, record.check(floors).size(), "dirt on recorded grass is not natural spread");
    }

    public static void testUnloadedCellsGoBackWhenALaterJudgmentFindsThemLoaded() {
        Floors floors = new Floors();
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        floors.set(3, 2, GRASS).unload(3, 2);
        Check.equal(0, record.check(floors).size(), "not loaded: left for later");
        floors.load(3, 2);
        Check.equal(Collections.singletonList(revert(3, 2, DIRT)), record.check(floors));
    }

    // ---------- after a judgment ----------

    public static void testANewlyRecognizedTankRecordsWithoutGoingBack() {
        Floors floors = new Floors().set(1, 1, GRASS);
        List<TankFloorRecord.Revert> reverts = new ArrayList<>();
        TankFloorRecord record = TankFloorRecord.afterJudgment(null, TANK, false, floors, reverts);
        Check.equal(0, reverts.size());
        Check.equal(GRASS, record.getFloor(1, 1), "pre-existing grass is recorded");
    }

    public static void testAnActiveTankGoesBackToItsRecord() {
        Floors floors = new Floors();
        List<TankFloorRecord.Revert> reverts = new ArrayList<>();
        TankFloorRecord record = TankFloorRecord.afterJudgment(null, TANK, false, floors, reverts);
        floors.set(2, 2, GRASS);
        TankFloorRecord after = TankFloorRecord.afterJudgment(record, TANK, true, floors, reverts);
        Check.isTrue(after == record, "the same record");
        Check.equal(Collections.singletonList(revert(2, 2, DIRT)), reverts);
        floors.apply(reverts);
        reverts.clear();
        TankFloorRecord.afterJudgment(after, TANK, true, floors, reverts);
        Check.equal(0, reverts.size(), "the judgment after setting it back finds nothing");
    }

    public static void testNoRecordWithoutAnActiveTank() {
        Floors floors = new Floors();
        List<TankFloorRecord.Revert> reverts = new ArrayList<>();
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        floors.set(1, 1, GRASS);
        Check.isNull(TankFloorRecord.afterJudgment(record, null, true, floors, reverts), "inactive: no record");
        Check.equal(0, reverts.size(), "nothing goes back in an inactive tank");
    }

    public static void testRecognizedAgainTakesANewRecord() {
        // The tank was inactive (or is another rectangle): what spread meanwhile is there before the
        // recognition and stays.
        Floors floors = new Floors();
        List<TankFloorRecord.Revert> reverts = new ArrayList<>();
        TankFloorRecord record = TankFloorRecord.take(TANK, floors);
        floors.set(1, 1, GRASS);
        TankFloorRecord again = TankFloorRecord.afterJudgment(record, TANK, false, floors, reverts);
        Check.equal(0, reverts.size(), "newly active");
        Check.equal(GRASS, again.getFloor(1, 1));
        TankBounds moved = new TankBounds(0, 0, 4, 4);
        TankFloorRecord other = TankFloorRecord.afterJudgment(again, moved, true, floors, reverts);
        Check.equal(0, reverts.size(), "other bounds");
        Check.equal(moved, other.getBounds());
    }

    public static void testATankSavedWithoutARecordRecordsAtItsNextJudgment() {
        // Recognized before the record existed: grass that spread while it was dormant stays this
        // time; what spreads after it is set back.
        Floors floors = new Floors().set(1, 1, GRASS);
        List<TankFloorRecord.Revert> reverts = new ArrayList<>();
        TankFloorRecord record = TankFloorRecord.afterJudgment(null, TANK, true, floors, reverts);
        Check.equal(0, reverts.size(), "no record yet: nothing goes back");
        Check.equal(GRASS, record.getFloor(1, 1));
        floors.set(2, 1, GRASS);
        TankFloorRecord.afterJudgment(record, TANK, true, floors, reverts);
        Check.equal(Collections.singletonList(revert(2, 1, DIRT)), reverts);
    }

    // ---------- saving ----------

    public static void testRestoreFromSavedFloors() {
        TankFloorRecord record = TankFloorRecord.restore(TANK, new int[]{GRASS, DIRT, -5, DIRT, DIRT, STONE});
        Check.equal(GRASS, record.getFloor(1, 1));
        Check.equal(TankFloorRecord.UNKNOWN, record.getFloor(3, 1), "a negative floor is no record");
        Check.isNull(TankFloorRecord.restore(TANK, new int[]{DIRT}), "does not fit the bounds");
        Check.isNull(TankFloorRecord.restore(null, new int[0]), "no tank");
        Check.equal(TANK.getInteriorCellCount(), TankFloorRecord.take(TANK, new Floors()).getFloors().length,
                "one floor per interior cell");
    }

}
