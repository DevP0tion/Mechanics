package devp0tion.mechanics.registry;

import devp0tion.mechanics.items.PacketWrenchMode;
import devp0tion.mechanics.items.PacketWrenchPreview;
import devp0tion.mechanics.items.PacketWrenchPreviewRequest;
import devp0tion.mechanics.pipe.PacketUndergroundPipes;
import necesse.engine.registries.PacketRegistry;

/**
 * Packet registration. The wrench's clicks and the pumps use the vanilla item, object and container
 * packets; the mod adds the underground pipe state sync, the wrench's mode switch (N30-5) and the
 * wrench tooltip's question and answer (N30-1, N30-2).
 */
public final class MechanicsPackets {

    private MechanicsPackets() {
    }

    public static void load() {
        PacketRegistry.registerPacket(PacketUndergroundPipes.class);
        PacketRegistry.registerPacket(PacketWrenchMode.class);
        PacketRegistry.registerPacket(PacketWrenchPreviewRequest.class);
        PacketRegistry.registerPacket(PacketWrenchPreview.class);
    }

}
