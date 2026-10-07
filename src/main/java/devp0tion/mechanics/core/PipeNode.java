package devp0tion.mechanics.core;

/**
 * One pipe cell (9-1, 9-8). Pipes have no logic: a pipe only holds fluid as data (N7-1). Pumps push
 * fluid through them ({@link PipeGrid}).
 *
 * <ul>
 *     <li>Capacity: its own tier's transport amount (N12-3, {@link PipeTierRules}).</li>
 *     <li>Reached: fluid has reached the pipe ({@link #isReached()}), so it belongs to a network
 *     (N13-1). Pipes are never emptied (N16-5) and a removed pipe's fluid is lost (N12-1), so a pipe
 *     is reached exactly while it holds fluid.</li>
 *     <li>Link flags ({@link LinkFlags}): one per side and one vertical, toggled by the wrench (12-8,
 *     13-4, 13-5) and kept when the other side is removed (N16-4).</li>
 *     <li>Hints (N22-3, N24-2, N25-6): per destination valve, the direction of the next step toward
 *     it ({@link #HINT_VERTICAL} for the other layer on the tile, or the valve on the tile under an
 *     underground pipe). No distance is kept. The pipe engine writes them, the holder of the pipe's
 *     state saves them.</li>
 *     <li>Loaded: the pipe engine only holds pipes of loaded regions ({@link PipeGrid}); the
 *     engine before the ECS restructure kept unloaded ones as read-only mirrors (N14-3).</li>
 * </ul>
 * The basic pipe's state lives in its object entity, the underground pipe's in its holder (N19-6,
 * N22-2); both run this same logic.
 */
public final class PipeNode extends LiquidStorage {

    private final int tileX;
    private final int tileY;
    private final PipeLayer layer;
    private final MineralTier tier;
    private int links = LinkFlags.ALL_OPEN;
    boolean loaded = true;
    boolean removed;

    PipeNetwork network;
    private int writeCount;
    /** The pipes around it in the engine: N, E, S, W on its layer, then the other layer (technical). */
    final PipeNode[] around = new PipeNode[5];

    // Scratch fields of the engine's dry runs and cap order, valid for one stamp (technical).
    long dryStamp;
    int dryAdded;
    int dryFlow;
    boolean dryFilled;
    long capStamp;
    int capCount;
    int[] capPumps = new int[2];
    int[] capSteps = new int[2];

    /** The pumps of one cap order crossing this pipe, with the fewest steps of each. */
    void addCapEntry(int pump, int steps) {
        for (int i = 0; i < capCount; i++) {
            if (capPumps[i] == pump) {
                capSteps[i] = Math.min(capSteps[i], steps);
                return;
            }
        }
        if (capCount == capPumps.length) {
            capPumps = java.util.Arrays.copyOf(capPumps, capCount * 2);
            capSteps = java.util.Arrays.copyOf(capSteps, capCount * 2);
        }
        capPumps[capCount] = pump;
        capSteps[capCount] = steps;
        capCount++;
    }

    // Flow used in the current cycle window (N14-2), transient.
    long flowWindow = -1;
    int flowUsed;
    /** The push (one cycle of one pump) that first reached this pipe (the fill speed, N28-7), transient. */
    long reachedPush;

    /** Hint code of the step to the other layer on the same tile (or to the valve on it). */
    public static final int HINT_VERTICAL = 4;

    private static final long[] NO_DESTS = new long[0];
    private static final byte[] NO_CODES = new byte[0];
    private long[] hintDests = NO_DESTS;
    private byte[] hintCodes = NO_CODES;
    private int hintCount;

    PipeNode(int tileX, int tileY, PipeLayer layer, MineralTier tier, int capacity) {
        super(capacity);
        this.tileX = tileX;
        this.tileY = tileY;
        this.layer = layer;
        this.tier = tier;
    }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public PipeLayer getLayer() {
        return layer;
    }

    /** The material tier (9-2, 9-11). */
    public MineralTier getTier() {
        return tier;
    }

    /** Fluid has reached this pipe: it holds fluid (N13-1, N16-5). */
    public boolean isReached() {
        return !isEmpty();
    }

    /** The network of a reached pipe; {@code null} while empty (N13-1). */
    public PipeNetwork getNetwork() {
        return network;
    }

    /** False while the pipe's region is not loaded (a read-only mirror, N14-3). */
    public boolean isLoaded() {
        return loaded;
    }

