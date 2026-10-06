package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.TankInteriorRule;
import devp0tion.mechanics.objects.GlassBlockObject;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.localization.Localization;
import necesse.engine.events.players.ItemPlaceEvent;
import necesse.inventory.item.Item;
import necesse.inventory.item.placeableItem.StonePlaceableItem;
import necesse.inventory.item.placeableItem.bucketItem.InfiniteWaterBucketItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.inventory.item.placeableItem.tileItem.GrassSeedItem;
import necesse.inventory.item.placeableItem.tileItem.LandfillItem;
import necesse.inventory.item.placeableItem.tileItem.TileItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.multiTile.MultiTile;

import java.util.Iterator;

/**
 * Placement rejection inside a recognized tank's interior (N16-2): only glass blocks, the tank
 * floor tile and underground pipes may be placed there ({@link TankInteriorRule}); everything else
 * is rejected. The interior of an inactive (invalid) tank is not covered. Both sides, so the client's
 * placement preview agrees with the server (clients know the recognized tanks from the controller
 * view, {@link TankRegistry#findTankWithInterior}).
 *
 * <ul>
 *     <li>Objects on any object layer (vanilla, carpets and other tile-layer objects, wall and table
 *     decorations, other mod objects, multi-tile objects on any of their tiles): every object item's
 *     placement check returns {@link #ERROR} ({@code TankInteriorPatches.ObjectItemCanPlace}), so the
 *     preview shows it and the item is not placed.</li>
 *     <li>Floor tiles, liquid tiles included, and the other tile-changing items (landfill, grass
 *     seeds, stone on gravel, the infinite water bucket): the vanilla preventable item place event
 *     ({@link ItemPlaceEvent}) is prevented. Liquid from a bucket is placed as its liquid tile item, a
 *     tile item.</li>
 * </ul>
 * TODO(design): wires and logic gates are placed on their own layers (not object layers, not floor
 * tiles) and are not rejected.
 */
public final class TankInteriorPlacement {

    /** The placement error for a rejected object (N16-2). */
    public static final String ERROR = "tankinterior";

    private TankInteriorPlacement() {
    }

    /** Registers the item place event listener (mod init). */
    public static void register() {
        GameEvents.addListener(ItemPlaceEvent.class, new GameEventListener<ItemPlaceEvent>() {
            @Override
            public void onEvent(ItemPlaceEvent event) {
                if (!event.isPrevented() && rejects(event)) {
                    event.preventDefault();
                }
            }
        });
    }

    /**
     * The item description line for mod items that cannot be placed inside a recognized tank
     * (N11-6, N16-2). Vanilla items get no description; they are only rejected.
     */
    public static String rejectedTooltip() {
        return Localization.translate("itemtooltip", "tankinteriortip");
    }

    /** What an object is for the interior rule (N16-2). */
    public static TankInteriorRule.Placement placementOf(GameObject object) {
        if (object instanceof GlassBlockObject) {
            return TankInteriorRule.Placement.GLASS_BLOCK;
        }
        // TODO(game): pipes do not exist yet (pipe round). Map basic pipe objects to BASIC_PIPE and
        // underground pipe objects to UNDERGROUND_PIPE here.
        return TankInteriorRule.Placement.OTHER;
    }

    /** What a floor tile is for the interior rule (N16-2). */
    public static TankInteriorRule.Placement placementOf(GameTile tile) {
        return LevelTankCellLookup.isTankFloor(tile) ? TankInteriorRule.Placement.TANK_FLOOR
                : TankInteriorRule.Placement.OTHER;
    }

    /** Whether the tile is inside a recognized (valid) tank's interior. */
    public static boolean isRecognizedInterior(Level level, int tileX, int tileY) {
        return TankRegistry.findTankWithInterior(level, tileX, tileY) != null;
    }

    /**
     * The placement error for the object {@code object} placed with its master tile at
     * ({@code tileX}, {@code tileY}) and the given rotation: {@link #ERROR} when any tile it covers
     * is inside a recognized tank and the object is not allowed there, else {@code null}.
     */
    public static String checkObject(Level level, GameObject object, int tileX, int tileY, int rotation) {
        if (level == null || object == null || TankRegistry.getControllers(level).isEmpty()) {
            return null;
        }
        MultiTile multiTile = object.getMultiTile(rotation);
        Iterator<MultiTile.CoordinateValue<GameObject>> tiles = multiTile.streamObjects(tileX, tileY).iterator();
        while (tiles.hasNext()) {
            MultiTile.CoordinateValue<GameObject> tile = tiles.next();
            if (!TankInteriorRule.isAllowed(placementOf(tile.value)) && isRecognizedInterior(level, tile.tileX, tile.tileY)) {
                return ERROR;
            }
        }
        return null;
    }

    /** Whether an item placement event puts something not allowed into a recognized tank. */
    private static boolean rejects(ItemPlaceEvent event) {
        Item item = event.item == null ? null : event.item.item;
        TankInteriorRule.Placement placement;
        if (item instanceof TileItem) {
            placement = placementOf(((TileItem) item).getTile());
        } else if (item instanceof LandfillItem || item instanceof GrassSeedItem
                || item instanceof StonePlaceableItem || item instanceof InfiniteWaterBucketItem) {
            placement = TankInteriorRule.Placement.OTHER;
        } else if (item instanceof ObjectItem) {
            // Checked with the rotation by the object items' placement check; this covers the master
            // tile again for object items that skip it.
            placement = placementOf(((ObjectItem) item).getObject());
        } else {
            return false;
        }
        return !TankInteriorRule.isAllowed(placement) && isRecognizedInterior(event.level, event.tileX, event.tileY);
    }

}
