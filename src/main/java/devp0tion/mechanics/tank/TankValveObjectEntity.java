package devp0tion.mechanics.tank;

import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.core.TankValve;
import devp0tion.mechanics.objects.TankValveObjectItem;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

import java.util.Objects;

/**
 * A tank valve's state (2-1, 2-3): the pipe network endpoint ({@link TankValve}) that passes
 * incoming fluid automatically into its tank (N7-3).
 *
 * <ul>
 *     <li>Normally on; off while any wire on its tile carries a signal (N11-3). Updated from
 *     {@code onWireUpdate} and whenever the valve is used.</li>
 *     <li>Its tier: the mineral wall it was crafted from (N13-5 ②), from the item it was placed
 *     with ({@link TankValveObjectItem#onPlaceObject}). Saved, synced to clients and dropped with the
 *     item again.</li>
 *     <li>Its owner: the controller of the tank that recognized it first (N13-3), set by that
 *     controller ({@link TankControllerObjectEntity}). It belongs to that tank while the controller
 *     keeps a tank with the valve in its border ({@link TankStructure#effectiveValveOwner}); another
 *     tank completed around it later does not take it (N15-3). Saved and synced to clients.</li>
 * </ul>
 * TODO(game): pipes do not exist yet (4th implementation round, N11-5), so nothing delivers fluid
 * to the valve yet; the pipe network will use {@link #getValve()} as its endpoint.
 * TODO(design): automatic output from the valve is TODO (N7-3).
 */
public class TankValveObjectEntity extends ObjectEntity {

    /** Object entity type; must stay the same for saved worlds. */
    public static final String TYPE = "tankvalve";

    private final TankValve valve = new TankValve();
    private MineralTier tier = TankValveObjectItem.DEFAULT_TIER;
    private GridPos owner;

    public TankValveObjectEntity(Level level, int tileX, int tileY) {
        super(level, TYPE, tileX, tileY);
        // Saved: the tier and the owner. The on/off state comes from the wires.
    }

    @Override
    public void init() {
        super.init();
        updateWireSignal();
    }

    /** Reads the wire signal on the valve's tile (N11-3). */
    public void updateWireSignal() {
        valve.applyWireSignal(getLevel().wireManager.isWireActiveAny(tileX, tileY));
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
        valve.setTank(TankRegistry.findValveTank(getLevel(), tileX, tileY, owner));
        return valve;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addEnum("tier", tier);
        if (owner != null) {
            save.addInt("ownerX", owner.x);
            save.addInt("ownerY", owner.y);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        tier = save.getEnum(MineralTier.class, "tier", TankValveObjectItem.DEFAULT_TIER, false);
        owner = save.hasLoadDataByName("ownerX") && save.hasLoadDataByName("ownerY")
                ? new GridPos(save.getInt("ownerX", 0, false), save.getInt("ownerY", 0, false)) : null;
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
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        int tierIndex = reader.getNextByteUnsigned();
        tier = tierIndex < MineralTier.values().length ? MineralTier.values()[tierIndex] : TankValveObjectItem.DEFAULT_TIER;
        owner = reader.getNextBoolean() ? new GridPos(reader.getNextInt(), reader.getNextInt()) : null;
    }

}
