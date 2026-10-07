package devp0tion.mechanics.items;

import devp0tion.mechanics.wrench.WrenchMode;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;

/**
 * Client to server: the player switched the mode of the wrench in an inventory slot (N30-5). The
 * server sets it on its own copy of the item, which its click handling reads, and which is saved.
 * The client already changed its copy; when the slot holds no wrench on the server, the slot is
 * sent back so the client gets the server's state.
 */
public class PacketWrenchMode extends Packet {

    public final int inventoryID;
    public final int slot;
    public final WrenchMode mode;

    /** Received. */
    public PacketWrenchMode(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        inventoryID = reader.getNextShortUnsigned();
        slot = reader.getNextShortUnsigned();
        mode = WrenchMode.fromOrdinal(reader.getNextByteUnsigned());
    }

    public PacketWrenchMode(PlayerInventorySlot slot, WrenchMode mode) {
        this.inventoryID = slot.inventoryID;
        this.slot = slot.slot;
        this.mode = mode;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextShortUnsigned(inventoryID);
        writer.putNextShortUnsigned(this.slot);
        writer.putNextByteUnsigned(mode.ordinal());
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        if (!client.checkHasRequestedSelf() || client.playerMob == null) {
            return;
        }
        PlayerInventorySlot target = new PlayerInventorySlot(inventoryID, slot);
        InventoryItem item = target.getItem(client.playerMob.getInv());
        if (item == null || !(item.item instanceof MechanicsWrenchItem)) {
            target.markDirty(client.playerMob.getInv());
            return;
        }
        MechanicsWrenchItem.setMode(item, mode);
    }

}
