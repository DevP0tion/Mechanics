package devp0tion.mechanics.core;

import devp0tion.mechanics.core.PumpResult.Status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A valve in a wall shared by two recognized tanks counts as a plain wall (N33-1, replacing N15-3,
 * N29-8, N29-10 and the valve part of N13-3; N11-1 dropped): both tanks are recognized, its tier
 * counts for both (N13-5), it is neither a destination nor a source while the wall is shared
 * ({@link TankValveRole}, {@link PipeGrid#setValvePlainWall}), and it works again once the sharing
 * ends. See {@link Grid} for the map legend.
 */
final class TankSharedWallTest {

    private static final TankBounds LEFT = new TankBounds(0, 0, 5, 3);
    private static final TankBounds RIGHT = new TankBounds(4, 0, 5, 3);

    private TankSharedWallTest() {
    }

    /** Two 5x3 tanks sharing the wall x = 4 with a valve (4, 1); controllers at (0, 1) and (8, 1). */
    private static Grid twoTanks() {
        return Grid.of(
                "#########",
                "C...V...C",
                "#########");
    }

    // ---------- Recognition (TankStructure) ----------

    public static void testBothTanksAreRecognized() {
        Grid grid = twoTanks();
        TankSearchResult left = TankStructure.findTank(0, 1, grid);
        TankSearchResult right = TankStructure.findTank(8, 1, grid);
        Check.equal(TankSearchResult.Status.FOUND, left.getStatus(), "left");
        Check.equal(TankSearchResult.Status.FOUND, right.getStatus(), "right");
        Check.equal(LEFT, left.getTank().getBounds());
        Check.equal(RIGHT, right.getTank().getBounds());
        Check.equal(1, left.getTank().getValveCount(), "the valve is in the left border");
        Check.equal(1, right.getTank().getValveCount(), "and in the right one");
    }

    public static void testAKeptTankAndALaterTankAreBothRecognized() {
        // The left controller keeps its tank (the valve was its tank's first); the right tank is
        // completed later around the same valve: both are tanks (N33-1, N15-3 replaced).
        Grid grid = twoTanks().set(0, 1, TankCell.controller(LEFT));
        Check.equal(LEFT, TankStructure.findTank(0, 1, grid).getTank().getBounds(), "left keeps its tank");
        Check.equal(RIGHT, TankStructure.findTank(8, 1, grid).getTank().getBounds(), "right is a tank too");
        Grid bothKept = grid.set(8, 1, TankCell.controller(RIGHT));
        Check.equal(RIGHT, TankStructure.findTank(8, 1, bothKept).getTank().getBounds(), "both kept");
        Check.equal(LEFT, TankStructure.findTank(0, 1, bothKept).getTank().getBounds());
    }

    public static void testAValveMayBePlacedInASharedWall() {
        // N11-1 dropped: a valve placed in the wall two kept tanks share leaves both valid.
        Grid walls = Grid.of(
                "#########",
                "C...#...C",
                "#########")
                .set(0, 1, TankCell.controller(LEFT))
                .set(8, 1, TankCell.controller(RIGHT));
        Grid placed = walls.set(4, 1, TankCell.valve(MineralTier.COPPER));
        List<TankValidation> tanks = TankStructure.findTanksWithBorderCell(4, 1, placed);
        Check.equal(2, tanks.size(), "both tanks with the valve in their border");
        Check.isTrue(TankStructure.validate(LEFT, placed).isValid(), "left valid");
        Check.isTrue(TankStructure.validate(RIGHT, placed).isValid(), "right valid");
    }

    public static void testTheSharedValvesTierCountsForBothTanks() {
        // N13-5: the lowest tier of the border cells; the plain wall valve counts with its tier.
        MineralTier gold = MineralTier.values()[5];
        Grid grid = Grid.of(
                "555555555",
                "C...V...C",
                "555555555");
        int copper = 3 * 40 * MineralTier.COPPER.getCapacityMultiplier();
        for (int controllerX : new int[]{0, 8}) {
            TankValidation tank = TankStructure.findTank(controllerX, 1, grid).getTank();
            Check.equal(MineralTier.COPPER, tank.getLowestTier(), "the copper valve: " + tank);
            Check.equal(copper, tank.getCapacity());
        }
        Grid goldValve = grid.set(4, 1, TankCell.valve(gold));
        for (int controllerX : new int[]{0, 8}) {
            TankValidation tank = TankStructure.findTank(controllerX, 1, goldValve).getTank();
            Check.equal(gold, tank.getLowestTier(), "a valve of the walls' tier: " + tank);
            Check.equal(3 * 40 * gold.getCapacityMultiplier(), tank.getCapacity());
        }
    }

    // ---------- The valve's role (TankValveRole) ----------

    /** A controller as the game knows it: its kept tank and whether that tank is recognized now. */
    private static final class Controller {
        final String name;
        TankBounds kept;
        boolean recognized;

        Controller(String name, TankBounds kept, boolean recognized) {
            this.name = name;
            this.kept = kept;
            this.recognized = recognized;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static TankValveRole<Controller> roleAt(int x, int y, Controller... controllers) {
        return TankValveRole.of(x, y, Arrays.asList(controllers), c -> c.kept, c -> c.recognized);
    }

    public static void testAValveOfOneTankWorksForIt() {
        Controller a = new Controller("A", LEFT, true);
        TankValveRole<Controller> role = roleAt(4, 1, a);
        Check.isTrue(role.getTank() == a, "A's valve: " + role);
        Check.isFalse(role.isPlainWall(), "a valve");
        Controller notATankYet = new Controller("B", RIGHT, false);
        Check.isTrue(roleAt(4, 1, a, notATankYet).getTank() == a, "the other side is no tank yet: works as usual");
        Check.isNull(roleAt(4, 0, a).getTank(), "a corner is no valve cell");
        Check.isNull(roleAt(2, 1, a).getTank(), "an interior cell");
        Check.isNull(roleAt(4, 1).getTank(), "no controller");
    }

    public static void testAValveOfTankABecomesAPlainWallWhenTankBIsRecognized() {
        Controller a = new Controller("A", LEFT, true);
        Controller b = new Controller("B", null, false);
        Check.isTrue(roleAt(4, 1, a, b).getTank() == a, "A's valve before B");
        b.kept = RIGHT;
        b.recognized = true;
        TankValveRole<Controller> shared = roleAt(4, 1, a, b);
        Check.isTrue(shared.isPlainWall(), "a plain wall for both (N33-1)");
        Check.isNull(shared.getTank(), "for no tank");
        Check.isTrue(roleAt(4, 1, b, a).isPlainWall(), "whatever the order");
    }

    public static void testTheValveWorksAgainWhenTheSharingEnds() {
        Controller a = new Controller("A", LEFT, true);
        Controller b = new Controller("B", RIGHT, true);
        Check.isTrue(roleAt(4, 1, a, b).isPlainWall(), "shared");
        // B breaks: B keeps its tank, inactive (N13-3), A is the one recognized tank.
        b.recognized = false;
        TankValveRole<Controller> broken = roleAt(4, 1, a, b);
        Check.isFalse(broken.isPlainWall(), "a valve again");
        Check.isTrue(broken.getTank() == a, "A's again");
        // B's controller is gone.
        Check.isTrue(roleAt(4, 1, a).getTank() == a, "B gone");
        // B's controller took another tank.
        b.kept = new TankBounds(8, 0, 3, 3);
        b.recognized = true;
        Check.isTrue(roleAt(4, 1, a, b).getTank() == a, "B keeps another tank");
        // A breaks while B is recognized: the valve works for B.
        b.kept = RIGHT;
        a.recognized = false;
        Check.isTrue(roleAt(4, 1, a, b).getTank() == b, "B's now");
    }

    public static void testWithoutARecognizedTank() {
        Controller a = new Controller("A", LEFT, false);
        Check.isTrue(roleAt(4, 1, a).getTank() == a, "the one kept tank, inactive (5-9)");
        Check.isFalse(roleAt(4, 1, a).isPlainWall(), "no plain wall");
        Controller b = new Controller("B", RIGHT, false);
        TankValveRole<Controller> none = roleAt(4, 1, a, b);
        Check.isNull(none.getTank(), "TODO(design) reading: two kept tanks, none recognized, is no tank");
        Check.isFalse(none.isPlainWall(), "not a shared wall of recognized tanks");
    }

    // ---------- The pipe engine (PipeGrid) ----------

    /** Full basic pipes (1..3, 0) ending at {@code valve} (4, 0); the pump goes at (0, 0). */
    private static PipeGrid line(TankValve valve) {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        grid.placeValve(4, 0, valve);
        Fluids.fill(grid, 1, 3, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        return grid;
    }

    public static void testAPlainWallValveIsNoDestination() {
        TankValve valve = Fluids.valve(1000);
        PipeGrid grid = line(valve);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Check.isTrue(Fluids.cycle(pump).getDelivered(valve) > 0, "a valve: a destination");
        int stored = valve.getTank().getAmount();
        int links = valve.getLinks();
        grid.setValvePlainWall(4, 0, true);
        Check.isTrue(valve.isPlainWall(), "a plain wall");
        Check.isTrue(grid.getLinkedValves(grid.getPipe(3, 0, PipeLayer.BASE)).isEmpty(), "the pipe links no valve");
        PumpResult wall = Fluids.cycle(pump);
        Check.equal(Status.NO_DESTINATION, wall.getStatus(), "the full pipes end at a wall");
        Check.equal(0, wall.getDelivered(valve));
        Check.equal(stored, valve.getTank().getAmount(), "the tank's fluid is untouched");
        Check.equal(links, valve.getLinks(), "its flags stay");
        grid.setValvePlainWall(4, 0, false);
        Check.isTrue(Fluids.cycle(pump).getDelivered(valve) > 0, "a valve again: a destination again");
    }

    public static void testAValvePlacedAsAPlainWallWorksOnceTheSharingEnds() {
        // The game places a valve that is already in a shared wall as a plain wall.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        Fluids.fill(grid, 1, 3, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        TankValve valve = Fluids.valve(1000);
        valve.setPlainWall(true);
        grid.placeValve(4, 0, valve);
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus(), "no destination");
        grid.setValvePlainWall(4, 0, false);
        Check.isTrue(Fluids.cycle(pump).getDelivered(valve) > 0, "a destination from the next cycle");
    }

    public static void testThePlainWallIsAStructureChange() {
        TankValve valve = Fluids.valve(1000);
        PipeGrid grid = line(valve);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.cycle(pump);
        Check.equal(0, grid.getStaleDestinations().size(), "searched");
        long region = TileBuckets.bucketOf(4, 0);
        int before = grid.getRegionChange(region);
        grid.setValvePlainWall(4, 0, true);
        Check.isTrue(grid.getStaleDestinations().contains(PipeGrid.key(4, 0)), "searched again when used (N25-4)");
        int after = grid.getRegionChange(region);
        Check.isTrue(after > before, "a structure change of its region (N28-1)");
        grid.setValvePlainWall(4, 0, true);
        Check.equal(after, grid.getRegionChange(region), "no change: nothing happens");
        Fluids.cycle(pump);
        Check.equal(0, grid.getStaleDestinations().size(), "searched again");
        grid.setValvePlainWall(4, 0, false);
        Check.isTrue(grid.getRegionChange(region) > after, "and back");
        Check.isTrue(grid.getStaleDestinations().contains(PipeGrid.key(4, 0)), "a destination to search again");
    }

    public static void testASummaryThroughThePlainWallsRegionIsInvalid() {
        // N28-1, N28-2: a pump's summary passing the regions of the valve and its pipe.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Fluids.baseLine(grid, 1, 40, 0, MineralTier.IRON);
        TankValve valve = Fluids.valve(100000);
        grid.placeValve(41, 0, valve);
        Fluids.fill(grid, 1, 40, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Check.isTrue(Fluids.cycle(pump).getDelivered(valve) > 0, "delivered");
        Check.equal(1, grid.getSummaries().size(), "summarized (N23-2)");
        PipeGrid.RouteSummary summary = grid.getSummaries().get(0);
        Check.isTrue(grid.isIntact(summary), "intact");
        grid.setValvePlainWall(41, 0, true);
        Check.isFalse(grid.isIntact(summary), "invalid after the plain wall");
    }

    public static void testTheUndergroundPipeOnItsTileExchangesNothing() {
        // Pump (0, 0) -> basic pipe (1, 0) -> underground pipes (1..4, 0) -> valve (4, 0) above.
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Fluids.line(grid, 1, 4, 0, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        TankValve valve = Fluids.valve(1000);
        grid.placeValve(4, 0, valve);
        // The vertical link of the pipe placed over another starts cut (N16-4).
        grid.toggleVertical(1, 0);
        Fluids.fill(grid, 1, 1, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        Fluids.fill(grid, 1, 4, 0, PipeLayer.UNDERGROUND, FluidType.FRESHWATER);
        PipeNode under = grid.getPipe(4, 0, PipeLayer.UNDERGROUND);
        Check.equal(Collections.singletonList(valve), grid.getLinkedValves(under), "linked up to the valve");
        Check.isTrue(Fluids.cycle(pump).getDelivered(valve) > 0, "delivered through the underground pipe");
        grid.setValvePlainWall(4, 0, true);
        Check.isTrue(grid.getLinkedValves(under).isEmpty(), "no link to a plain wall");
        Check.equal(Status.NO_DESTINATION, Fluids.cycle(pump).getStatus(), "nothing to deliver to");
        grid.setValvePlainWall(4, 0, false);
        Check.isTrue(Fluids.cycle(pump).getDelivered(valve) > 0, "delivered again");
    }

    public static void testAPumpNextToAPlainWallValvePullsNothingThroughIt() {
        PipeGrid grid = new PipeGrid(Fluids.uniform(20));
        TankValve source = Fluids.valve(1000);
        source.getTank().insert(FluidType.FRESHWATER, 500);
        grid.placeValve(-1, 0, source);
        Pump pump = new Pump(PumpTier.FIRE);
        pump.setFuelSupply(new Fluids.Logs(100));
        grid.placePump(0, 0, pump);
        Fluids.baseLine(grid, 1, 2, 0, MineralTier.COPPER);
        Fluids.fill(grid, 1, 2, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        TankValve destination = Fluids.valve(1000);
        grid.placeValve(3, 0, destination);
        Fluids.cycle(pump);
        int left = source.getTank().getAmount();
        Check.isTrue(left < 500, "pulled through the valve");

        grid.setValvePlainWall(-1, 0, true);
        Check.isFalse(grid.isPumpValveLinked(pump, Direction.WEST), "not linked to a plain wall");
        Check.isTrue(grid.getSourceValves(pump).isEmpty(), "no source valve");
        Check.equal(Collections.singletonList(Pump.SourceSlot.valve(Direction.WEST)), pump.getSourceSlots(),
                "TODO(confirm) reading: the slot stays, skipped");
        List<Integer> amounts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Fluids.cycle(pump);
            amounts.add(source.getTank().getAmount());
        }
        Check.equal(Arrays.asList(left, left, left), amounts, "nothing pulled through the plain wall");

        grid.setValvePlainWall(-1, 0, false);
        Check.isTrue(grid.isPumpValveLinked(pump, Direction.WEST), "linked again");
        Fluids.cycle(pump);
        Check.isTrue(source.getTank().getAmount() < left, "pulls again");
    }

}
