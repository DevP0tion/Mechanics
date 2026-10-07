package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.TankInteriorRule;
import devp0tion.mechanics.objects.BasicPipeObject;
import devp0tion.mechanics.objects.GlassBlockObject;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import devp0tion.mechanics.pipe.PlacementCorrection;
import necesse.engine.GameEventListener;
import necesse.engine.GameEvents;
import necesse.engine.localization.Localization;
import necesse.engine.events.players.ItemPlaceEvent;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.registries.ObjectLayerRegistry;
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
 * A placement the server refuses here but the client already made (its view of the recognized
 * tanks can lag) is corrected on that client ({@link PlacementCorrection}): objects through the
 * refused object placement hook, floor tiles right here.
 * Wires and logic gates are placed on their own layers (not object layers, not floor tiles) and are
 * allowed inside a recognized tank (N29-7).
 *
 * <p>Natural generation (N20-1, the same scope): objects the game places by itself are not placed
 * inside a recognized tank either ({@link #checkNaturalObject}, {@code TankInteriorPatches.NaturalCanPlace}):
 * grass and the other plants growing on their tiles, also in the world time simulation when a region
 * loads, plants spreading (reeds, flowers and the like), snow piles and cobwebs. They all check
 * {@code GameObject.canPlace(level, x, y, rotation, byPlayer = false)} before placing. The tanks are
 * those of the loaded controllers, as each judged it last (a loaded controller starts with its saved
 * judgment, N22-7); there is no index of tank ranges (N23-4). What grows while no controller of the
 * tank is loaded, for example in a region that loads before the controller's, is broken when the
 * controller judges its active tank ({@link #breakNaturalGrowth}, N23-4, N26-3, N29-5).
 *
 * <p>Natural floor tile changes (N29-4, the same scope as N20-1): grass tiles spreading onto dirt
 * and dirt turning to snow do not happen inside a recognized tank's interior
 * ({@link #blocksFloorSpread}, {@code TankInteriorPatches.FloorSpreadCanPlace} for the grass
 * tiles' spread check, {@code TankInteriorPatches.DirtTileTick} for the dirt tile's tick, which
 * also turns it to snow; the world time simulation of a loading region uses the same spread check).
 * TODO(confirm): floor tiles that already spread there are left as they are, not reverted.
 *
 * <p>While a tank is dormant (its controller not loaded, N21-2), nothing is rejected or blocked in the
 * loaded part of its interior, placements by players included; a player's object there makes the
 * tank invalid at its next judgment (N29-3).
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
                    // The client may have placed it already (its tank view can lag): correct it (D1).
                    PlacementCorrection.correct(event.level, event.player, event.tileX, event.tileY);
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
        if (object instanceof UndergroundPipeObject) {
            return TankInteriorRule.Placement.UNDERGROUND_PIPE;
        }
        if (object instanceof BasicPipeObject) {
            return TankInteriorRule.Placement.BASIC_PIPE;
        }
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

    /**
     * {@link #checkObject} for an object the game places by itself (natural generation, N20-1): the
     * same check, against the tanks of the loaded controllers as each judged it last. A controller
     * loaded with its region starts with its saved judgment (N22-7), so the world time simulation of
     * that region already sees its tank.
     */
    public static String checkNaturalObject(Level level, GameObject object, int tileX, int tileY, int rotation) {
        return checkObject(level, object, tileX, tileY, rotation);
    }

    /**
     * Whether the base layer object on the tile is what the judgment of an active tank breaks
     * (N20-1, N23-4, N29-5): every object of the grass kind (grass and the other plants that grow and
     * spread on their own, reeds, flowers, cobwebs, snow piles; the game's own grass flag), placed by
     * a player or not. So flowers spreading from planted flower patches and plants a player placed
     * while the tank was dormant are broken too. Objects of other mods that grow by themselves
     * without the grass flag are not covered: they still make the tank invalid.
     */
    public static boolean isNaturalGrowth(Level level, int tileX, int tileY) {
        GameObject object = level.getObject(ObjectLayerRegistry.BASE_LAYER, tileX, tileY);
        return object != null && object.getID() != 0 && object.isGrass;
    }

    /**
     * N29-4: whether a natural floor change (a grass tile spreading onto dirt, dirt turning to snow)
     * is blocked on the tile: it is inside a recognized tank's interior, the tanks of the loaded
     * controllers as each judged it last (the scope of N20-1). Not while a tank is dormant (N29-3).
     */
    public static boolean blocksFloorSpread(Level level, int tileX, int tileY) {
        return level != null && !TankRegistry.getControllers(level).isEmpty() && isRecognizedInterior(level, tileX, tileY);
    }

    /**
     * Breaks the natural growth on the tile (N23-4) without drops (N26-3): the base layer is cleared
     * and the clients that have the tile are told. Server only. The change is reported like any
     * other ({@link TankChangePatches}).
     */
    static void breakNaturalGrowth(Level level, int tileX, int tileY) {
        if (level == null || !level.isServer() || !isNaturalGrowth(level, tileX, tileY)) {
            return;
        }
        int layer = ObjectLayerRegistry.BASE_LAYER;
        level.objectLayer.setObject(layer, tileX, tileY, 0);
        level.objectLayer.setObjectRotation(layer, tileX, tileY, 0);
        level.objectLayer.setIsPlayerPlaced(layer, tileX, tileY, false);
        if (level.getServer() != null) {
            level.getServer().network.sendToClientsWithTile(new PacketChangeObject(level, layer, tileX, tileY, 0, 0),
                    level, tileX, tileY);
        }
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
