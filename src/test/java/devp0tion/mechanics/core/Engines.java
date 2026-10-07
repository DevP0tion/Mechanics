package devp0tion.mechanics.core;

import java.util.List;
import java.util.Set;

/**
 * Which pipe engine the semantic tests build ({@link #create}): the engine before the ECS
 * restructure, or the new engine in its comparison-only compatibility mode (N26-1). The test runner
 * runs those test classes once per engine.
 */
final class Engines {

    enum Kind {
        LEGACY,
        /** The new engine with the old rules (N26-1): global nearest-first remainder, immediate filling. */
        NEW_COMPAT,
        /** The new engine with its own rules (N24-3, N25-3, N26-4). */
        NEW
    }

    static Kind current = Kind.LEGACY;

    private Engines() {
    }

    static EngineApi create(PipeTierRules rules) {
        return create(current, rules);
    }

    static EngineApi create(Kind kind, PipeTierRules rules) {
        switch (kind) {
            case LEGACY:
                return new LegacyPipeGrid(rules);
            case NEW_COMPAT: {
                PipeGrid grid = new PipeGrid(rules);
                grid.setCompatMode(true);
                return new NewEngine(grid);
            }
            default:
                return new NewEngine(new PipeGrid(rules));
        }
    }

    static boolean isLegacy() {
        return current == Kind.LEGACY;
    }

    /** The new engine behind an {@link EngineApi}, or {@code null} for the legacy one. */
    static PipeGrid newGrid(EngineApi api) {
        return api instanceof NewEngine ? ((NewEngine) api).grid : null;
    }

    /** {@link PipeGrid} behind the shared API. */
    static final class NewEngine implements EngineApi {
        final PipeGrid grid;

        NewEngine(PipeGrid grid) {
            this.grid = grid;
        }

        @Override
        public void setListener(PipeGrid.Listener listener) {
            grid.setListener(listener);
        }

        @Override
        public PipeTierRules getTierRules() {
            return grid.getTierRules();
        }

        @Override
        public void tick() {
            grid.tick();
        }

        @Override
        public long getTick() {
            return grid.getTick();
        }

        @Override
        public PipeNode getPipe(int x, int y, PipeLayer layer) {
            return grid.getPipe(x, y, layer);
        }

        @Override
        public TankValve getValve(int x, int y) {
            return grid.getValve(x, y);
        }

        @Override
        public Pump getPump(int x, int y) {
            return grid.getPump(x, y);
        }

        @Override
        public List<PipeNode> getPipes() {
            return grid.getPipes();
        }

        @Override
        public PipeNetwork getNetwork(int x, int y, PipeLayer layer) {
            return grid.getNetwork(x, y, layer);
        }

        @Override
        public List<PipeNetwork> getNetworks() {
            return grid.getNetworks();
        }

        @Override
        public int getFluidBlockedSides(int x, int y, PipeLayer layer) {
            return grid.getFluidBlockedSides(x, y, layer);
        }

        @Override
        public boolean areLinked(PipeNode a, PipeNode b) {
            return grid.areLinked(a, b);
        }

        @Override
        public List<TankValve> getLinkedValves(PipeNode node) {
            return grid.getLinkedValves(node);
        }

        @Override
        public List<PipeNode> getPumpEntries(Pump pump) {
            return grid.getPumpEntries(pump);
        }

        @Override
        public Set<FluidType> getOutputFluids(Pump pump) {
            return grid.getOutputFluids(pump);
        }

        @Override
        public List<TankValve> getSourceValves(Pump pump) {
            return grid.getSourceValves(pump);
        }

        @Override
        public boolean isPumpValveLinked(Pump pump, Direction direction) {
            return grid.isPumpValveLinked(pump, direction);
        }

        @Override
        public PipeGrid.Check checkPipePlacement(int x, int y, PipeLayer layer) {
            return grid.checkPipePlacement(x, y, layer);
        }

        @Override
        public PipeNode placePipe(int x, int y, PipeLayer layer, MineralTier tier) {
            return grid.placePipe(x, y, layer, tier);
        }

        @Override
        public PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                                 boolean loaded) {
            return grid.loadPipe(x, y, layer, tier, links, fluid, amount, loaded);
        }

        @Override
        public PipeNode unloadPipe(int x, int y, PipeLayer layer) {
            return grid.unloadPipe(x, y, layer);
        }

        @Override
        public PipeNode removePipe(int x, int y, PipeLayer layer) {
            return grid.removePipe(x, y, layer);
        }

        @Override
        public PipeGrid.Check checkValvePlacement(int x, int y) {
            return grid.checkValvePlacement(x, y);
        }

        @Override
        public void placeValve(int x, int y, TankValve valve) {
            grid.placeValve(x, y, valve);
        }

        @Override
        public void loadValve(int x, int y, TankValve valve) {
            grid.loadValve(x, y, valve);
        }

        @Override
        public TankValve removeValve(int x, int y) {
            return grid.removeValve(x, y);
        }

        @Override
        public TankValve unloadValve(int x, int y) {
            return grid.unloadValve(x, y);
        }

        @Override
        public PipeGrid.Check checkPumpPlacement(int x, int y, FluidType tileFluid) {
            return grid.checkPumpPlacement(x, y, tileFluid);
        }

        @Override
        public void placePump(int x, int y, Pump pump) {
            grid.placePump(x, y, pump);
        }

        @Override
        public void loadPump(int x, int y, Pump pump) {
            grid.loadPump(x, y, pump);
        }

        @Override
        public Pump removePump(int x, int y) {
            return grid.removePump(x, y);
        }

        @Override
        public PipeGrid.Check toggleSide(int x, int y, PipeGrid.Part part, Direction direction) {
            return grid.toggleSide(x, y, part, direction);
        }

        @Override
        public PipeGrid.Check toggleVertical(int x, int y) {
            return grid.toggleVertical(x, y);
        }

        @Override
        public int getPathDistance(Pump pump, TankValve valve, FluidType fluid) {
            return grid.getPathDistance(pump, valve, fluid);
        }
    }

}
