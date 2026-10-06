package devp0tion.mechanics.pipe;

import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.LiquidTileLookup;
import necesse.engine.registries.TileRegistry;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;

/**
 * The game adapter of {@link LiquidTileLookup}: liquid tiles of a level for a pump's liquid tile
 * source (N19-3).
 *
 * <ul>
 *     <li>The fluid of a tile: its vanilla liquid tile, seawater or freshwater by position (S10,
 *     {@code LiquidManager.isSaltWater}), and crude oil for seawater deeper than the bucket's limit
 *     ({@code LiquidManager.getHeight} below -3, the same check as the vanilla bucket's "deepsea",
 *     N17-5).</li>
 *     <li>A used-up tile becomes what a bucket leaves: dirt, marked as player placed, sent to the
 *     clients and the tiles and objects around it re-checked (vanilla {@code BucketItem.onPlace}).
 *     No item is given.</li>
 * </ul>
 * TODO(design): buckets cannot scoop the deep sea at all; a pump using up a deep seawater (crude
 * oil) tile turns it into dirt the same way, a placeholder until it is decided.
 */
public final class LevelLiquidTileLookup implements LiquidTileLookup {

    private final Level level;

    public LevelLiquidTileLookup(Level level) {
        this.level = level;
    }

    @Override
    public boolean isLoaded(int tileX, int tileY) {
        return level.isTileWithinBounds(tileX, tileY) && level.regionManager.isTileLoaded(tileX, tileY);
    }

    @Override
    public FluidType getFluid(int tileX, int tileY) {
        if (!isLoaded(tileX, tileY)) {
            return null;
        }
        return fluidAt(level, tileX, tileY);
    }

    /** The fluid a pump gets from a loaded tile, or {@code null} when it is not liquid. */
    public static FluidType fluidAt(Level level, int tileX, int tileY) {
        GameTile tile = level.getTile(tileX, tileY);
        if (tile == null || !tile.isLiquid) {
            return null;
        }
        return FluidType.fromPumpedTile(tile.getStringID(), level.liquidManager.isSaltWater(tileX, tileY),
                level.liquidManager.getHeight(tileX, tileY));
    }

    @Override
    public void consume(int tileX, int tileY) {
        if (!level.isServer() || !isLoaded(tileX, tileY)) {
            return;
        }
        level.setTile(tileX, tileY, TileRegistry.dirtID);
        level.tileLayer.setIsPlayerPlaced(tileX, tileY, true);
        level.sendTileUpdatePacket(tileX, tileY);
        level.getLevelTile(tileX, tileY).checkAround();
        level.getLevelObject(tileX, tileY).checkAround();
    }

}
