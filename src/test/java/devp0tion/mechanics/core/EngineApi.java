package devp0tion.mechanics.core;

import java.util.List;
import java.util.Set;

/**
 * The public API both pipe engines share, so the semantic tests run on the engine before the ECS
 * restructure ({@link LegacyPipeGrid}) and on the new one ({@link PipeGrid}, in its comparison-only
 * compatibility mode) alike (N22-6, N26-1). {@link Engines} picks the engine.
 */
interface EngineApi {

    void setListener(PipeGrid.Listener listener);

    PipeTierRules getTierRules();

    void tick();

    long getTick();

    PipeNode getPipe(int x, int y, PipeLayer layer);

    TankValve getValve(int x, int y);

    Pump getPump(int x, int y);

    List<PipeNode> getPipes();

    PipeNetwork getNetwork(int x, int y, PipeLayer layer);

    List<PipeNetwork> getNetworks();

    int getFluidBlockedSides(int x, int y, PipeLayer layer);

    boolean areLinked(PipeNode a, PipeNode b);

    List<TankValve> getLinkedValves(PipeNode node);

    List<PipeNode> getPumpEntries(Pump pump);

    Set<FluidType> getOutputFluids(Pump pump);

    List<TankValve> getSourceValves(Pump pump);

    boolean isPumpValveLinked(Pump pump, Direction direction);

    PipeGrid.Check checkPipePlacement(int x, int y, PipeLayer layer);

    PipeNode placePipe(int x, int y, PipeLayer layer, MineralTier tier);

    PipeNode loadPipe(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount,
                      boolean loaded);

    PipeNode unloadPipe(int x, int y, PipeLayer layer);

    PipeNode removePipe(int x, int y, PipeLayer layer);

    PipeGrid.Check checkValvePlacement(int x, int y);

    void placeValve(int x, int y, TankValve valve);

    void loadValve(int x, int y, TankValve valve);

    TankValve removeValve(int x, int y);

    TankValve unloadValve(int x, int y);

    PipeGrid.Check checkPumpPlacement(int x, int y, FluidType tileFluid);

    void placePump(int x, int y, Pump pump);

    void loadPump(int x, int y, Pump pump);

    Pump removePump(int x, int y);

    PipeGrid.Check toggleSide(int x, int y, PipeGrid.Part part, Direction direction);

    PipeGrid.Check toggleVertical(int x, int y);

    int getPathDistance(Pump pump, TankValve valve, FluidType fluid);

}
