package devp0tion.mechanics.tank;

import devp0tion.mechanics.client.TankFluidRendering;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankStatusText;
import devp0tion.mechanics.core.TankStorage;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.core.TankValidation;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

import java.util.Objects;

/**
 * The tank controller's state (2-1, 4-3): it recognizes the multiblock tank around it and holds the
 * tank's fluid (5-9).
 *
 * <h2>Server</h2>
 * <ul>
 *     <li>Searches its tank ({@link TankStructure#findTank}) when it is created and again after
 *     any object or floor tile within reach changed ({@link TankRegistry#onTileChanged}, 5-1). While
 *     part of the area a tank could cover is not loaded, the search waits (D5).</li>
 *     <li>First come, first served (N13-3): it keeps the tank it recognized ({@link #getKeptTank()})
 *     while that rectangle is valid, even when a later change also puts it in another tank's border,
 *     and it remembers that tank while it is invalid. The valves of its tank become its own
 *     ({@link TankValveObjectEntity#getOwner()}). The kept tank is saved and synced to clients.</li>
 *     <li>The regions its recognized tank spans are kept loaded together (N15-6,
 *     {@link TankRegionsLevelData}).</li>
 *     <li>One fluid type and amount, saved with the world ({@link TankStorage}, 12-7). A broken
 *     wall deactivates the tank and keeps the fluid (5-9); a rebuilt tank smaller than the stored
 *     amount loses the excess (N11-2); breaking the controller loses the fluid with this entity
 *     (5-10). The capacity is not saved: it comes from the tank recognized after loading.</li>
 * </ul>
 *
 * <h2>Clients</h2>
 * Clients get a view (recognized bounds, fluid, amount, capacity) through the object entity
 * content packet: structural changes at once, amount-only changes at most every
 * {@link #AMOUNT_SYNC_TICKS} ticks (coalesced). The controller window, the hover tooltip and the
 * fluid rendering read that view.
 */
public class TankControllerObjectEntity extends ObjectEntity {

    /** Object entity type; must stay the same for saved worlds. */
    public static final String TYPE = "tankcontroller";

    /**
     * Amount-only view changes are sent at most every this many ticks, coalesced (the latest amount
     * goes out). Structural changes (recognition, bounds, fluid, capacity) are sent at once. A
     * technical sync rate, not a design value.
     */
    static final int AMOUNT_SYNC_TICKS = 5;

    // Server state.
    private final TankStorage storage = new TankStorage();
    private boolean structureChanged = true;
    /** Whether a search (or a re-validation of the kept tank) has completed since creation or loading. */
    private boolean searchCompleted;
    private TankBounds regionsRegisteredFor;
    private boolean regionsRegistered;
    private int ticksSinceViewSync = AMOUNT_SYNC_TICKS;

    // The tank this controller keeps (N13-3): saved, and synced to clients for their placement checks.
    private TankBounds kept;

    // The view synced to clients (on the server: the last state sent).
    private boolean viewActive;
    private TankBounds viewBounds;
    private FluidType viewFluid;
    private int viewAmount;
    private int viewCapacity;

    public TankControllerObjectEntity(Level level, int tileX, int tileY) {
        super(level, TYPE, tileX, tileY);
    }

    @Override
    public void init() {
        super.init();
        TankRegistry.add(this);
    }

    @Override
    public void remove() {
        super.remove();
        unregister();
        // Its tank's cells may now belong to other tanks (also when only unloading: harmless).
        TankRegistry.onTankReleased(getLevel(), kept);
    }

    @Override
    public void dispose() {
        super.dispose();
        unregister();
    }

    private void unregister() {
        TankRegistry.remove(this);
        if (isClient()) {
            TankFluidRendering.onTankViewChanged(getLevel(), viewBounds, viewFluid, null, null);
        }
    }

    // ------------------------------------------------------------------ server

    /** Something near the controller changed: search the tank again on the next tick (5-1). */
    void markStructureChanged() {
        structureChanged = true;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (structureChanged) {
            searchTank();
        }
        updateView();
        updateRegionKeeping();
    }

