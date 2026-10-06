package devp0tion.mechanics.core;

/** {@link LiquidStorage} capacity and type rules (12-7), tank storage (5-9, N4-4) and sources (11-1, 11-2). */
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

    public static void testSmallerRebuiltTankKeepsTheExcess() {
        TankStorage tank = Fluids.tank(100);
        tank.insert(FluidType.LAVA, 80);
        tank.applyStructure(Fluids.validTank(40));
        Check.equal(80, tank.getAmount(), "nothing destroyed");
        Check.equal(0, tank.getSpaceFor(FluidType.LAVA), "over capacity");
        tank.extract(FluidType.LAVA, 50);
        Check.equal(10, tank.getSpaceFor(FluidType.LAVA), "room again once under capacity");
    }

    public static void testLiquidTileIsInfinite() {
        LiquidTileSource tile = new LiquidTileSource(FluidType.SEAWATER);
        Check.equal(FluidType.SEAWATER, tile.getSourceFluid());
        Check.equal(Integer.MAX_VALUE, tile.getAvailable(FluidType.SEAWATER), "infinite (11-2)");
        Check.equal(40, tile.extract(FluidType.SEAWATER, 40));
        Check.equal(40, tile.extract(FluidType.SEAWATER, 40), "never used up (11-2)");
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

}
