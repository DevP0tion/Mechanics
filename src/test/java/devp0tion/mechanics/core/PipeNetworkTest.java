package devp0tion.mechanics.core;

/**
 * Networks by reach (N13-1), per-network management and merging (N18-2), splitting (N18-3), pipe
 * capacity per tier (N12-3), the removed pipe's fluid (N12-1) and the tier table (N19-8, N15-2).
 */
final class PipeNetworkTest {

    private PipeNetworkTest() {
    }

    private static PipeGrid grid() {
        return new PipeGrid(Fluids.uniform(20));
    }

    // ---------- the tier table ----------

    public static void testIronCarriesEightyAndTheRestIsProvisional() {
        Check.equal(80, PipeTierRules.TABLE.getTransportAmount(MineralTier.IRON), "N19-8: four fire pumps");
        Check.equal(4 * PumpTier.FIRE.getUnitsPerCycle(), PipeTierRules.TABLE.getTransportAmount(MineralTier.IRON));
        for (MineralTier tier : MineralTier.values()) {
            Check.equal(PipeTierRules.PROVISIONAL_TRANSPORT == 80 ? 80 : -1,
                    PipeTierRules.TABLE.getTransportAmount(tier), tier + ": placeholder = iron's value");
            for (FluidType fluid : FluidType.values()) {
                Check.isTrue(PipeTierRules.TABLE.canCarry(tier, fluid), tier + " " + fluid + ": placeholder carries all");
            }
        }
    }

    public static void testTableRowsFollowTheMineralTiers() {
        Check.equal(MineralTier.values().length, PipeTierRules.Row.values().length);
        for (MineralTier tier : MineralTier.values()) {
            Check.equal(tier.name(), PipeTierRules.Row.of(tier).name());
        }
    }