    private void searchTank() {
        Level level = getLevel();
        if (!LevelTankCellLookup.isAreaLoaded(level, tileX, tileY)) {
            // The regions a recognized tank spans are kept loaded together (N15-6), so a tank is not
            // judged by half of it. The search area (the whole reach) can still cover regions beyond
            // the tank: the controller keeps its previous state and searches again once everything
            // within reach is loaded (retried every tick). The kept tank itself is checked as soon as
            // its own rectangle is loaded: the search would find it first anyway (N13-3).
            revalidateKeptTank(level);
            return;
        }
        structureChanged = false;
        searchCompleted = true;
        TankValidation tank = TankStructure.findTank(tileX, tileY, new LevelTankCellLookup(level)).getTank();
        // A valid smaller tank loses the excess at once, an invalid one keeps everything (N13-4).
        storage.applyStructure(tank);
        if (tank != null) {
            setKeptTank(tank.getBounds());
            claimValves(tank);
        }
        // No tank: the kept tank is still remembered (N13-3) and the storage is inactive (5-9).
    }

    /**
     * While the search area is not fully loaded: when the kept tank's rectangle is loaded and still
     * a valid tank for this controller, it is recognized again right away (the full search would
     * return it first, {@link TankStructure#findTank}). Otherwise nothing changes until the search.
     */
    private void revalidateKeptTank(Level level) {
        if (kept == null || !isLoaded(level, kept)) {
            return;
        }
        TankValidation tank = TankStructure.validate(kept, new LevelTankCellLookup(level), new GridPos(tileX, tileY));
        if (tank.isValid()) {
            structureChanged = false;
            searchCompleted = true;
            storage.applyStructure(tank);
            claimValves(tank);
        }
    }

