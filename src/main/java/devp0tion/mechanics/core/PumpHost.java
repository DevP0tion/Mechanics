package devp0tion.mechanics.core;

import java.util.Set;

/**
 * What a {@link Pump} needs from the pipe engine it is placed in: its output cell, its linked
 * valves and the push. The engine is {@link PipeGrid}.
 */
interface PumpHost {

    /** The fluid in the pump's output cell, the basic pipe in front of it (N20-5, N36-1); empty without one or when it is empty. */
    Set<FluidType> getOutputFluids(Pump pump);

    /** Whether the pump and the valve on its side {@code direction} are linked as its source: only behind a valve pump (N36-3, N36-5, N36-20). */
    boolean isPumpValveLinked(Pump pump, Direction direction);

    TankValve getValve(int x, int y);

    /** The push of one cycle of {@code fluid} from the pump. */
    PushPlan planPush(Pump pump, FluidType fluid);

    /**
     * Why the pump has nowhere to push {@code fluid} (N36-56): what is in front of it, for a cycle
     * whose push can take nothing.
     */
    PumpResult.Detail destinationDetail(Pump pump, FluidType fluid);

    /** The pump is about to push {@code fluid} (its network may change, N17-3, N18-2). */
    void onPumpPushing(Pump pump, FluidType fluid);

}