    public static void testEachPipeHoldsItsOwnTiersAmount() {
        PipeGrid grid = new PipeGrid(Fluids.TIERS);
        PipeNode copper = grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        PipeNode gold = grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.GOLD);
        Check.equal(10, copper.getCapacity(), "own tier (N12-3)");
        Check.equal(30, gold.getCapacity(), "own tier, not the network's lowest (N12-3)");
    }

    // ---------- networks by reach ----------

    public static void testEmptyPipesBelongToNoNetwork() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 4, 0, MineralTier.COPPER);
        Check.equal(0, grid.getNetworks().size(), "N13-1");
        Check.isNull(grid.getNetwork(0, 0, PipeLayer.BASE), "no network");
    }

    public static void testNewPumpStartsItsOwnNetwork() {
        PipeGrid grid = grid();
        Pump a = Fluids.pump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Pump b = Fluids.pump(grid, 5, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Check.isTrue(a.getNetwork() != null && a.getNetwork() != b.getNetwork(), "one network each (N18-2)");
        Check.equal(0, a.getNetwork().size(), "no pipe reached yet");
    }

    public static void testReachedPipesFormThePumpsNetwork() {
        PipeGrid grid = grid();
        Pump pump = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.FRESHWATER);
        Fluids.baseLine(grid, 1, 5, 0, MineralTier.COPPER);
        grid.placeValve(6, 0, Fluids.valve(1000));
        Fluids.cycle(pump);
        PipeNetwork network = pump.getNetwork();
        Check.equal(1, network.size(), "only the reached pipe (N13-1)");
        Check.isTrue(grid.getNetwork(1, 0, PipeLayer.BASE) == network, "the pump's network");
        Check.isNull(grid.getNetwork(2, 0, PipeLayer.BASE), "not reached yet");
        Fluids.cycle(pump);
        Check.equal(2, pump.getNetwork().size(), "grows as the fluid reaches pipes");
        Check.equal(FluidType.FRESHWATER, pump.getNetwork().getFluid());
    }

    public static void testSameFluidNetworksMergeWhenTheFluidMeets() {
        // Two pumps push toward a valve between them; each starts its own network (N18-2).
        PipeGrid grid = grid();
        Pump left = Fluids.fueledPump(grid, 0, 0, PumpTier.FIRE, FluidType.LAVA);
        Pump right = Fluids.fueledPump(grid, 4, 0, PumpTier.FIRE, FluidType.LAVA);
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        grid.placeValve(2, 1, Fluids.valve(1000));
        Fluids.push(grid, left, right);
        Check.isTrue(left.getNetwork() != right.getNetwork(), "1,0 and 3,0 reached, 2,0 still empty");
        for (int i = 0; i < 3; i++) {
            Fluids.push(grid, left, right);
        }
        Check.isTrue(left.getNetwork() == right.getNetwork(), "the fluid met at 2,0: one network (N18-2)");
        Check.equal(3, left.getNetwork().size());
        Check.equal(2, left.getNetwork().getPumps().size());
    }

    public static void testDifferentFluidsNeverJoin() {
        PipeGrid grid = grid();
        Pump water = Fluids.fueledPump(grid, 0, 0, PumpTier.ADVANCED_FIRE, FluidType.FRESHWATER);
        Pump lava = Fluids.fueledPump(grid, 4, 0, PumpTier.ADVANCED_FIRE, FluidType.LAVA);
        Fluids.baseLine(grid, 1, 3, 0, MineralTier.COPPER);
        grid.placeValve(2, 1, Fluids.valve(1000));
        for (int i = 0; i < 60; i++) {
            Fluids.tickAll(grid, water, lava);
        }
        Check.isTrue(water.getNetwork() != lava.getNetwork(), "N12-2, N13-2");
        PipeNode middle = grid.getPipe(2, 0, PipeLayer.BASE);
        Check.equal(FluidType.FRESHWATER, middle.getFluid(), "the first pump to reach it (tick order, #15)");
        Check.equal(FluidType.LAVA, grid.getPipe(3, 0, PipeLayer.BASE).getFluid());
        Check.isTrue(grid.areLinked(middle, grid.getPipe(3, 0, PipeLayer.BASE)), "linked, but the face is a dead end");
        Check.equal(FluidType.FRESHWATER, ((TankValve) grid.getValve(2, 1)).getTank().getFluid(),
                "only water reached the valve");
    }

    public static void testRemovalSplitsTheNetworkAndLosesTheRemovedFluid() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 4, 0, MineralTier.COPPER);
        Fluids.fill(grid, 0, 4, 0, PipeLayer.BASE, FluidType.FRESHWATER);
        Check.equal(1, grid.getNetworks().size(), "reached and linked: one network");
        PipeNode removed = grid.removePipe(2, 0, PipeLayer.BASE);
        Check.isNull(removed.getNetwork(), "no longer in a network");
        PipeNetwork left = grid.getNetwork(0, 0, PipeLayer.BASE);
        PipeNetwork right = grid.getNetwork(4, 0, PipeLayer.BASE);
        Check.isTrue(left != right, "split (N18-3)");
        Check.equal(40L, left.getTotalAmount(), "each piece keeps its pipes' fluid");
        Check.equal(40L, right.getTotalAmount());
        // The removed pipe's 20 are gone: the game drops nothing (N12-1).
    }

    public static void testCuttingALinkSplitsAndRelinkingMerges() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 3, 0, MineralTier.COPPER);
        Fluids.fill(grid, 0, 3, 0, PipeLayer.BASE, FluidType.SLIME);
        grid.toggleSide(1, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.equal(2, grid.getNetworks().size(), "cut");
        grid.toggleSide(1, 0, PipeGrid.Part.BASIC_PIPE, Direction.EAST);
        Check.equal(1, grid.getNetworks().size(), "linked again");
    }

    public static void testNetworkUsesItsLowestTier() {
        PipeGrid grid = new PipeGrid(Fluids.TIERS);
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.GOLD);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.SPIDERITE);
        Fluids.fill(grid, 0, 2, 0, PipeLayer.BASE, FluidType.LAVA);
        Check.equal(MineralTier.IRON, grid.getNetwork(0, 0, PipeLayer.BASE).getLowestTier(), "N12-4");
    }

}
