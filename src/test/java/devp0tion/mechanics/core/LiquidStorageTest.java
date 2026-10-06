package devp0tion.mechanics.core;

/**
 * {@link LiquidStorage} capacity and type rules (12-7), tank storage (5-9, N4-4, N11-2, N13-4), valves
 * (N7-3, N11-3) and sources (11-1, 11-2).
 */
final class LiquidStorageTest {

    private LiquidStorageTest() {
    }

    public static void testInsertStopsAtCapacity() {
        LiquidStorage storage = new LiquidStorage(50);
        Check.equal(30, storage.insert(FluidType.FRESHWATER, 30));
        Check.equal(20, storage.insert(FluidType.FRESHWATER, 30), "only the room left");
        Check.equal(50, storage.getAmount());
        Check.isTrue(storage.isFull(), "full");
        Check.equal(0, storage.insert(FluidType.FRESHWATER, 1), "full storage takes nothing");
    }

    public static void testOneFluidAtATime() {
        LiquidStorage storage = new LiquidStorage(50);
        storage.insert(FluidType.LAVA, 10);
        Check.equal(0, storage.insert(FluidType.SEAWATER, 10), "another fluid is refused (12-7)");
        Check.equal(0, storage.getSpaceFor(FluidType.SEAWATER));
        Check.equal(40, storage.getSpaceFor(FluidType.LAVA));
        Check.isFalse(storage.canHold(FluidType.SEAWATER), "seawater");
        Check.isFalse(storage.canHold(FluidType.FRESHWATER), "seawater and freshwater differ (12-2)");
        Check.equal(FluidType.LAVA, storage.getFluid());
    }

    public static void testEmptyingClearsTheFluid() {
        LiquidStorage storage = new LiquidStorage(50);
        storage.insert(FluidType.LAVA, 10);
        Check.equal(0, storage.extract(FluidType.SLIME, 10), "wrong fluid");
        Check.equal(4, storage.extract(FluidType.LAVA, 4));
        Check.equal(6, storage.extract(FluidType.LAVA, 100), "only what is there");
        Check.isTrue(storage.isEmpty(), "empty");
        Check.isNull(storage.getFluid(), "type cleared");
        Check.equal(10, storage.insert(FluidType.SLIME, 10), "any fluid fits again");
    }

    public static void testNullAndNegativeAmounts() {
        LiquidStorage storage = new LiquidStorage(10);
        Check.equal(0, storage.insert(null, 5), "null fluid");
        Check.throwsException(IllegalArgumentException.class, () -> storage.insert(FluidType.LAVA, -1));
        Check.throwsException(IllegalArgumentException.class, () -> storage.extract(FluidType.LAVA, -1));
        Check.throwsException(IllegalArgumentException.class, () -> new LiquidStorage(-1));
    }

    public static void testSetContentsForLoading() {
        LiquidStorage storage = new LiquidStorage(10);
        storage.setContents(FluidType.OOZE, 25);
        Check.equal(25, storage.getAmount(), "may exceed the capacity, nothing is destroyed");
        Check.equal(0, storage.getSpaceFor(FluidType.OOZE), "over capacity takes nothing");
        Check.throwsException(IllegalArgumentException.class, () -> storage.setContents(FluidType.OOZE, 0));
        Check.throwsException(IllegalArgumentException.class, () -> storage.setContents(null, 5));
        storage.setContents(null, 0);
        Check.isTrue(storage.isEmpty(), "cleared");
    }

    public static void testTankCapacityComesFromTheStructure() {
        TankStorage tank = new TankStorage();
        Check.isFalse(tank.isActive(), "new controller");
        Check.equal(0, tank.insert(FluidType.FRESHWATER, 10), "inactive tank takes nothing");
        TankValidation structure = TankStructure.validate(0, 0, 4, 3, Grid.of(
                "#C1#",
                "#..#",
                "####"));
        tank.applyStructure(structure);
        Check.isTrue(tank.isActive(), "valid structure");
        Check.equal(2 * 40, tank.getCapacity(), "2 cells x 40 x copper 1 (N4-4)");
    }