    private static boolean isLoaded(Level level, TankBounds bounds) {
        for (int y = bounds.y; y <= bounds.getMaxY(); y++) {
            for (int x = bounds.x; x <= bounds.getMaxX(); x++) {
                if (level.isTileWithinBounds(x, y) && !level.regionManager.isTileLoaded(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void setKeptTank(TankBounds bounds) {
        if (!Objects.equals(bounds, kept)) {
            TankBounds old = kept;
            kept = bounds;
            TankRegistry.onTankReleased(getLevel(), old);
            markDirty();
        }
    }

    /** The valves of the recognized tank become this controller's (N13-3). */
    private void claimValves(TankValidation tank) {
        GridPos self = new GridPos(tileX, tileY);
        for (GridPos position : tank.getValves()) {
            TankValveObjectEntity valve = getLevel().entityManager.getObjectEntity(position.x, position.y,
                    TankValveObjectEntity.class);
            if (valve != null) {
                valve.setOwner(self);
            }
        }
    }

    /**
     * Keeps the regions of the recognized tank loaded together (N15-6). Nothing is registered or
     * cleared before the first completed search: a controller loaded from a save starts inactive,
     * and clearing its saved entry then would let the tank's other regions unload (for example an
     * offline owner's settlement on a dedicated server).
     */
    private void updateRegionKeeping() {
        if (!searchCompleted) {
            return;
        }
        TankBounds active = storage.isActive() ? kept : null;
        if (regionsRegistered && Objects.equals(active, regionsRegisteredFor)) {
            return;
        }
        TankRegionsLevelData data = TankRegionsLevelData.get(getLevel(), active != null);
        if (data != null) {
            data.setTank(tileX, tileY, active);
        }
        regionsRegisteredFor = active;
        regionsRegistered = true;
    }

    private void updateView() {
        boolean active = storage.isActive();
        TankBounds bounds = active ? kept : null;
        if (ticksSinceViewSync < AMOUNT_SYNC_TICKS) {
            ticksSinceViewSync++;
        }
        boolean structural = active != viewActive || !Objects.equals(bounds, viewBounds) || storage.getFluid() != viewFluid
                || storage.getCapacity() != viewCapacity;
        boolean amountDue = storage.getAmount() != viewAmount && ticksSinceViewSync >= AMOUNT_SYNC_TICKS;
        if (structural || amountDue) {
            viewActive = active;
            viewBounds = bounds;
            viewFluid = storage.getFluid();
            viewAmount = storage.getAmount();
            viewCapacity = storage.getCapacity();
            ticksSinceViewSync = 0;
            markDirty();
        }
    }

    /** The tank's fluid. Only meaningful on the server; valves pass fluid into it. */
    public TankStorage getStorage() {
        return storage;
    }

    /**
     * The tank this controller keeps (N13-3): the last tank it recognized, also while that tank is
     * invalid, or {@code null}. Both sides (synced to clients).
     */
    public TankBounds getKeptTank() {
        return kept;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        if (storage.getFluid() != null) {
            save.addEnum("fluid", storage.getFluid());
            save.addInt("amount", storage.getAmount());
        }
        if (kept != null) {
            save.addInt("tankX", kept.x);
            save.addInt("tankY", kept.y);
            save.addInt("tankWidth", kept.outerWidth);
            save.addInt("tankHeight", kept.outerHeight);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        FluidType fluid = save.getEnum(FluidType.class, "fluid", null, false);
        int amount = save.getInt("amount", 0, false);
        if (fluid != null && amount > 0) {
            storage.setContents(fluid, amount);
        }
        if (save.hasLoadDataByName("tankX")) {
            kept = new TankBounds(save.getInt("tankX", 0, false), save.getInt("tankY", 0, false),
                    save.getInt("tankWidth", 0, false), save.getInt("tankHeight", 0, false));
        }
        structureChanged = true;
    }

    // ------------------------------------------------------------------ sync

    @Override
    public void setupContentPacket(PacketWriter writer) {
        super.setupContentPacket(writer);
        writer.putNextBoolean(viewActive);
        writer.putNextBoolean(viewBounds != null);
        if (viewBounds != null) {
            writer.putNextInt(viewBounds.x);
            writer.putNextInt(viewBounds.y);
            writer.putNextInt(viewBounds.outerWidth);
            writer.putNextInt(viewBounds.outerHeight);
        }
        writer.putNextByte((byte) (viewFluid == null ? -1 : viewFluid.ordinal()));
        writer.putNextInt(viewAmount);
        writer.putNextInt(viewCapacity);
        writer.putNextBoolean(kept != null);
        if (kept != null) {
            writer.putNextInt(kept.x);
            writer.putNextInt(kept.y);
            writer.putNextInt(kept.outerWidth);
            writer.putNextInt(kept.outerHeight);
        }
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        TankBounds oldBounds = viewBounds;
        FluidType oldFluid = viewFluid;
        viewActive = reader.getNextBoolean();
        if (reader.getNextBoolean()) {
            int x = reader.getNextInt();
            int y = reader.getNextInt();
            int width = reader.getNextInt();
            int height = reader.getNextInt();
            viewBounds = new TankBounds(x, y, width, height);
        } else {
            viewBounds = null;
        }
        int fluidIndex = reader.getNextByte();
        viewFluid = fluidIndex >= 0 && fluidIndex < FluidType.values().length ? FluidType.values()[fluidIndex] : null;
        viewAmount = reader.getNextInt();
        viewCapacity = reader.getNextInt();
        if (reader.getNextBoolean()) {
            int x = reader.getNextInt();
            int y = reader.getNextInt();
            int width = reader.getNextInt();
            int height = reader.getNextInt();
            kept = new TankBounds(x, y, width, height);
        } else {
            kept = null;
        }
        if (isClient()) {
            TankFluidRendering.onTankViewChanged(getLevel(), oldBounds, oldFluid, viewBounds, viewFluid);
        }
    }

    // ------------------------------------------------------------------ view (both sides)

    /** Whether a tank is recognized around the controller. */
    public boolean isActive() {
        return viewActive;
    }

    /** The recognized tank (border included), or {@code null} while no tank is recognized. */
    public TankBounds getTankBounds() {
        return viewBounds;
    }

    /** The stored fluid, or {@code null} when empty (the synced view; on the server the live value). */
    public FluidType getFluid() {
        return isServer() ? storage.getFluid() : viewFluid;
    }

    public int getAmount() {
        return viewAmount;
    }

    public int getCapacity() {
        return viewCapacity;
    }

    /** {@code <fluid name> <current>/<max>} (6-2, 6-9). */
    public String getStatusText() {
        return TankStatusText.format(FluidNames.displayName(viewFluid), viewAmount, viewCapacity);
    }

}
