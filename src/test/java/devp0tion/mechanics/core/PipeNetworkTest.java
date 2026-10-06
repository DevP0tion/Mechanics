package devp0tion.mechanics.core;

/** Network rules: lowest tier (9-10, N7-3), one fluid (12-7), splitting on removal (N7-1). */
final class PipeNetworkTest {

    private PipeNetworkTest() {
    }

    private static PipeGrid grid() {
        return new PipeGrid(Fluids.TIERS);
    }

    private static long totalInPipes(PipeGrid grid) {
        long total = 0;
        for (PipeNetwork network : grid.getNetworks()) {
            total += network.getTotalAmount();
        }
        return total;
    }

    // ---------- tiers ----------

    public static void testNetworkUsesTheLowestTier() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.GOLD);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.IRON);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.SPIDERITE);
        PipeNetwork network = grid.getNetwork(0, 0, PipeLayer.BASE);
        Check.equal(MineralTier.IRON, network.getLowestTier(), "9-10");
        Check.equal(20, network.getCellCapacity(), "iron's test transport amount (N7-3)");
        for (PipeNode node : network.getNodes()) {
            Check.equal(20, node.getCapacity(), "every cell: " + node);
        }
    }

    public static void testRemovingTheLowestTierRaisesTheCapacity() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.GOLD);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(10, grid.getNetwork(0, 0, PipeLayer.BASE).getCellCapacity());
        grid.removePipe(1, 0, PipeLayer.BASE);
        Check.equal(30, grid.getNetwork(0, 0, PipeLayer.BASE).getCellCapacity(), "gold alone");
    }

    public static void testLowerTierJoiningKeepsTheFluid() {
        PipeGrid grid = grid();
        PipeNode gold = grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.GOLD);
        gold.setContents(FluidType.LAVA, 30);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(10, gold.getCapacity(), "copper now limits the network");
        Check.equal(30, gold.getAmount(), "the excess is kept, not destroyed");
        Check.equal(0, gold.getSpaceFor(FluidType.LAVA), "over capacity takes nothing");
    }

    public static void testUndecidedTransportAmountsFail() {
        PipeGrid grid = new PipeGrid(PipeTierRules.UNDECIDED);
        Check.throwsException(UnsupportedOperationException.class,
                () -> grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER));
    }

    // ---------- one fluid per network ----------

    public static void testNetworkFluidFollowsItsPipes() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 2, 0, MineralTier.COPPER);
        PipeNetwork network = grid.getNetwork(0, 0, PipeLayer.BASE);
        Check.isNull(network.getFluid(), "empty network");
        Check.isTrue(network.canCarry(FluidType.SLIME), "empty carries anything");
        grid.getPipe(2, 0, PipeLayer.BASE).setContents(FluidType.SLIME, 4);
        Check.equal(FluidType.SLIME, network.getFluid());
        Check.isFalse(network.canCarry(FluidType.LAVA), "one fluid (12-7)");
        grid.getPipe(2, 0, PipeLayer.BASE).setContents(null, 0);
        Check.isNull(network.getFluid(), "empty again");
    }

    public static void testPlacingAPipeBetweenDifferentFluidsIsRefused() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.getPipe(0, 0, PipeLayer.BASE).setContents(FluidType.SEAWATER, 5);
        grid.getPipe(2, 0, PipeLayer.BASE).setContents(FluidType.FRESHWATER, 5);
        Check.equal(PipeGrid.Check.WOULD_MIX_FLUIDS, grid.checkPipePlacement(1, 0, PipeLayer.BASE), "12-2, 12-7");
        Check.throwsException(IllegalStateException.class,
                () -> grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER));
        Check.equal(2, grid.getNetworks().size(), "nothing changed");
    }

    public static void testJoiningAFluidAndAnEmptyNetwork() {
        PipeGrid grid = grid();
        grid.placePipe(0, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(2, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.getPipe(0, 0, PipeLayer.BASE).setContents(FluidType.LAVA, 5);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        Check.equal(1, grid.getNetworks().size());
        Check.equal(FluidType.LAVA, grid.getNetwork(2, 0, PipeLayer.BASE).getFluid());
    }

    // ---------- splitting ----------

    public static void testRemovalSplitsTheNetworkAndConservesFluid() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 4, 0, MineralTier.COPPER);
        int[] amounts = {10, 10, 7, 3, 0};
        for (int x = 0; x < amounts.length; x++) {
            if (amounts[x] > 0) {
                grid.getPipe(x, 0, PipeLayer.BASE).setContents(FluidType.FRESHWATER, amounts[x]);
            }
        }
        Check.equal(30L, totalInPipes(grid));
        PipeNode removed = grid.removePipe(2, 0, PipeLayer.BASE);
        Check.equal(7, removed.getAmount(), "the removed pipe keeps its own fluid for the caller");
        Check.equal(FluidType.FRESHWATER, removed.getFluid());
        Check.isNull(removed.getNetwork(), "no longer in a network");

        PipeNetwork left = grid.getNetwork(0, 0, PipeLayer.BASE);
        PipeNetwork right = grid.getNetwork(4, 0, PipeLayer.BASE);
        Check.isTrue(left != right, "split");
        Check.equal(2, grid.getNetworks().size());
        Check.equal(20L, left.getTotalAmount(), "left piece keeps its pipes' fluid");
        Check.equal(3L, right.getTotalAmount(), "right piece keeps its pipes' fluid");
        Check.equal(30L, totalInPipes(grid) + removed.getAmount(), "nothing created or destroyed");
        Check.equal(FluidType.FRESHWATER, right.getFluid());
    }

    public static void testEmptyPieceHasNoFluid() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 2, 0, MineralTier.COPPER);
        grid.getPipe(0, 0, PipeLayer.BASE).setContents(FluidType.LAVA, 10);
        grid.removePipe(1, 0, PipeLayer.BASE);
        Check.equal(FluidType.LAVA, grid.getNetwork(0, 0, PipeLayer.BASE).getFluid());
        Check.isNull(grid.getNetwork(2, 0, PipeLayer.BASE).getFluid(), "the empty piece takes any fluid again");
    }

    public static void testRemovingABranchPointMakesThreePieces() {
        PipeGrid grid = grid();
        Fluids.baseLine(grid, 0, 2, 1, MineralTier.COPPER);
        grid.placePipe(1, 0, PipeLayer.BASE, MineralTier.COPPER);
        grid.placePipe(1, 1, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        grid.placePipe(1, 2, PipeLayer.UNDERGROUND, MineralTier.COPPER);
        Check.equal(1, grid.getNetworks().size());
        grid.removePipe(1, 1, PipeLayer.BASE);
        Check.equal(4, grid.getNetworks().size(), "west, east, north, and the underground pair");
        Check.equal(2, grid.getNetwork(1, 2, PipeLayer.UNDERGROUND).size());
    }

    public static void testRemovingNothing() {
        Check.isNull(grid().removePipe(5, 5, PipeLayer.BASE), "no pipe");
    }

}
