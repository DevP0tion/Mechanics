package devp0tion.mechanics.pipe;

import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.PipeNode;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

/**
 * The holder of one underground pipe's state (N22-2, N24-1): an object entity keyed by
 * (x, y, layer) that the mod owns, since the engine only makes object entities for the base layer.
 *
 * <ul>
 *     <li>It holds the pipe's component, its {@link PipeNode} in the level's pipe engine (fluid, amount,
 *     link flags, hints, N25-6), and nothing runs in it (N22-5).</li>
 *     <li>It lives in the pipe system's own entity list ({@link PipeSystem}), not in the engine's.</li>
 *     <li>It is saved in the region's {@code OBJECTENTITIES} section like an engine object entity,
 *     with a {@value #LAYER_FIELD} field naming the underground pipe layer ({@link HolderSavePatches}).
 *     Without the mod the engine drops such an entry with an error in the log.</li>
 *     <li>Clients get its link flags through the mod's packet ({@link PacketUndergroundPipes}, N26-2).</li>
 * </ul>
 */
public class UndergroundPipeHolder extends ObjectEntity {

    /** Object entity type in the save; must stay the same for saved worlds. */
    public static final String TYPE = "mechanicsundergroundpipe";

    /** The save entry field that marks a holder and names its layer (N24-1). */
    public static final String LAYER_FIELD = "layer";

    private PipeNode node;
    private boolean fromSave;
    private int links = LinkFlags.ALL_OPEN;
    private FluidType savedFluid;
    private int savedAmount;
    private long[] savedHintDests;
    private byte[] savedHintCodes;

    public UndergroundPipeHolder(Level level, int tileX, int tileY) {
        super(level, TYPE, tileX, tileY);
    }

    /** The pipe in the engine, or {@code null} while it is not registered. */
    public PipeNode getNode() {
        return node;
    }

    void setNode(PipeNode node) {
        this.node = node;
    }

    /** Whether its state came from a save entry (N24-1). */
    boolean isFromSave() {
        return fromSave;
    }

    int getSavedLinks() {
        return links;
    }

    FluidType getSavedFluid() {
        return savedFluid;
    }

    int getSavedAmount() {
        return savedAmount;
    }

    long[] getSavedHintDests() {
        return savedHintDests;
    }

    byte[] getSavedHintCodes() {
        return savedHintCodes;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addInt("links", node != null ? node.getLinks() : links);
        FluidType fluid = node != null ? node.getFluid() : savedFluid;
        int amount = node != null ? node.getAmount() : savedAmount;
        if (fluid != null && amount > 0) {
            save.addEnum("fluid", fluid);
            save.addInt("amount", amount);
        }
        long[] dests = node != null ? node.getHintDestinations() : savedHintDests;
        byte[] codes = node != null ? node.getHintCodes() : savedHintCodes;
        if (dests != null && dests.length > 0) {
            save.addLongArray("hintDests", dests);
            save.addByteArray("hintCodes", codes);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        fromSave = true;
        links = LinkFlags.sanitize(save.getInt("links", LinkFlags.ALL_OPEN, false));
        savedFluid = save.getEnum(FluidType.class, "fluid", null, false);
        savedAmount = savedFluid == null ? 0 : Math.max(0, save.getInt("amount", 0, false));
        if (savedAmount == 0) {
            savedFluid = null;
        }
        savedHintDests = save.getLongArray("hintDests", null, false);
        savedHintCodes = save.getByteArray("hintCodes", null, false);
    }

    @Override
    public void serverTick() {
        // Data only: the pipe system's systems run everything (N22-5).
    }

    @Override
    public void clientTick() {
    }

}
