package devp0tion.mechanics.tank;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectPlaceOption;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

import java.awt.geom.Line2D;

/**
 * Engine hooks for the tank interior placement rule (N16-2, {@link TankInteriorPlacement}).
 *
 * <p>Every object item's placement goes through
 * {@code ObjectItem.canPlace(Level, ObjectPlaceOption, PlayerMob, Line2D, InventoryItem, GNDItemMap)}
 * (the vanilla subclasses that override it call it first): it decides the placement preview and
 * whether the item is placed, on both sides, and knows the object, its tile and its rotation. When it
 * finds nothing wrong, the patch adds the interior check.
 *
 * <p>Natural generation (N20-1) places objects without an item: grass growing on its tile
 * ({@code GrassTile.tick} and the other growing tiles), the world time simulation of a loading region
 * ({@code GrassTile.addSimulateGrow} and the like), spreading plants ({@code GrassSpreadOptions}),
 * snow piles and cobwebs. Each checks {@code GameObject.canPlace(Level, int, int, int, boolean)} with
 * {@code byPlayer = false} first; that check gets the interior check too.
 */
public final class TankInteriorPatches {

    private TankInteriorPatches() {
    }

    /** {@code ObjectItem.canPlace(level, placeOption, player, playerPositionLine, item, mapContent)}. */
    @ModMethodPatch(target = ObjectItem.class, name = "canPlace",
            arguments = {Level.class, ObjectPlaceOption.class, PlayerMob.class, Line2D.class, InventoryItem.class,
                    GNDItemMap.class})
    public static class ObjectItemCanPlace {

        @Advice.OnMethodExit
        static void onExit(@Advice.Argument(0) Level level, @Advice.Argument(1) ObjectPlaceOption placeOption,
                           @Advice.Return(readOnly = false) String error) {
            if (error == null && placeOption != null) {
                error = TankInteriorPlacement.checkObject(level, placeOption.object, placeOption.tileX,
                        placeOption.tileY, placeOption.rotation);
            }
        }

    }

    /**
     * {@code GameObject.canPlace(level, tileX, tileY, rotation, byPlayer)}: with {@code byPlayer}
     * false, an object the game places by itself (N20-1). Player placement is checked through the
     * object items ({@link ObjectItemCanPlace}).
     */
    @ModMethodPatch(target = GameObject.class, name = "canPlace",
            arguments = {Level.class, int.class, int.class, int.class, boolean.class})
    public static class NaturalCanPlace {

        @Advice.OnMethodExit
        static void onExit(@Advice.This GameObject object, @Advice.Argument(0) Level level,
                           @Advice.Argument(1) int tileX, @Advice.Argument(2) int tileY,
                           @Advice.Argument(3) int rotation, @Advice.Argument(4) boolean byPlayer,
                           @Advice.Return(readOnly = false) String error) {
            if (error == null && !byPlayer) {
                error = TankInteriorPlacement.checkNaturalObject(level, object, tileX, tileY, rotation);
            }
        }

    }

}
