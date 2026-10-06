package devp0tion.mechanics.core;

import java.util.Objects;

/**
 * The liquid tile under a pump (11-1 ①). Pumping does not use it up (11-2) and there is no depth
 * limit (11-9). The game maps the tile to a fluid with {@link FluidType#fromLiquidTile}.
 */
public final class LiquidTileSource implements FluidSource {

    private final FluidType fluid;

    public LiquidTileSource(FluidType fluid) {
        this.fluid = Objects.requireNonNull(fluid, "fluid");
    }

    @Override
    public FluidType getSourceFluid() {
        return fluid;
    }

    @Override
    public int getAvailable(FluidType type) {
        return type == fluid ? Integer.MAX_VALUE : 0;
    }

    @Override
    public int extract(FluidType type, int maxAmount) {
        return type == fluid ? LiquidStorage.requireNonNegative(maxAmount, "maxAmount") : 0;
    }

    /** Liquid tiles of the same fluid are the same source: infinite and stateless (11-2). */
    @Override
    public boolean equals(Object o) {
        return o instanceof LiquidTileSource && ((LiquidTileSource) o).fluid == fluid;
    }

    @Override
    public int hashCode() {
        return fluid.hashCode();
    }

    @Override
    public String toString() {
        return "LiquidTileSource[" + fluid + "]";
    }

}
