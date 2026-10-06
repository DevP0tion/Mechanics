package devp0tion.mechanics.tank;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.regionSystem.layers.ObjectRegionLayer;
import necesse.level.maps.regionSystem.layers.TileRegionLayer;
import net.bytebuddy.asm.Advice;

/**
 * Engine hooks for automatic tank recognition (5-1): every object change and every floor tile
 * change on a level is reported to {@link TankRegistry#onTileChanged}.
 *
 * <p>The game has no event for object or tile changes, but every change of a loaded tile goes
 * through the region layer setters patched here (placing, breaking, replacing, world edits, packets
 * on clients). Both run on the server and on clients; only server levels react.
 *
 * <p>This is the detection mechanism (N16-1). Placements inside a recognized tank's interior are
 * rejected separately ({@link TankInteriorPlacement}, N16-2); every change that does happen near a
 * tank is still reported here.
 */
public final class TankChangePatches {

    private TankChangePatches() {
    }

    /** {@code ObjectRegionLayer.setObjectByRegion(layerID, regionTileX, regionTileY, objectID, forceDontUpdate)}. */
    @ModMethodPatch(target = ObjectRegionLayer.class, name = "setObjectByRegion",
            arguments = {int.class, int.class, int.class, int.class, boolean.class})
    public static class ObjectChanged {

        @Advice.OnMethodExit
        static void onExit(@Advice.This ObjectRegionLayer layer,
                           @Advice.Argument(1) int regionTileX, @Advice.Argument(2) int regionTileY) {
            TankRegistry.onTileChanged(layer.level, regionTileX + layer.region.tileXOffset,
                    regionTileY + layer.region.tileYOffset);
        }

    }

    /** {@code TileRegionLayer.setTileByRegion(regionTileX, regionTileY, tileID, forceDontUpdate)}. */
    @ModMethodPatch(target = TileRegionLayer.class, name = "setTileByRegion",
            arguments = {int.class, int.class, int.class, boolean.class})
    public static class FloorChanged {

        @Advice.OnMethodExit
        static void onExit(@Advice.This TileRegionLayer layer,
                           @Advice.Argument(0) int regionTileX, @Advice.Argument(1) int regionTileY) {
            TankRegistry.onTileChanged(layer.level, regionTileX + layer.region.tileXOffset,
                    regionTileY + layer.region.tileYOffset);
        }

    }

}