    /** The link flags ({@link LinkFlags}). */
    public int getLinks() {
        return links;
    }

    public boolean isSideOpen(Direction direction) {
        return LinkFlags.isSideOpen(links, direction);
    }

    public boolean isVerticalOpen() {
        return LinkFlags.isVerticalOpen(links);
    }

    void setLinks(int links) {
        this.links = LinkFlags.sanitize(links);
    }

    void setSideOpen(Direction direction, boolean open) {
        links = LinkFlags.withSide(links, direction, open);
    }

    void setVerticalOpen(boolean open) {
        links = LinkFlags.withVertical(links, open);
    }

    /** Flow still allowed through this pipe in the cycle window (N14-2). */
    int flowLeft(long window) {
        return flowWindow == window ? Math.max(0, getCapacity() - flowUsed) : getCapacity();
    }

    void useFlow(long window, int amount) {
        if (flowWindow != window) {
            flowWindow = window;
            flowUsed = 0;
        }
        flowUsed += amount;
    }

    // ------------------------------------------------------------------ hints

    /** Number of destinations this pipe has a hint for. */
    public int getHintCount() {
        return hintCount;
    }

    /** The destination (valve tile, {@link PipeGrid#key}) of hint {@code i}. */
    public long getHintDestination(int i) {
        return hintDests[i];
    }

    /** The direction code of hint {@code i}: a {@link Direction} ordinal or {@link #HINT_VERTICAL}. */
    public int getHintCode(int i) {
        return hintCodes[i];
    }

    /** The hint toward {@code dest}, or -1 when there is none. */
    public int getHint(long dest) {
        for (int i = 0; i < hintCount; i++) {
            if (hintDests[i] == dest) {
                return hintCodes[i];
            }
        }
        return -1;
    }

    /** The saved hints: destination tile keys (copy). */
    public long[] getHintDestinations() {
        long[] result = new long[hintCount];
        System.arraycopy(hintDests, 0, result, 0, hintCount);
        return result;
    }

    /** The saved hints: direction codes in the order of {@link #getHintDestinations} (copy). */
    public byte[] getHintCodes() {
        byte[] result = new byte[hintCount];
        System.arraycopy(hintCodes, 0, result, 0, hintCount);
        return result;
    }

    /** Sets the hint toward {@code dest}; returns true when the pipe had none for it before. */
    boolean setHint(long dest, int code) {
        for (int i = 0; i < hintCount; i++) {
            if (hintDests[i] == dest) {
                hintCodes[i] = (byte) code;
                return false;
            }
        }
        if (hintCount == hintDests.length) {
            int size = Math.max(4, hintCount * 2);
            long[] dests = new long[size];
            byte[] codes = new byte[size];
            System.arraycopy(hintDests, 0, dests, 0, hintCount);
            System.arraycopy(hintCodes, 0, codes, 0, hintCount);
            hintDests = dests;
            hintCodes = codes;
        }
        hintDests[hintCount] = dest;
        hintCodes[hintCount] = (byte) code;
        hintCount++;
        return true;
    }

    /** Drops the hint toward {@code dest}; returns true when there was one. */
    boolean removeHint(long dest) {
        for (int i = 0; i < hintCount; i++) {
            if (hintDests[i] == dest) {
                hintCount--;
                hintDests[i] = hintDests[hintCount];
                hintCodes[i] = hintCodes[hintCount];
                return true;
            }
        }
        return false;
    }

    /** Replaces every hint (saved hints, before the pipe enters the engine); invalid codes are skipped. */
    void setHints(long[] dests, byte[] codes) {
        hintCount = 0;
        if (dests == null || codes == null) {
            return;
        }
        for (int i = 0; i < Math.min(dests.length, codes.length); i++) {
            if (codes[i] >= 0 && codes[i] <= HINT_VERTICAL) {
                setHint(dests[i], codes[i]);
            }
        }
    }

    /** How many times this pipe's contents were written (to check that full pipes are left alone). */
    int getWriteCount() {
        return writeCount;
    }

    @Override
    protected void onContentsChanged() {
        writeCount++;
    }

    @Override
    public String toString() {
        return "PipeNode[" + layer + " " + tileX + "," + tileY + " " + tier + (loaded ? "" : " unloaded") + ", "
                + (getFluid() == null ? "empty" : getFluid() + " " + getAmount()) + "/" + getCapacity() + "]";
    }

}
