package devp0tion.mechanics.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The liquid tile under a pump (11-1 ①), with the N19-3 rules (11-2 partly replaced) and the N20-7
 * judgment:
 * <ol>
 *     <li>When the 5x5 area centred on the pump ({@link #AREA_RADIUS}) is entirely one fluid, the
 *     source is infinite.</li>
 *     <li>Otherwise pumping uses up liquid tiles, 1 tile = 10 units (one bucket, N2-1): the
 *     farthest tile of the pump tile's fluid connected to the pump tile goes first, the pump's own
 *     tile last. Once the pump's tile is gone the pump has no source here.</li>
 * </ol>
 * The fluid of a tile comes from {@link LiquidTileLookup#getFluid} (deep seawater gives crude oil,
 * N17-5), so "the same liquid" means the same {@link FluidType}.
 * <p>Deep seawater never runs out (N27-5): a pump whose tile gives crude oil is an inexhaustible
 * source whatever the 5x5 judgment says, and never uses up a deep tile. Shallow seawater and the
 * other liquids follow the rules above (their tiles are never crude oil, so a finite source of them
 * never reaches a deep tile either).
 *
 * <h2>The 5x5 judgment (N20-7)</h2>
 * <ul>
 *     <li>The 5x5 check uses loaded cells only: the source is judged infinite when the pump's own
 *     tile is liquid and every loaded cell of the area holds its fluid. Cells that are not loaded
 *     are left out.</li>
 *     <li>It is judged when the pump is placed ({@link #judgeArea}, the first time the pump
 *     registers) and kept ({@link #getJudgment}, saved with the pump): the pump keeps working on it
 *     while cells of the area are unloaded.</li>
 *     <li>The loaded cells are watched for changes, once per pump cycle: a cell whose fluid changed
 *     since it was last seen loaded makes the area judged again, the same way. A cell that unloads is
 *     no longer watched; when it loads again it is watched from then on.</li>
 *     <li>A source without a judgment (restored from a pump saved before the judgment was stored)
 *     is judged at its first use, or when the game calls {@link #judgeArea}, with the cells loaded
 *     then.</li>
 * </ul>
 * The connected-tile search of a finite source also uses loaded tiles only (N20-7).
 *
 * <p>A used-up tile gives 10 units at once; what the pump does not take this cycle stays in this
 * source ({@link #getBuffered()}, saved by the game) and is used first next time.
 * <p>"Connected" is 4-neighbour adjacency, "farthest" the number of steps from the pump tile; equal
 * distances go by larger tile y, then larger x (a deterministic order, not a design value).
 * <p>TODO(design): the search for connected tiles is limited to {@link #MAX_CONNECTED_TILES} loaded
 * tiles (the nearest ones), a technical bound so that a pump at the edge of a large body of liquid
 * does not search all of it; "the farthest" is the farthest of those.
 * <p>Within one pump cycle ({@link #beginCycle}) the watch of the area and the connected-tile search
 * run once and are reused; only {@link #extract} changes tiles in a cycle and it keeps them up to
 * date, with the same results as searching again (a technical optimization, the rules above
 * unchanged).
 */
public final class LiquidTileSource implements FluidSource {

    /** The 5x5 area: two tiles around the pump (N19-3). */
    public static final int AREA_RADIUS = 2;

    private static final int AREA_SIDE = 2 * AREA_RADIUS + 1;
    /** A watched cell not seen loaded since it was last unloaded (or since the pump was loaded). */
    private static final int UNSEEN = -2;
    /** A watched cell seen loaded without liquid. */
    private static final int NO_FLUID = -1;

    /** TODO(design): technical bound of the connected-tile search (see the class comment). */
    public static final int MAX_CONNECTED_TILES = 1024;

    private final LiquidTileLookup lookup;
    private final int pumpX;
    private final int pumpY;
    private FluidType bufferedFluid;
    private int buffered;
    /** The 5x5 judgment (N20-7): whether the source is infinite; {@code null} until judged. */
    private Boolean infinite;
    /**
     * The fluid of each cell of the area as last seen loaded, row by row: {@link #UNSEEN},
     * {@link #NO_FLUID} or the fluid's ordinal (not saved).
     */
    private final int[] watched = new int[AREA_SIDE * AREA_SIDE];
    /** What one pump cycle has computed so far, or {@code null} outside a cycle. */
    private CycleMemo memo;

    /** Results reused within one pump cycle. */
    private static final class CycleMemo {
        boolean areaWatched;
        List<long[]> connected;
        /** The connected-tile search stopped at {@link #MAX_CONNECTED_TILES} with tiles left. */
        boolean truncated;
    }

    public LiquidTileSource(LiquidTileLookup lookup, int pumpX, int pumpY) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.pumpX = pumpX;
        this.pumpY = pumpY;
        Arrays.fill(watched, UNSEEN);
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
     * Start of a pump cycle ({@link Pump}): the loaded cells of the area are watched (N20-7), and
     * until {@link #endCycle} the connected-tile search is computed once and reused. Nothing but this
     * source's {@link #extract} may change the tiles in between.
     */
    void beginCycle() {
        memo = new CycleMemo();
        watchArea();
        memo.areaWatched = true;
    }

    /** End of the pump cycle: the next reads see the level as it is then. */
    void endCycle() {
        memo = null;
    }

    /**
     * The 5x5 judgment (N19-3 ①, N20-7): whether the source is infinite. Watches the loaded cells of
     * the area first (once per cycle, {@link #beginCycle}) and judges again when one changed.
     */
    public boolean isInfinite() {
        if (memo == null || !memo.areaWatched) {
            watchArea();
            if (memo != null) {
                memo.areaWatched = true;
            }
        }
        return infinite != null && infinite;
    }

    /**
     * Judges the area now from its loaded cells (N20-7): the pump was just placed. Nothing is judged
     * while the pump's own tile is not loaded.
     */
    public void judgeArea() {
        if (!lookup.isLoaded(pumpX, pumpY)) {
            return;
        }
        for (int i = 0; i < watched.length; i++) {
            int x = pumpX - AREA_RADIUS + i % AREA_SIDE;
            int y = pumpY - AREA_RADIUS + i / AREA_SIDE;
            watched[i] = lookup.isLoaded(x, y) ? cellCode(lookup.getFluid(x, y)) : UNSEEN;
        }
        infinite = computeInfinite();
    }

    /** The stored judgment, for saving: {@code null} while not judged yet. */
    public Boolean getJudgment() {
        return infinite;
    }

    /**
     * Restores a saved judgment (N20-7). The cells are watched from the next use on, as they are
     * then; {@code null} (a pump saved before the judgment was stored) leaves it to be judged at the
     * first use.
     */
    public void setJudgment(Boolean judgment) {
        infinite = judgment;
        Arrays.fill(watched, UNSEEN);
    }

    /**
     * Compares the loaded cells with how they were last seen: a change, or no judgment yet, judges
     * the area again. Cells that are not loaded are not watched until they load again.
     */
    private void watchArea() {
        boolean changed = false;
        for (int i = 0; i < watched.length; i++) {
            int x = pumpX - AREA_RADIUS + i % AREA_SIDE;
            int y = pumpY - AREA_RADIUS + i / AREA_SIDE;
            if (!lookup.isLoaded(x, y)) {
                watched[i] = UNSEEN;
                continue;
            }
            int code = cellCode(lookup.getFluid(x, y));
            if (watched[i] != UNSEEN && watched[i] != code) {
                changed = true;
            }
            watched[i] = code;
        }
        if ((infinite == null || changed) && lookup.isLoaded(pumpX, pumpY)) {
            infinite = computeInfinite();
        }
    }

    /**
     * Whether the pump tile's {@code fluid} never runs out: deep seawater (crude oil) always (N27-5),
     * any other fluid when the 5x5 judgment is infinite (N19-3 ①).
     */
    private boolean isInexhaustible(FluidType fluid) {
        return fluid == FluidType.CRUDE_OIL || isInfinite();
    }

    private static int cellCode(FluidType fluid) {
        return fluid == null ? NO_FLUID : fluid.ordinal();
    }

    /** The pump tile is liquid and every loaded cell of the area holds its fluid (N19-3 ①, N20-7). */
    private boolean computeInfinite() {
        FluidType center = lookup.getFluid(pumpX, pumpY);
        if (center == null) {
            return false;
        }
        for (int y = pumpY - AREA_RADIUS; y <= pumpY + AREA_RADIUS; y++) {
            for (int x = pumpX - AREA_RADIUS; x <= pumpX + AREA_RADIUS; x++) {
                if (lookup.isLoaded(x, y) && lookup.getFluid(x, y) != center) {
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
        return lookup.getFluid(pumpX, pumpY);
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
        if (lookup.getFluid(pumpX, pumpY) != type) {
            return 0;
        }
        if (isInexhaustible(type)) {
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
        if (taken == maxAmount || lookup.getFluid(pumpX, pumpY) != type) {
            return taken;
        }
        if (isInexhaustible(type)) {
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
     * The farthest tile was used up within a cycle. The source stays finite. When the
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
