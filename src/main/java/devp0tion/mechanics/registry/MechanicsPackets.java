package devp0tion.mechanics.registry;

import devp0tion.mechanics.pipe.PacketUndergroundPipes;
import necesse.engine.registries.PacketRegistry;

/**
 * Packet registration. The wrench and the pumps use the vanilla item, object and container packets;
 * the mod only adds the underground pipe state sync.
 */
public final class MechanicsPackets {

    private MechanicsPackets() {
    }

    public static void load() {
        PacketRegistry.registerPacket(PacketUndergroundPipes.class);
    }

}
