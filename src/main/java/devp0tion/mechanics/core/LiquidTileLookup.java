package devp0tion.mechanics.core;

/**
 * Reads and consumes liquid tiles for a pump's liquid tile source ({@link LiquidTileSource}). The
 * game adapter implements it over a level; tests use a grid.
 */
public interface LiquidTileLookup {

    /**
     * Whether the tile is loaded (readable and changeable now). A tile that can never be loaded
     * (outside a finite level) counts as loaded and holds no liquid.
     */
    boolean isLoaded(int tileX, int tileY);

    /**
     * The fluid a pump gets from the tile ({@link FluidType#fromPumpedTile}: deep seawater gives
     * crude oil, N17-5), or {@code null} when it is not a liquid tile or not loaded.
     */
    FluidType getFluid(int tileX, int tileY);

    /** Takes the liquid of the tile away: the tile becomes what a bucket leaves (N19-3). */
    void consume(int tileX, int tileY);

}
