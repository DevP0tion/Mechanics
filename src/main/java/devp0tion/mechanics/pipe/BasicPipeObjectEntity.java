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
 * A basic pipe's state (N19-6): its cell in the level's pipe grid ({@link PipeNode}: fluid amount,
 * link flags; reached = holds fluid).
 *
 * <ul>
 *     <li>Server: registers the pipe in the level's grid when created; a new pipe starts empty with
 *     its sides open and its vertical link cut over an underground pipe (N16-4), a loaded one with
 *     its saved state. When its region unloads the grid keeps a read-only mirror (N14-3), also when
 *     the engine only replaces this entity with another one of the same pipe (region loading,
 *     placement, {@link PipeSystem#isReplacedEntity}): the next entity takes the cell over. When the
 *     pipe is removed its fluid is lost (N12-1).</li>
 *     <li>Saved: link flags, fluid and amount.</li>
 *     <li>Clients get the link flags, which they draw (connections and cut faces, N16-4), and the
 *     sides whose neighbouring basic pipe holds another fluid ({@link PipeGrid#getFluidBlockedSides}),
 *     drawn like cut faces since they are dead ends (N13-2). Those change only when a pipe here or
 *     next to it starts or stops holding fluid. The fluid stays on the server (no pipe fluid display
 *     is decided).</li>
 * </ul>
 */
public class BasicPipeObjectEntity extends ObjectEntity {

    /** Object entity type; must stay the same for saved worlds. */
    public static final String TYPE = "mechanicsbasicpipe";

    private PipeNode node;
    private boolean loadedFromSave;
    private boolean unloading;
    /** The object this entity was created for ({@link PipeSystem#isReplacedEntity}). */
    private int objectID = -1;
    private int links = LinkFlags.ALL_OPEN;
    /** Sides blocked by another fluid next to it (both sides: clients get them synced). */
    private int blockedSides;
    private FluidType savedFluid;
    private int savedAmount;

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
        PipeSystem system = PipeSystem.get(getLevel());
        if (system == null) {
            return;
        }
        PipeGrid grid = system.getGrid();
        try {
            if (loadedFromSave || grid.getPipe(tileX, tileY, PipeLayer.BASE) != null) {
                PipeNode mirror = grid.getPipe(tileX, tileY, PipeLayer.BASE);
                int loadLinks = loadedFromSave ? links : mirror.getLinks();
                FluidType fluid = loadedFromSave ? savedFluid : mirror.getFluid();
                int amount = loadedFromSave ? savedAmount : mirror.getAmount();
                node = grid.loadPipe(tileX, tileY, PipeLayer.BASE, tier(), loadLinks, fluid, amount, true);
            } else {
                node = grid.placePipe(tileX, tileY, PipeLayer.BASE, tier());
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
        if (system != null && node != null) {
            if (unloading) {
                system.getGrid().unloadPipe(tileX, tileY, PipeLayer.BASE);
            } else if (system.getGrid().getPipe(tileX, tileY, PipeLayer.BASE) == node) {
                if (PipeSystem.isReplacedEntity(this, objectID)) {
                    // Only this entity is replaced (same pipe): the next one takes the cell over, as
                    // after an unload, with no network rebuild.
                    system.getGrid().unloadPipe(tileX, tileY, PipeLayer.BASE);
                } else {
                    system.getGrid().removePipe(tileX, tileY, PipeLayer.BASE);
                }
            }
        }
        node = null;
    }

    /** The grid cell (server), or {@code null}. */
    public PipeNode getNode() {
        return node;
    }

    /** The link flags (both sides: clients get them synced). */
    public int getLinks() {
        return links;
    }

    /** Called when the grid changed the flags: sync them to clients. */
    void syncLinks() {
        if (node != null && node.getLinks() != links) {
            links = node.getLinks();
            markDirty();
        }
    }

    /** The sides blocked by another fluid in the neighbouring basic pipe (N13-2; both sides). */
    public int getBlockedSides() {
        return blockedSides;
    }

    /** Called when this pipe or one next to it started or stopped holding fluid: sync the blocked sides. */
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
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        loadedFromSave = true;
        links = LinkFlags.sanitize(save.getInt("links", LinkFlags.ALL_OPEN, false));
        savedFluid = save.getEnum(FluidType.class, "fluid", null, false);
        savedAmount = savedFluid == null ? 0 : Math.max(0, save.getInt("amount", 0, false));
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
        blockedSides = reader.getNextByteUnsigned() & LinkFlags.SIDES;
    }

}
