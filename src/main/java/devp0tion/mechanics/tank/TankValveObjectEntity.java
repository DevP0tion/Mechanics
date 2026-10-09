package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.TankValve;
import devp0tion.mechanics.core.TankValveRole;
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
 *     <li>Normally on; off while any wire on its tile carries a signal (N11-3); a valve that is off
 *     neither takes incoming fluid nor lets a linked pump pull through it (N27-4). Updated from
 *     {@code onWireUpdate} and every tick by the pipe system's systems ({@link PipeSystem#tick},
 *     N22-5).</li>
 *     <li>Its tier: the mineral wall it was crafted from (N13-5 ②), from the item it was placed
 *     with ({@link TankValveObjectItem#onPlaceObject}). Saved, synced to clients and dropped with the
 *     item again. An item without tier data counts as copper (N19-7 (a)).</li>
 *     <li>Its tank (N33-1, {@link TankRegistry#valveRole}): the one recognized tank with the valve in
 *     its border. In a wall shared by two recognized tanks it is a plain wall for both
 *     ({@link #isPlainWall}): neither a destination nor a source, and the pipes linked to it, the
 *     underground pipe on its tile and the pumps next to it exchange no fluid through it
 *     ({@link PipeGrid#setValvePlainWall}); the pumps keep it in their sources where it is and skip
 *     it until it is a valve again (N35-1, as a valve switched off by a wire signal, N27-4). It
 *     follows the judgments: a valve that tank A used becomes a plain wall once a tank B next to A
 *     is recognized, and works again once the sharing ends. Looked up every tick, not saved (old
 *     saves' owner is ignored); the plain wall state is synced to clients, which draw and collide
 *     with it as a wall. Like a wall it shows
 *     nothing about it (decided, as the wrench's N33-15): the valve has no hover tooltip of its own,
 *     and the wrench shows none on it.</li>
 *     <li>In the level's pipe grid (server): a new valve next to a pump is linked to it at once
 *     (N36-17, N36-18, replacing the cut start of N13-3 and N16-3, N36-22); its link flags (sides and the underground pipe on its tile) are
 *     saved and synced to clients, which draw cut faces (N16-4). Its tank is looked up every tick
 *     by the pipe system's systems. While its region loads, the engine's fresh entity registers
 *     nothing: the saved entity replaces it, or the region's loaded event registers it (A1, as basic
 *     pipes and pumps). When its region unloads, or the engine only replaces this entity with
 *     another one of the same valve (region loading, placement, {@link PipeSystem#isReplacedEntity}),
 *     it leaves the grid: the valve pump in front of it has no source until it is back (N36-58), and
 *     only a removed valve stops being its source for good.</li>
 * </ul>
 * TODO(design): automatic output from the valve is TODO (N7-3).
 */
public class TankValveObjectEntity extends ObjectEntity {

    /** Object entity type; must stay the same for saved worlds. */
    public static final String TYPE = "tankvalve";

    private final TankValve valve = new TankValve();
    private MineralTier tier = TankValveObjectItem.DEFAULT_TIER;
    /** In a wall shared by two recognized tanks (N33-1): synced to clients, not saved. */
    private boolean plainWall;
    private boolean loadedFromSave;
    private boolean deferred;
    private boolean registered;
    private boolean unloading;
    /** The object this entity was created for ({@link PipeSystem#isReplacedEntity}). */
    private int objectID = -1;
    private int links = LinkFlags.ALL_OPEN;

    public TankValveObjectEntity(Level level, int tileX, int tileY) {
        super(level, TYPE, tileX, tileY);
        // Saved: the tier and the link flags. The on/off state comes from the wires, the tank and the
        // plain wall state from the tanks around it (N33-1).
    }

    @Override
    public void init() {
        super.init();
        objectID = getLevel().getObjectID(tileX, tileY);
        updateWireSignal();
        if (!getLevel().isServer()) {
            return;
        }
        if (!loadedFromSave && PipeSystem.isRegionLoading(this)) {
            // A fresh entity of a loading region (A1): the saved one replaces it, or the region's
            // loaded event registers it.
            deferred = true;
            return;
        }
        register();
    }

    /** Registers a valve whose fresh entity no saved one replaced while its region loaded. */
    public void registerIfDeferred() {
        if (deferred && !removed()) {
            deferred = false;
            register();
        }
    }

    private void register() {
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
            system.addValveEntity(this);
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
        if (system != null) {
            system.removeValveEntity(this);
        }
        if (system != null && registered && system.getGrid().getValve(tileX, tileY) == valve) {
            if (unloading || PipeSystem.isReplacedEntity(this, objectID)) {
                // Its region unloads, or the engine only replaces this entity with another one of the
                // same valve: the valve pump in front of it has no source while it is out and pulls
                // from it again once it is back (N36-58).
                system.getGrid().unloadValve(tileX, tileY);
            } else {
                system.getGrid().removeValve(tileX, tileY);
            }
        }
        registered = false;
    }

    @Override
    public void serverTick() {
        // The entity's own tick only: the wire signal and the tank are read by the pipe system's
        // systems (N22-5, {@link #updateFromLevel}).
        super.serverTick();
    }

    /**
     * The valve step of the pipe system's systems (N22-5), every tick before the pumps run: the wire
     * signal on its tile (N11-3, N27-4) and its tank, read from the level.
     */
    public void updateFromLevel() {
        if (registered) {
            updateWireSignal();
            refreshTank();
        }
    }

    /** Reads the wire signal on the valve's tile (N11-3). */
    public void updateWireSignal() {
        valve.applyWireSignal(getLevel().wireManager.isWireActiveAny(tileX, tileY));
    }

    /**
     * Looks the valve's tank up (N33-1): the storage of the tank it works for, and whether it is a
     * plain wall now. A change of the plain wall state is a structure change in the grid (the links
     * through the valve appear or disappear); a valve not in the grid yet just takes the state.
     */
    private void refreshTank() {
        TankValveRole<TankControllerObjectEntity> role = TankRegistry.valveRole(getLevel(), tileX, tileY);
        TankControllerObjectEntity controller = role.getTank();
        valve.setTank(controller == null ? null : controller.getStorage());
        PipeSystem system = registered ? PipeSystem.getIfExists(getLevel()) : null;
        if (system != null && system.getGrid().getValve(tileX, tileY) == valve) {
            system.getGrid().setValvePlainWall(tileX, tileY, role.isPlainWall());
        } else {
            valve.setPlainWall(role.isPlainWall());
        }
        if (plainWall != role.isPlainWall()) {
            plainWall = role.isPlainWall();
            markDirty();
        }
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

    /**
     * Whether the valve is in a wall shared by two recognized tanks: a plain wall for both (N33-1).
     * Both sides: clients get it synced.
     */
    public boolean isPlainWall() {
        return plainWall;
    }

    /** The core valve, linked to the tank it works for now (N33-1). Server only (clients hold no fluid). */
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
        save.addInt("links", registered ? valve.getLinks() : links);
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        loadedFromSave = true;
        tier = save.getEnum(MineralTier.class, "tier", TankValveObjectItem.DEFAULT_TIER, false);
        // An owner saved before N33-1 (ownerX, ownerY) is ignored: the tank is looked up every tick.
        // Valves saved before round 4b have no flags: everything open.
        links = LinkFlags.sanitize(save.getInt("links", LinkFlags.ALL_OPEN, false));
    }

    @Override
    public void setupContentPacket(PacketWriter writer) {
        super.setupContentPacket(writer);
        writer.putNextByteUnsigned(tier.ordinal());
        writer.putNextBoolean(plainWall);
        writer.putNextByteUnsigned(registered ? valve.getLinks() : links);
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        int tierIndex = reader.getNextByteUnsigned();
        tier = tierIndex < MineralTier.values().length ? MineralTier.values()[tierIndex] : TankValveObjectItem.DEFAULT_TIER;
        plainWall = reader.getNextBoolean();
        links = LinkFlags.sanitize(reader.getNextByteUnsigned());
    }

}
