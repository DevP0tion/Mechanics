package devp0tion.mechanics.tank;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.DirtTile;
import necesse.level.gameTile.GameTile;
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
 * {@code byPlayer = false} first; that check gets the interior check too, against the tanks of the
 * loaded controllers ({@link TankInteriorPlacement#checkNaturalObject}). What grows while no
 * controller of a tank is loaded is broken when the controller judges its tank (N23-4).
 *
 * <p>Natural floor changes (N29-4): a grass tile spreads onto dirt only when its
 * {@code GameTile.canPlace(Level, int, int, boolean)} with {@code byPlayer = false} allows it, on a
 * dirt tile's tick ({@code DirtTile.tick}) and in the world time simulation
 * ({@code DirtTile.addSimulateLogic}); that check is refused inside a recognized tank
 * ({@link FloorSpreadCanPlace}). The dirt tile's tick also turns it to snow without a check: the
 * tick is skipped there ({@link DirtTileTick}).
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

    /**
     * {@code GameTile.canPlace(level, tileX, tileY, byPlayer)}: with {@code byPlayer} false, a floor
     * tile the game places by itself. For the tiles that spread onto dirt (grass tiles,
     * {@code spreadToDirtChance() > 0}) it is refused inside a recognized tank (N29-4).
     */
    @ModMethodPatch(target = GameTile.class, name = "canPlace",
            arguments = {Level.class, int.class, int.class, boolean.class})
    public static class FloorSpreadCanPlace {

        @Advice.OnMethodExit
        static void onExit(@Advice.This GameTile tile, @Advice.Argument(0) Level level,
                           @Advice.Argument(1) int tileX, @Advice.Argument(2) int tileY,
                           @Advice.Argument(3) boolean byPlayer, @Advice.Return(readOnly = false) String error) {
            if (error == null && !byPlayer && tile.spreadToDirtChance() > 0
                    && TankInteriorPlacement.blocksFloorSpread(level, tileX, tileY)) {
                error = TankInteriorPlacement.ERROR;
            }
        }

    }

    /**
     * {@code DirtTile.tick(level, x, y)}: a dirt tile turns to snow or takes grass from a neighbour.
     * Skipped inside a recognized tank (N29-4).
     */
    @ModMethodPatch(target = DirtTile.class, name = "tick", arguments = {Level.class, int.class, int.class})
    public static class DirtTileTick {

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean onEnter(@Advice.Argument(0) Level level, @Advice.Argument(1) int tileX,
                               @Advice.Argument(2) int tileY) {
            return level != null && level.isServer() && TankInteriorPlacement.blocksFloorSpread(level, tileX, tileY);
        }

    }

}