    public static void testBrokenTankKeepsItsFluid() {
        TankStorage tank = Fluids.tank(100);
        tank.insert(FluidType.FRESHWATER, 70);
        tank.applyStructure(TankStructure.validate(0, 0, 3, 3, Grid.of("#C#", "#.#", "#.#")));
        Check.isFalse(tank.isActive(), "a broken wall deactivates the tank (5-9)");
        Check.equal(70, tank.getAmount(), "fluid kept (5-9)");
        Check.equal(0, tank.insert(FluidType.FRESHWATER, 10), "inactive: takes nothing");
        Check.equal(0, tank.extract(FluidType.FRESHWATER, 10), "inactive: gives nothing");
        Check.isNull(tank.getSourceFluid(), "inactive: no source fluid");
        tank.applyStructure(Fluids.validTank(100));
        Check.equal(10, tank.extract(FluidType.FRESHWATER, 10), "active again");
    }

    public static void testSmallerRebuiltTankLosesTheExcess() {
        TankStorage tank = Fluids.tank(100);
        tank.insert(FluidType.LAVA, 80);
        Check.equal(40, tank.applyStructure(Fluids.validTank(40)), "amount lost (N11-2)");
        Check.equal(40, tank.getAmount(), "clamped to the new capacity");
        Check.equal(FluidType.LAVA, tank.getFluid(), "the rest stays");
        Check.isTrue(tank.isFull(), "full");
        tank.applyStructure(Fluids.validTank(100));
        Check.equal(40, tank.getAmount(), "a bigger tank does not bring the excess back");
        Check.equal(60, tank.getSpaceFor(FluidType.LAVA));
    }

    public static void testRebuiltTankThatStillFitsLosesNothing() {
        TankStorage tank = Fluids.tank(100);
        tank.insert(FluidType.SLIME, 80);
        Check.equal(0, tank.applyStructure(Fluids.validTank(80)), "same as the stored amount");
        Check.equal(80, tank.getAmount());
        Check.equal(0, tank.applyStructure(Fluids.validTank(200)), "bigger");
        Check.equal(80, tank.getAmount());
    }

    public static void testBrokenTankDiscardsNothing() {
        // Only a valid rebuild discards (N11-2); a broken tank keeps everything (5-9).
        TankStorage tank = Fluids.tank(100);
        tank.insert(FluidType.OOZE, 90);
        Check.equal(0, tank.applyStructure(null), "broken: nothing lost");
        Check.equal(90, tank.getAmount());
        Check.equal(100, tank.getCapacity(), "the last capacity is kept");
    }

    public static void testInvalidTankKeepsAllUntilValidAgainWithLessRoom() {
        // N13-4: while invalid, everything is kept, however long; the moment it is valid again
        // with a smaller capacity, the excess is lost.
        TankStorage tank = Fluids.tank(100);
        tank.insert(FluidType.SLIME, 90);
        for (int i = 0; i < 3; i++) {
            Check.equal(0, tank.applyStructure(null), "invalid: nothing lost");
        }
        Check.equal(90, tank.getAmount());
        Check.equal(50, tank.applyStructure(Fluids.validTank(40)), "valid again with 40: 50 lost at once");
        Check.equal(40, tank.getAmount());
    }

