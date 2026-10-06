package devp0tion.mechanics.core;

/**
 * Something a pump can pull fluid from (11-1): the liquid tile under it ({@link LiquidTileSource})
 * or the tank of a valve it is attached to ({@link TankStorage}).
 */
public interface FluidSource {

    /** The fluid this source would give now, or {@code null} if it has none. */
    FluidType getSourceFluid();

    /**
     * How much of {@code type} can be taken now ({@link Integer#MAX_VALUE} for an infinite source).
     * 0 for any other fluid.
     */
    int getAvailable(FluidType type);

    /**
     * Takes up to {@code maxAmount} of {@code type}.
     *
     * @return the amount taken
     */
    int extract(FluidType type, int maxAmount);

}
