package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.core.TankValve;
import devp0tion.mechanics.objects.TankValveObjectItem;
import devp0tion.mechanics.pipe.PipeSystem;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

import java.util.Objects;

/**
 * A tank valve's state (2-1, 2-3): the pipe network endpoint ({@link TankValve}) that passes
 * incoming fluid automatically into its tank (N7-3).
 *
 * <ul>
 *     <li>Normally on; off while any wire on its tile carries a signal (N11-3). Updated from
 *     {@code onWireUpdate} and every tick.</li>
 *     <li>Its tier: the mineral wall it was crafted from (N13-5 ②), from the item it was placed
 *     with ({@link TankValveObjectItem#onPlaceObject}). Saved, synced to clients and dropped with the
 *     item again. An item without tier data counts as copper (N19-7 (a)).</li>
 *     <li>Its owner: the controller of the tank that recognized it first (N13-3), set by that
 *     controller ({@link TankControllerObjectEntity}). It belongs to that tank while the controller
 *     keeps a tank with the valve in its border ({@link TankStructure#effectiveValveOwner}); another
 *     tank completed around it later does not take it (N15-3). Saved and synced to clients.</li>
 *     <li>In the level's pipe grid (server): a new valve's link toward a pump already next to it
 *     starts cut (N13-3, N16-3); its link flags (sides and the underground pipe on its tile) are
 *     saved and synced to clients, which draw cut faces (N16-4). Its tank is looked up every tick.
 *     When its region unloads, or the engine only replaces this entity with another one of the same
 *     valve (region loading, placement, {@link PipeSystem#isReplacedEntity}), it leaves the grid
 *     but the pumps next to it keep it as a source; only a removed valve stops being one.</li>
 * </ul>
 * TODO(design): automatic output from the valve is TODO (N7-3).
 */
public class TankValveObjectEntity extends ObjectEntity {

    /** Object entity type; must stay the same for saved worlds. */
    public static final String TYPE = "tankvalve";

    private final TankValve valve = new TankValve();
    private MineralTier tier = TankValveObjectItem.DEFAULT_TIER;
    private GridPos owner;
    private boolean loadedFromSave;
    private boolean registered;
    private boolean unloading;
    /** The object this entity was created for ({@link PipeSystem#isReplacedEntity}). */
    private int objectID = -1;
    private int links = LinkFlags.ALL_OPEN;

    public TankValveObjectEntity(Level level, int tileX, int tileY) {
        super(level, TYPE, tileX, tileY);
        // Saved: the tier, the owner and the link flags. The on/off state comes from the wires.
    }

    @Override
    public void init() {
        super.init();
        objectID = getLevel().getObjectID(tileX, tileY);
        updateWireSignal();
        PipeSystem system = PipeSystem.get(getLevel());
        if (system == null) {
            return;
        }
        refreshTank();
        PipeGrid grid = system.getGrid();
        try {
            if (loadedFromSave) {
                valve.setLinks(links);
                grid.loadValve(tileX, tileY, valve);
            } else {
                grid.placeValve(tileX, tileY, valve);
            }
            registered = true;
            links = valve.getLinks();
        } catch (IllegalStateException e) {
            System.err.println("Mechanics: valve at " + tileX + "," + tileY + " not registered: " + e.getMessage());
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
        if (system != null && registered && system.getGrid().getValve(tileX, tileY) == valve) {
            if (unloading || PipeSystem.isReplacedEntity(this, objectID)) {
                // Pumps next to it keep it as a source while its region is unloaded, and when the
                // engine only replaces this entity with another one of the same valve.
                system.getGrid().unloadValve(tileX, tileY);
            } else {
                system.getGrid().removeValve(tileX, tileY);
            }
        }
        registered = false;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (registered) {
            updateWireSignal();
            refreshTank();
        }
    }

    /** Reads the wire signal on the valve's tile (N11-3). */
    public void updateWireSignal() {
        valve.applyWireSignal(getLevel().wireManager.isWireActiveAny(tileX, tileY));
    }

    private void refreshTank() {
        valve.setTank(TankRegistry.findValveTank(getLevel(), tileX, tileY, owner));
    }

    /** Whether the valve is on (no wire signal, N11-3). */
    public boolean isOn() {
        return valve.isEnabled();
    }

    /** The valve's tier (N13-5 ②). */
    public MineralTier getTier() {
        return tier;
    }

    public void setTier(MineralTier tier) {
        Objects.requireNonNull(tier, "tier");
        if (tier != this.tier) {
            this.tier = tier;
            markDirty();
        }
    }

    /** The controller the valve remembers belonging to (N13-3), or {@code null}. */
    public GridPos getOwner() {
        return owner;
    }

    /** Called by the controller that recognizes the valve's tank (N13-3). */
    void setOwner(GridPos owner) {
        if (!Objects.equals(owner, this.owner)) {
            this.owner = owner;
            markDirty();
        }
    }

    /** The core valve, linked to the tank it belongs to now. Server only (clients hold no fluid). */
    public TankValve getValve() {
        updateWireSignal();
        refreshTank();
        return valve;
    }

    /** The link flags ({@link LinkFlags}; both sides: clients get them synced). */
    public int getLinks() {
        return links;
    }

    /** Called when the grid changed the flags: sync them to clients. */
    public void syncLinks() {
        if (valve.getLinks() != links) {
            links = valve.getLinks();
            markDirty();
        }
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addEnum("tier", tier);
        if (owner != null) {
            save.addInt("ownerX", owner.x);
            save.addInt("ownerY", owner.y);
        }
        save.addInt("links", registered ? valve.getLinks() : links);
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        loadedFromSave = true;
        tier = save.getEnum(MineralTier.class, "tier", TankValveObjectItem.DEFAULT_TIER, false);
        owner = save.hasLoadDataByName("ownerX") && save.hasLoadDataByName("ownerY")
                ? new GridPos(save.getInt("ownerX", 0, false), save.getInt("ownerY", 0, false)) : null;
        // Valves saved before round 4b have no flags: everything open.
        links = LinkFlags.sanitize(save.getInt("links", LinkFlags.ALL_OPEN, false));
    }

    @Override
    public void setupContentPacket(PacketWriter writer) {
        super.setupContentPacket(writer);
        writer.putNextByteUnsigned(tier.ordinal());
        writer.putNextBoolean(owner != null);
        if (owner != null) {
            writer.putNextInt(owner.x);
            writer.putNextInt(owner.y);
        }
        writer.putNextByteUnsigned(registered ? valve.getLinks() : links);
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        int tierIndex = reader.getNextByteUnsigned();
        tier = tierIndex < MineralTier.values().length ? MineralTier.values()[tierIndex] : TankValveObjectItem.DEFAULT_TIER;
        owner = reader.getNextBoolean() ? new GridPos(reader.getNextInt(), reader.getNextInt()) : null;
        links = LinkFlags.sanitize(reader.getNextByteUnsigned());
    }

}