    public static void testTemporarySmallerTankWhileExtendingLosesTheExcess() {
        // N13-4: a tank is extended to the right. Its right wall comes down (invalid, fluid kept);
        // a wall placed inside first forms a smaller valid rectangle for a moment, and the excess is
        // lost then; the bigger tank completed afterwards does not bring it back.
        TankBounds original = new TankBounds(0, 0, 4, 3);
        TankStorage tank = new TankStorage();
        Grid built = Grid.of(
                "#C##",
                "#..#",
                "####").set(1, 0, TankCell.controller(original));
        tank.applyStructure(TankStructure.findTank(1, 0, built).getTank());
        Check.equal(80, tank.getCapacity(), "2 cells x 40");
        tank.insert(FluidType.FRESHWATER, 80);

        Grid opened = Grid.of(
                "#C###",
                "#....",
                "#####").set(1, 0, TankCell.controller(original));
        Check.equal(0, tank.applyStructure(TankStructure.findTank(1, 0, opened).getTank()), "opened: kept");
        Check.isFalse(tank.isActive(), "invalid while open");
        Check.equal(80, tank.getAmount());

        Grid smaller = Grid.of(
                "#C###",
                "#.#..",
                "#####").set(1, 0, TankCell.controller(original));
        TankValidation small = TankStructure.findTank(1, 0, smaller).getTank();
        Check.equal(new TankBounds(0, 0, 3, 3), small.getBounds(), "the smaller rectangle");
        Check.equal(40, tank.applyStructure(small), "40 lost at once");

        Grid extended = Grid.of(
                "#C####",
                "#....#",
                "######").set(1, 0, TankCell.controller(small.getBounds()));
        TankValidation big = TankStructure.findTank(1, 0, extended).getTank();
        Check.equal(new TankBounds(0, 0, 6, 3), big.getBounds());
        Check.equal(0, tank.applyStructure(big));
        Check.equal(40, tank.getAmount(), "the lost fluid does not come back");
        Check.equal(160, tank.getCapacity());
    }

    public static void testLoadedFluidIsClampedOnlyByTheRecognizedTank() {
        // A controller loads its saved fluid before its tank is recognized (capacity 0, inactive).
        TankStorage tank = new TankStorage();
        tank.setContents(FluidType.SEAWATER, 120);
        Check.equal(120, tank.getAmount(), "kept while inactive");
        Check.equal(0, tank.applyStructure(Fluids.validTank(120)), "same tank: nothing lost");
        Check.equal(120, tank.getAmount());
    }

    public static void testRebuiltTankLosingEverythingClearsTheFluid() {
        TankStorage tank = Fluids.tank(100);
        tank.insert(FluidType.LAVA, 50);
        // Capacity 0 is not a real tank size; it checks that the fluid type is cleared.
        tank.applyStructure(Fluids.validTank(0));
        Check.isTrue(tank.isEmpty(), "nothing fits");
        Check.isNull(tank.getFluid(), "type cleared");
    }

    public static void testOpenWaterIsInfinite() {
        LiquidTileSource tile = LiquidTileSource.infinite(FluidType.SEAWATER);
        Check.equal(FluidType.SEAWATER, tile.getSourceFluid());
        Check.equal(Integer.MAX_VALUE, tile.getAvailable(FluidType.SEAWATER), "infinite (N19-3 ①)");
        Check.equal(40, tile.extract(FluidType.SEAWATER, 40));
        Check.equal(40, tile.extract(FluidType.SEAWATER, 40), "never used up (N19-3 ①)");
        Check.equal(0, tile.extract(FluidType.FRESHWATER, 40), "other fluid");
        Check.equal(0, tile.getAvailable(FluidType.FRESHWATER), "other fluid");
    }

    public static void testValveFeedsItsTank() {
        TankStorage tank = Fluids.tank(30);
        TankValve valve = Fluids.valveOf(tank);
        Check.equal(30, valve.getSpaceFor(FluidType.FRESHWATER));
        valve.setEnabled(false);
        Check.equal(0, valve.getSpaceFor(FluidType.FRESHWATER), "disabled by wire (N7-3)");
        valve.setEnabled(true);
        valve.setTank(null);
        Check.equal(0, valve.getSpaceFor(FluidType.FRESHWATER), "no tank");
        Check.isTrue(new TankValve().isEnabled(), "accepts by default (N7-3)");
    }

    public static void testWireSignalSwitchesTheValveOff() {
        TankValve valve = Fluids.valve(30);
        valve.applyWireSignal(true);
        Check.isFalse(valve.isEnabled(), "signal: off (N11-3)");
        Check.equal(0, valve.getSpaceFor(FluidType.FRESHWATER), "off: accepts nothing");
        valve.applyWireSignal(false);
        Check.isTrue(valve.isEnabled(), "no signal: on (N11-3)");
        Check.equal(30, valve.getSpaceFor(FluidType.FRESHWATER));
    }

}
