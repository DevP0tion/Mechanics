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
 *     <li>Loaded: a pipe in an unloaded region stays in the grid as a read-only mirror of its last
 *     state (N14-3, N15-1): pushes may pass through it when it is full and never write it.</li>
 * </ul>
 * The basic pipe's state lives in its object entity, the underground pipe's in the level store
 * (N19-6, N14-4); both run this same logic.
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

    // Flow used in the current cycle window (N14-2), transient.
    long flowWindow = -1;
    int flowUsed;

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
