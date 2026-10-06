package devp0tion.mechanics.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The liquid tile under a pump (11-1 ①), with the N19-3 rules (11-2 partly replaced):
 * <ol>
 *     <li>When the 5x5 area centred on the pump ({@link #AREA_RADIUS}) is entirely one fluid, the
 *     source is infinite.</li>
 *     <li>Otherwise pumping uses up liquid tiles, 1 tile = 10 units (one bucket, N2-1): the
 *     farthest tile of the pump tile's fluid connected to the pump tile goes first, the pump's own
 *     tile last. Once the pump's tile is gone the pump has no source here.</li>
 * </ol>
 * The fluid of a tile comes from {@link LiquidTileLookup#getFluid} (deep seawater gives crude oil,
 * N17-5), so "the same liquid" means the same {@link FluidType}.
 *
 * <p>A used-up tile gives 10 units at once; what the pump does not take this cycle stays in this
 * source ({@link #getBuffered()}, saved by the game) and is used first next time.
 *
 * <p>While any tile of the 5x5 area is not loaded, the source gives nothing (the pump waits): a
 * region boundary must not make an infinite source look finite and eat tiles.
 * <p>"Connected" is 4-neighbour adjacency, "farthest" the number of steps from the pump tile; equal
 * distances go by larger tile y, then larger x (a deterministic order, not a design value).
 * <p>TODO(design): the search for connected tiles is limited to {@link #MAX_CONNECTED_TILES} loaded
 * tiles (the nearest ones), a technical bound so that a pump at the edge of a large body of liquid
 * does not search all of it; "the farthest" is the farthest of those.
 * <p>Within one pump cycle ({@link #beginCycle}) the area checks and the connected-tile search run
 * once and are reused; only {@link #extract} changes tiles in a cycle and it keeps them up to date,
 * with the same results as searching again (a technical optimization, the rules above unchanged).
 */
public final class LiquidTileSource implements FluidSource {

    /** The 5x5 area: two tiles around the pump (N19-3). */
    public static final int AREA_RADIUS = 2;

    /** TODO(design): technical bound of the connected-tile search (see the class comment). */
    public static final int MAX_CONNECTED_TILES = 1024;

    private final LiquidTileLookup lookup;
    private final int pumpX;
    private final int pumpY;
    private FluidType bufferedFluid;
    private int buffered;
    /** What one pump cycle has computed so far, or {@code null} outside a cycle. */
    private CycleMemo memo;

    /** Results reused within one pump cycle. */
    private static final class CycleMemo {
        Boolean areaLoaded;
        Boolean infinite;
        List<long[]> connected;
        /** The connected-tile search stopped at {@link #MAX_CONNECTED_TILES} with tiles left. */
        boolean truncated;
    }

    public LiquidTileSource(LiquidTileLookup lookup, int pumpX, int pumpY) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.pumpX = pumpX;
        this.pumpY = pumpY;
    }

    /** An infinite source of one fluid everywhere (a pump in open water; mostly for tests). */
    public static LiquidTileSource infinite(final FluidType fluid) {
        Objects.requireNonNull(fluid, "fluid");
        return new LiquidTileSource(new LiquidTileLookup() {
            @Override
            public boolean isLoaded(int tileX, int tileY) {
                return true;
            }

            @Override
            public FluidType getFluid(int tileX, int tileY) {
                return fluid;
            }

            @Override
            public void consume(int tileX, int tileY) {
            }
        }, 0, 0);
    }

    /** The fluid of the pump's own tile, or {@code null} when it is not a liquid tile (used up). */
    public FluidType getTileFluid() {
        return lookup.getFluid(pumpX, pumpY);
    }

    /** Units left over from used-up tiles. */
    public int getBuffered() {
        return buffered;
    }

    public FluidType getBufferedFluid() {
        return bufferedFluid;
    }

    /** Restores the left-over units when loading. */
    public void setBuffered(FluidType fluid, int amount) {
        LiquidStorage.requireNonNegative(amount, "amount");
        this.bufferedFluid = amount == 0 ? null : Objects.requireNonNull(fluid, "fluid");
        this.buffered = amount;
    }

    /**
     * Start of a pump cycle ({@link Pump}): until {@link #endCycle}, the area checks and the
     * connected-tile search are computed once and reused. Nothing but this source's
     * {@link #extract} may change the tiles in between.
     */
    void beginCycle() {
        memo = new CycleMemo();
    }

    /** End of the pump cycle: the next reads see the level as it is then. */
    void endCycle() {
        memo = null;
    }

    /** Whether every tile of the 5x5 area is loaded and holds the pump tile's fluid (N19-3 ①). */
    public boolean isInfinite() {
        if (memo != null && memo.infinite != null) {
            return memo.infinite;
        }
        boolean infinite = computeInfinite();
        if (memo != null) {
            memo.infinite = infinite;
        }
        return infinite;
    }

    private boolean computeInfinite() {
        FluidType center = lookup.getFluid(pumpX, pumpY);
        if (center == null) {
            return false;
        }
        for (int y = pumpY - AREA_RADIUS; y <= pumpY + AREA_RADIUS; y++) {
            for (int x = pumpX - AREA_RADIUS; x <= pumpX + AREA_RADIUS; x++) {
                if (!lookup.isLoaded(x, y) || lookup.getFluid(x, y) != center) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isAreaLoaded() {
        if (memo != null && memo.areaLoaded != null) {
            return memo.areaLoaded;
        }
        boolean loaded = computeAreaLoaded();
        if (memo != null) {
            memo.areaLoaded = loaded;
        }
        return loaded;
    }

    private boolean computeAreaLoaded() {
        for (int y = pumpY - AREA_RADIUS; y <= pumpY + AREA_RADIUS; y++) {
            for (int x = pumpX - AREA_RADIUS; x <= pumpX + AREA_RADIUS; x++) {
                if (!lookup.isLoaded(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public FluidType getSourceFluid() {
        if (buffered > 0) {
            return bufferedFluid;
        }
        return isAreaLoaded() ? lookup.getFluid(pumpX, pumpY) : null;
    }

    @Override
    public int getAvailable(FluidType type) {
        if (type == null) {
            return 0;
        }
        if (buffered > 0) {
            // The buffer is used first; tiles of another fluid wait until it is empty.
            return type == bufferedFluid ? buffered + tilesAvailable(type) : 0;
        }
        return tilesAvailable(type);
    }

    private int tilesAvailable(FluidType type) {
        if (!isAreaLoaded() || lookup.getFluid(pumpX, pumpY) != type) {
            return 0;
        }
        if (isInfinite()) {
            return Integer.MAX_VALUE;
        }
        long units = (long) connectedTiles().size() * FluidUnits.BUCKET;
        return (int) Math.min(Integer.MAX_VALUE - (long) buffered, units);
    }

    @Override
    public int extract(FluidType type, int maxAmount) {
        LiquidStorage.requireNonNegative(maxAmount, "maxAmount");
        if (type == null || maxAmount == 0) {
            return 0;
        }
        int taken = 0;
        if (buffered > 0) {
            if (type != bufferedFluid) {
                return 0;
            }
            taken = Math.min(buffered, maxAmount);
            setBuffered(bufferedFluid, buffered - taken);
        }
        if (taken == maxAmount || !isAreaLoaded() || lookup.getFluid(pumpX, pumpY) != type) {
            return taken;
        }
        if (isInfinite()) {
            return maxAmount;
        }
        while (taken < maxAmount) {
            long[] farthest = farthestTile(type);
            if (farthest == null) {
                break;
            }
            lookup.consume((int) farthest[0], (int) farthest[1]);
            onConsumed(farthest);
            int use = Math.min(FluidUnits.BUCKET, maxAmount - taken);
            taken += use;
            if (use < FluidUnits.BUCKET) {
                setBuffered(type, FluidUnits.BUCKET - use);
            }
        }
        return taken;
    }

    /** The farthest connected tile of {@code type} (the pump tile last), or {@code null}. */
    private long[] farthestTile(FluidType type) {
        if (lookup.getFluid(pumpX, pumpY) != type) {
            return null;
        }
        long[] best = null;
        int bestDistance = -1;
        for (long[] tile : connectedTiles()) {
            int distance = (int) tile[2];
            if (best == null || distance > bestDistance
                    || distance == bestDistance && (tile[1] > best[1] || tile[1] == best[1] && tile[0] > best[0])) {
                best = tile;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The farthest tile was used up within a cycle. The area stays loaded and not infinite. When the
     * search had found every connected tile, the others keep their steps (each is reached through
     * nearer tiles only), so dropping the tile gives what a new search would; when the search was cut
     * off at {@link #MAX_CONNECTED_TILES}, a new search may reach further, so it runs again.
     */
    private void onConsumed(long[] tile) {
        if (memo == null || memo.connected == null) {
            return;
        }
        if (memo.truncated) {
            memo.connected = null;
        } else {
            memo.connected.remove(tile);
        }
    }

    /** Loaded tiles of the pump tile's fluid connected to it: {x, y, steps}, nearest first. */
    private List<long[]> connectedTiles() {
        if (memo != null && memo.connected != null) {
            return memo.connected;
        }
        List<long[]> result = new ArrayList<>();
        FluidType fluid = lookup.getFluid(pumpX, pumpY);
        if (fluid == null) {
            return result;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        queue.add(new long[]{pumpX, pumpY, 0});
        seen.add(PipeGrid.key(pumpX, pumpY));
        while (!queue.isEmpty() && result.size() < MAX_CONNECTED_TILES) {
            long[] tile = queue.poll();
            result.add(tile);
            for (Direction d : Direction.values()) {
                int x = (int) tile[0] + d.dx;
                int y = (int) tile[1] + d.dy;
                if (seen.add(PipeGrid.key(x, y)) && lookup.isLoaded(x, y) && lookup.getFluid(x, y) == fluid) {
                    queue.add(new long[]{x, y, tile[2] + 1});
                }
            }
        }
        if (memo != null) {
            memo.connected = result;
            memo.truncated = !queue.isEmpty();
        }
        return result;
    }

    @Override
    public String toString() {
        return "LiquidTileSource[" + pumpX + "," + pumpY + (buffered > 0 ? ", buffered " + bufferedFluid + " " + buffered : "")
                + "]";
    }

}
