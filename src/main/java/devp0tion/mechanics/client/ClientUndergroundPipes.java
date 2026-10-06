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
 * What a client knows about the underground pipes of its level: their link flags, from
 * {@link PacketUndergroundPipes}. Read by the drawing threads, so the maps are concurrent.
 * Unknown tiles read as {@link LinkFlags#ALL_OPEN} until the server's state arrives.
 */
public final class ClientUndergroundPipes {

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
                map.put(key, LinkFlags.sanitize((int) entry[2]));
            }
        }
    }

    /** The link flags of the underground pipe at the tile as last sent by the server. */
    public static int getLinks(Level level, int tileX, int tileY) {
        Map<Long, Integer> map = of(level, false);
        Integer links = map == null ? null : map.get(PipeGrid.key(tileX, tileY));
        return links == null ? LinkFlags.ALL_OPEN : links;
    }

}
