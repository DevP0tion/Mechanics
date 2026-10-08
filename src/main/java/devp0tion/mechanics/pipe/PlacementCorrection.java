package devp0tion.mechanics.pipe;

import devp0tion.mechanics.objects.PumpObject;
import devp0tion.mechanics.objects.TankControllerObject;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.packet.PacketChangeObjects;
import necesse.engine.network.packet.PacketChangeTile;
import necesse.engine.network.packet.PacketObjectEntity;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.inventory.PlayerInventorySlot;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectPlaceOption;
import necesse.level.maps.Level;
import necesse.level.maps.multiTile.MultiTile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Corrects a client after the server refused something the client already did (D1).
 *
 * <p>The vanilla placement flow lets the client place an object (and use up the item) as soon as
 * its own placement check passes; when the server's check then fails, nothing tells the client
 * (the attack handler just skips the place), so the client keeps a ghost object and a wrong item
 * count. Whenever one of this mod's rules makes the server refuse (the tank rules, the pump's
 * sources, the wrench), the client is sent the real state of the cells: the floor tile, the
 * objects of every layer, the object entity (link flags, tank views), the underground pipe state,
 * and the item slot is marked dirty so the server resends it.
 */
public final class PlacementCorrection {

    /** The canPlace errors of this mod's rules. */
    private static final Set<String> OWN_ERRORS = new HashSet<>(Arrays.asList(
            TankInteriorPlacement.ERROR,
            TankControllerObject.SHARED_WALL_ERROR,
            PumpObject.MIXED_SOURCES_ERROR));

    private PlacementCorrection() {
    }

    /** Whether a canPlace error comes from this mod's rules. */
    public static boolean isOwnError(String error) {
        return error != null && OWN_ERRORS.contains(error);
    }

    /**
     * An object item's placement failed on the server ({@code ObjectItem.onAttemptPlace}): when the
     * reason is one of this mod's rules, every cell the object would cover is corrected.
     */
    public static void onObjectPlaceRefused(Level level, PlayerMob player, GNDItemMap mapContent, String error) {
        if (level == null || !level.isServer() || player == null || !isOwnError(error) || mapContent == null
                || !mapContent.getBoolean("hasPlaceOption")) {
            return;
        }
        ObjectPlaceOption option = new ObjectPlaceOption("placeOption", mapContent);
        GameObject object = option.object;
        if (object == null) {
            correct(level, player, option.tileX, option.tileY);
            return;
        }
        MultiTile multiTile = object.getMultiTile(option.rotation);
        Iterator<MultiTile.CoordinateValue<GameObject>> tiles = multiTile.streamObjects(option.tileX, option.tileY).iterator();
        while (tiles.hasNext()) {
            MultiTile.CoordinateValue<GameObject> tile = tiles.next();
            sendCell(level, player, tile.tileX, tile.tileY);
        }
        markSlotDirty(player);
    }

    /** Sends the real state of one cell to the player's client and marks its item slot dirty. */
    public static void correct(Level level, PlayerMob player, int tileX, int tileY) {
        if (level == null || !level.isServer() || player == null) {
            return;
        }
        sendCell(level, player, tileX, tileY);
        markSlotDirty(player);
    }

    private static void sendCell(Level level, PlayerMob player, int tileX, int tileY) {
        if (!player.isServerClient() || !level.isTileWithinBounds(tileX, tileY)) {
            return;
        }
        ServerClient client = player.getServerClient();
        client.sendPacket(new PacketChangeTile(level, tileX, tileY));
        client.sendPacket(new PacketChangeObjects(level, tileX, tileY));
        ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
        if (entity != null) {
            client.sendPacket(new PacketObjectEntity(entity));
        }
        PipeSystem system = PipeSystem.getIfExists(level);
        if (system != null) {
            client.sendPacket(system.undergroundTilePacket(tileX, tileY));
        }
    }

    private static void markSlotDirty(PlayerMob player) {
        if (!player.isServerClient()) {
            return;
        }
        PlayerInventorySlot slot = player.attackSlot != null ? player.attackSlot : player.getSelectedItemSlot();
        if (slot != null) {
            slot.getInv(player.getInv()).markDirty(slot.slot);
        }
    }

}
