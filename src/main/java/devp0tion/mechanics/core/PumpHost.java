package devp0tion.mechanics.core;

import java.util.Set;

/**
 * What a {@link Pump} needs from the pipe engine it is placed in: its output cells, its linked
 * valves and the push. The engine is {@link PipeGrid}.
 */
interface PumpHost {

    /** The fluids in the pump's output cells (N20-5), empty pipes left out. */
    Set<FluidType> getOutputFluids(Pump pump);

    /** Whether the pump and the valve on its side {@code direction} are linked (N16-3). */
    boolean isPumpValveLinked(Pump pump, Direction direction);

    TankValve getValve(int x, int y);

    /** The push of one cycle of {@code fluid} from the pump. */
    PushPlan planPush(Pump pump, FluidType fluid);

    /** The pump is about to push {@code fluid} (its network may change, N17-3, N18-2). */
    void onPumpPushing(Pump pump, FluidType fluid);

}
