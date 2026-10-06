package devp0tion.mechanics.pipe;

import devp0tion.mechanics.client.ClientUndergroundPipes;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server to client: the link flags of underground pipes and their faces blocked by another fluid,
 * the only underground pipe state clients draw (connections and cut faces, N16-4, N13-2). The pipes
 * themselves (existence and tier) are layer objects and come with the vanilla region data and object
 * packets; fluid amounts stay on the server.
 *
 * <ul>
 *     <li>A region snapshot replaces everything the client knows about that region; it is sent when
 *     the client gets the region ({@link PipeSyncPatches}).</li>
 *     <li>A tile update sets one tile (flags -1: no underground pipe state there).</li>
 *     <li>Each entry: tile x, y and the flags byte; when a face is blocked by another fluid, bit
 *     {@link #BLOCKED_FOLLOWS} of the flags byte is set and a byte with those faces follows
 *     ({@link devp0tion.mechanics.core.LinkFlags} bits), so the common entry without any stays as
 *     small as before.</li>
 * </ul>
 */
public class PacketUndergroundPipes extends Packet {

    /** Bit of an entry's flags byte: a byte with the faces blocked by another fluid follows. */
    static final int BLOCKED_FOLLOWS = 0x20;

    public final int levelIdentifierHashCode;
    public final boolean regionSnapshot;
    public final int regionX;
    public final int regionY;
    /** {tileX, tileY, flags, blocked faces} entries. */
    public final List<long[]> entries;

    /** Received. */
    public PacketUndergroundPipes(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        levelIdentifierHashCode = reader.getNextInt();
        regionSnapshot = reader.getNextBoolean();
        regionX = reader.getNextInt();
        regionY = reader.getNextInt();
        int count = reader.getNextInt();
        List<long[]> read = new ArrayList<>(Math.max(0, Math.min(count, 4096)));
        for (int i = 0; i < count; i++) {
            int x = reader.getNextInt();
            int y = reader.getNextInt();
            int flags = reader.getNextByte();
            int blocked = flags >= 0 && (flags & BLOCKED_FOLLOWS) != 0 ? reader.getNextByteUnsigned() : 0;
            read.add(new long[]{x, y, flags < 0 ? -1 : flags & ~BLOCKED_FOLLOWS, blocked});
        }
        entries = read;
    }

    private PacketUndergroundPipes(Level level, boolean regionSnapshot, int regionX, int regionY, List<long[]> entries) {
        this.levelIdentifierHashCode = level.getIdentifierHashCode();
        this.regionSnapshot = regionSnapshot;
        this.regionX = regionX;
        this.regionY = regionY;
        this.entries = entries;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(levelIdentifierHashCode);
        writer.putNextBoolean(regionSnapshot);
        writer.putNextInt(regionX);
        writer.putNextInt(regionY);
        writer.putNextInt(entries.size());
        for (long[] entry : entries) {
            writer.putNextInt((int) entry[0]);
            writer.putNextInt((int) entry[1]);
            int blocked = entry[2] < 0 ? 0 : (int) entry[3];
            writer.putNextByte((byte) (blocked == 0 ? entry[2] : entry[2] | BLOCKED_FOLLOWS));
            if (blocked != 0) {
                writer.putNextByteUnsigned(blocked);
            }
        }
    }

    /** The state of every underground pipe of a region: {tileX, tileY, flags, blocked faces} entries. */
    public static PacketUndergroundPipes region(Level level, int regionX, int regionY, List<long[]> entries) {
        return new PacketUndergroundPipes(level, true, regionX, regionY, entries);
    }

    /** One tile; {@code flags} -1 when there is no underground pipe. */
    public static PacketUndergroundPipes tile(Level level, int tileX, int tileY, int flags, int blocked) {
        return new PacketUndergroundPipes(level, false, 0, 0,
                Collections.singletonList(new long[]{tileX, tileY, flags, blocked}));
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Level level = client.getLevel();
        if (level == null || level.getIdentifierHashCode() != levelIdentifierHashCode) {
            return;
        }
        ClientUndergroundPipes.apply(level, this);
    }

}
