package devp0tion.mechanics.pipe;

import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.PipeLayer;
import devp0tion.mechanics.core.PipeNode;
import devp0tion.mechanics.objects.BasicPipeObject;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

/**
 * A basic pipe's state (N19-6, N22-2): its component in the level's pipe engine ({@link PipeNode}:
 * fluid amount, link flags, hints; reached = holds fluid). It holds data only; nothing runs in it
 * (N22-5).
 *
 * <ul>
 *     <li>Server: registers the pipe in the engine when created; a new pipe starts empty with its
 *     sides open and its vertical link cut over an underground pipe (N16-4), a loaded one with its
 *     saved state and hints (N25-6). While its region loads, the engine's fresh entity registers
 *     nothing: the saved entity replaces it, or the region's loaded event registers it (A1). When its
 *     region unloads, or the engine only replaces this entity with another one of the same pipe
 *     ({@link PipeSystem#isReplacedEntity}), the pipe leaves the engine (no mirror, N23-2). When the
 *     pipe is removed its fluid is lost (N12-1).</li>
 *     <li>Saved: link flags, fluid and amount, hints (per destination valve, its direction, N24-2;
 *     destinations as numbers of its group's table, with the table's id, N28-15).</li>
 *     <li>Clients get the link flags, which they draw (connections and cut faces, N16-4), and the
 *     faces where a pipe holding another fluid meets it ({@link PipeGrid#getFluidBlockedSides}: the
 *     neighbouring basic pipes, and the underground pipe on its tile through the vertical link),
 *     drawn like cut faces since they are dead ends (N13-2). The fluid stays on the server.</li>
 * </ul>
 */
public class BasicPipeObjectEntity extends ObjectEntity {

    /** Object entity type; must stay the same for saved worlds. */
    public static final String TYPE = "mechanicsbasicpipe";

    private PipeNode node;
    private boolean loadedFromSave;
    private boolean deferred;
    private boolean unloading;
    /** The object this entity was created for ({@link PipeSystem#isReplacedEntity}). */
    private int objectID = -1;
    private int links = LinkFlags.ALL_OPEN;
    /** Faces blocked by another fluid (sides and vertical; both sides: clients get them synced). */
    private int blockedSides;
    private FluidType savedFluid;
    private int savedAmount;
    private int savedHintTable = -1;
    private int[] savedHintNumbers;
    private byte[] savedHintCodes;

    public BasicPipeObjectEntity(Level level, int tileX, int tileY) {
        super(level, TYPE, tileX, tileY);
    }

    private MineralTier tier() {
        GameObject object = getLevel().getObject(tileX, tileY);
        return object instanceof BasicPipeObject ? ((BasicPipeObject) object).getMineralTier() : MineralTier.COPPER;
    }

    @Override
    public void init() {
        super.init();
        objectID = getLevel().getObjectID(tileX, tileY);
        if (!getLevel().isServer()) {
            return;
        }
        if (!loadedFromSave && PipeSystem.isRegionLoading(this)) {
            // A fresh entity of a loading region (A1): the saved one replaces it, or the region's
            // loaded event registers it.
            deferred = true;
            return;
        }
        register(!loadedFromSave);
    }

    /** Registers a pipe whose fresh entity no saved one replaced while its region loaded. */
    void registerIfDeferred() {
        if (deferred && !removed()) {
            deferred = false;
            register(false);
        }
    }

    private void register(boolean placed) {
        PipeSystem system = PipeSystem.get(getLevel());
        if (system == null) {
            return;
        }
        PipeGrid grid = system.getGrid();
        try {
            if (placed) {
                node = grid.placePipe(tileX, tileY, PipeLayer.BASE, tier());
            } else if (loadedFromSave) {
                node = grid.loadPipe(tileX, tileY, PipeLayer.BASE, tier(), links, savedFluid, savedAmount,
                        savedHintTable, savedHintNumbers, savedHintCodes);
            } else {
                // No saved state: as when it was placed, but loaded (not a structure change).
                int startLinks = grid.getPipe(tileX, tileY, PipeLayer.UNDERGROUND) != null
                        ? LinkFlags.withVertical(LinkFlags.ALL_OPEN, false) : LinkFlags.ALL_OPEN;
                node = grid.loadPipe(tileX, tileY, PipeLayer.BASE, tier(), startLinks, null, 0, true);
            }
            links = node.getLinks();
            blockedSides = grid.getFluidBlockedSides(tileX, tileY, PipeLayer.BASE);
        } catch (IllegalStateException e) {
            System.err.println("Mechanics: basic pipe at " + tileX + "," + tileY + " not registered: " + e.getMessage());
        }
    }

