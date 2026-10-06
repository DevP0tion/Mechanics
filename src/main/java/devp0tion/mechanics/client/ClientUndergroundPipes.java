package devp0tion.mechanics.client;

import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.pipe.PacketUndergroundPipes;
import necesse.level.maps.Level;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a client knows about the underground pipes of its level: their link flags and their faces
 * blocked by another fluid (N13-2), from {@link PacketUndergroundPipes}. Read by the drawing threads,
 * so the maps are concurrent. Unknown tiles read as {@link LinkFlags#ALL_OPEN} and not blocked until
 * the server's state arrives.
 */
public final class ClientUndergroundPipes {

    /** A tile's value: the link flags in the low bits, the blocked faces shifted by this. */
    private static final int BLOCKED_SHIFT = 8;

    private static final Map<Level, Map<Long, Integer>> LEVELS =
            Collections.synchronizedMap(new WeakHashMap<Level, Map<Long, Integer>>());

    private ClientUndergroundPipes() {
    }

    private static Map<Long, Integer> of(Level level, boolean create) {
        synchronized (LEVELS) {
            Map<Long, Integer> map = LEVELS.get(level);
            if (map == null && create) {
                map = new ConcurrentHashMap<>();
                LEVELS.put(level, map);
            }
            return map;
        }
    }

    /** Applies a received packet. */
    public static void apply(Level level, PacketUndergroundPipes packet) {
        Map<Long, Integer> map = of(level, true);
        if (packet.regionSnapshot) {
            map.keySet().removeIf(key -> level.regionManager.getRegionCoordByTile(PipeGrid.keyX(key)) == packet.regionX
                    && level.regionManager.getRegionCoordByTile(PipeGrid.keyY(key)) == packet.regionY);
        }
        for (long[] entry : packet.entries) {
            long key = PipeGrid.key((int) entry[0], (int) entry[1]);
            if (entry[2] < 0) {
                map.remove(key);
            } else {
                map.put(key, LinkFlags.sanitize((int) entry[2]) | (LinkFlags.sanitize((int) entry[3]) << BLOCKED_SHIFT));
            }
        }
    }

    /** The link flags of the underground pipe at the tile as last sent by the server. */
    public static int getLinks(Level level, int tileX, int tileY) {
        Integer value = valueAt(level, tileX, tileY);
        return value == null ? LinkFlags.ALL_OPEN : value & LinkFlags.ALL_OPEN;
    }

    /** The faces of the underground pipe at the tile blocked by another fluid, as last sent by the server. */
    public static int getBlockedSides(Level level, int tileX, int tileY) {
        Integer value = valueAt(level, tileX, tileY);
        return value == null ? 0 : (value >> BLOCKED_SHIFT) & LinkFlags.ALL_OPEN;
    }

    private static Integer valueAt(Level level, int tileX, int tileY) {
        Map<Long, Integer> map = of(level, false);
        return map == null ? null : map.get(PipeGrid.key(tileX, tileY));
    }

}
