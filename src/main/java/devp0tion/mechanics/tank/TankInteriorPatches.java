package devp0tion.mechanics.tank;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.ObjectPlaceOption;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

import java.awt.geom.Line2D;

/**
 * Engine hook for the tank interior placement rule (N16-2, {@link TankInteriorPlacement}).
 *
 * <p>Every object item's placement goes through
 * {@code ObjectItem.canPlace(Level, ObjectPlaceOption, PlayerMob, Line2D, InventoryItem, GNDItemMap)}
 * (the vanilla subclasses that override it call it first): it decides the placement preview and
 * whether the item is placed, on both sides, and knows the object, its tile and its rotation. When it
 * finds nothing wrong, the patch adds the interior check.
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

}