    @Override
    public void onUnloading(Region region) {
        super.onUnloading(region);
        unloading = true;
    }

    @Override
    public void remove() {
        super.remove();
        PipeSystem system = PipeSystem.getIfExists(getLevel());
        if (system != null && node != null && system.getGrid().getPipe(tileX, tileY, PipeLayer.BASE) == node) {
            if (unloading || PipeSystem.isReplacedEntity(this, objectID)) {
                // Its region unloaded, or only this entity is replaced (same pipe): it leaves the
                // engine; the state stays here (saved) or goes to the next entity.
                PipeNode left = system.getGrid().unloadPipe(tileX, tileY, PipeLayer.BASE);
                keepState(left);
            } else {
                system.getGrid().removePipe(tileX, tileY, PipeLayer.BASE);
            }
        }
        node = null;
    }

    private void keepState(PipeNode left) {
        if (left != null) {
            links = left.getLinks();
            savedFluid = left.getFluid();
            savedAmount = left.getAmount();
            savedHintTable = left.getHintTableId();
            savedHintNumbers = left.getHintNumbers();
            savedHintCodes = left.getHintCodes();
        }
    }

    /** The engine's component (server), or {@code null}. */
    public PipeNode getNode() {
        return node;
    }

    /** The link flags (both sides: clients get them synced). */
    public int getLinks() {
        return links;
    }

    /** Called when the engine changed the flags: sync them to clients. */
    void syncLinks() {
        if (node != null && node.getLinks() != links) {
            links = node.getLinks();
            markDirty();
        }
    }

    /** The faces blocked by another fluid (N13-2; sides and vertical, {@link LinkFlags} bits; both sides). */
    public int getBlockedSides() {
        return blockedSides;
    }

    /** Called when this pipe or one next to it or under it started or stopped holding fluid: sync the blocked faces. */
    void syncBlockedSides() {
        PipeSystem system = PipeSystem.getIfExists(getLevel());
        if (system == null || node == null) {
            return;
        }
        int sides = system.getGrid().getFluidBlockedSides(tileX, tileY, PipeLayer.BASE);
        if (sides != blockedSides) {
            blockedSides = sides;
            markDirty();
        }
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        int saveLinks = node != null ? node.getLinks() : links;
        FluidType fluid = node != null ? node.getFluid() : savedFluid;
        int amount = node != null ? node.getAmount() : savedAmount;
        save.addInt("links", saveLinks);
        if (fluid != null && amount > 0) {
            save.addEnum("fluid", fluid);
            save.addInt("amount", amount);
        }
        // Hints: numbers in the group's table, saved with the table's id (N28-15).
        int table = node != null ? node.getHintTableId() : savedHintTable;
        int[] numbers = node != null ? node.getHintNumbers() : savedHintNumbers;
        byte[] codes = node != null ? node.getHintCodes() : savedHintCodes;
        if (table >= 0 && numbers != null && numbers.length > 0) {
            save.addInt("hintTable", table);
            save.addIntArray("hintNumbers", numbers);
            save.addByteArray("hintCodes", codes);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        loadedFromSave = true;
        links = LinkFlags.sanitize(save.getInt("links", LinkFlags.ALL_OPEN, false));
        savedFluid = save.getEnum(FluidType.class, "fluid", null, false);
        savedAmount = savedFluid == null ? 0 : Math.max(0, save.getInt("amount", 0, false));
        if (savedAmount == 0) {
            savedFluid = null;
        }
        savedHintTable = save.getInt("hintTable", -1, false);
        savedHintNumbers = save.getIntArray("hintNumbers", null, false);
        savedHintCodes = save.getByteArray("hintCodes", null, false);
    }

    @Override
    public void setupContentPacket(PacketWriter writer) {
        super.setupContentPacket(writer);
        writer.putNextByteUnsigned(node != null ? node.getLinks() : links);
        writer.putNextByteUnsigned(blockedSides);
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        links = LinkFlags.sanitize(reader.getNextByteUnsigned());
        blockedSides = LinkFlags.sanitize(reader.getNextByteUnsigned());
    }

}
