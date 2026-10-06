package devp0tion.mechanics.core;

/**
 * One pipe cell (9-1, 9-8). Pipes have no logic: a pipe only holds fluid as data (N7-1), up to its
 * network's cell capacity (N7-3). Pumps push fluid through them ({@link PipeGrid}).
 *
 * <p>Link flags, all open on a new pipe (9-4, 9-5) and toggled by the wrench (12-8, 13-4, 13-5):
 * <ul>
 *     <li>one per direction: the link to the same-layer pipe, pump or valve on that side. Two pipes
 *     are linked only when both of their facing flags are open.</li>
 *     <li>a vertical flag: the link to the other layer's pipe on the same tile (both flags must be
 *     open), or, for an underground pipe, to the tank valve on the same tile (9-9).</li>
 * </ul>
 */
public final class PipeNode extends LiquidStorage {

    private final int tileX;
    private final int tileY;
    private final PipeLayer layer;
    private final MineralTier tier;
    private final boolean[] sideOpen = {true, true, true, true};
    private boolean verticalOpen = true;

    PipeNetwork network;
    private int writeCount;

    PipeNode(int tileX, int tileY, PipeLayer layer, MineralTier tier) {
        super(0);
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

    /** The network this pipe belongs to (every placed pipe belongs to exactly one). */
    public PipeNetwork getNetwork() {
        return network;
    }

    public boolean isSideOpen(Direction direction) {
        return sideOpen[direction.ordinal()];
    }

    void setSideOpen(Direction direction, boolean open) {
        sideOpen[direction.ordinal()] = open;
    }

    public boolean isVerticalOpen() {
        return verticalOpen;
    }

    void setVerticalOpen(boolean open) {
        verticalOpen = open;
    }

    void applyCellCapacity(int capacity) {
        setCapacity(capacity);
    }

    /** How many times this pipe's contents were written (to check that full pipes are left alone). */
    int getWriteCount() {
        return writeCount;
    }

    @Override
    protected void onContentsChanged() {
        writeCount++;
        if (network != null) {
            network.onNodeContentsChanged(this);
        }
    }

    @Override
    public String toString() {
        return "PipeNode[" + layer + " " + tileX + "," + tileY + " " + tier + ", "
                + (getFluid() == null ? "empty" : getFluid() + " " + getAmount()) + "/" + getCapacity() + "]";
    }

}
