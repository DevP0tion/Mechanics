package devp0tion.mechanics.tank;

import devp0tion.mechanics.client.TankFluidRendering;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankJudgment;
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
import necesse.level.maps.regionSystem.Region;

import java.util.Objects;

/**
 * The tank controller's state (2-1, 4-3): it recognizes the multiblock tank around it and holds the
 * tank's fluid (5-9).
 *
 * <h2>Server</h2>
 * <ul>
 *     <li>Judges its tank ({@link TankJudgment}) when it is created and again after any object or
 *     floor tile within reach changed ({@link TankRegistry#onTileChanged}, 5-1).</li>
 *     <li>First come, first served (N13-3): it keeps the tank it recognized ({@link #getKeptTank()})
 *     while that rectangle is valid, even when a later change also puts it in another tank's border,
 *     and it remembers that tank while it is invalid. The valves of its tank become its own
 *     ({@link TankValveObjectEntity#getOwner()}). The kept tank is saved and synced to clients.</li>
 *     <li>Its judgment is saved: the kept tank (the range), whether it is active and its capacity
 *     (N20-7, N22-7). A controller loaded while part of its tank is not loaded keeps the saved
 *     judgment, so an active tank works from the moment the controller loads; a change in a loaded
 *     cell judges the tank again with the loaded cells only (N21-3). The regions a tank spans are not
 *     kept loaded together (N20-8).</li>
 *     <li>Natural growth found inside its active tank when it judges the tank is broken without
 *     drops ({@link TankInteriorPlacement#breakNaturalGrowth}, N23-4, N26-3).</li>
 *     <li>One fluid type and amount, saved with the world ({@link TankStorage}, 12-7). A broken
 *     wall deactivates the tank and keeps the fluid (5-9); a rebuilt tank smaller than the stored
 *     amount loses the excess (N11-2); breaking the controller loses the fluid with this entity
 *     (5-10).</li>
 *     <li>While the controller is not loaded its tank is dormant (N21-2): an unloading controller
 *     releases its storage like a removed one ({@link #remove()}), so its valves are neither
 *     destinations nor sources until it loads again.</li>
 * </ul>
 *
 * <h2>Clients</h2>
 * Clients get a view (recognized bounds, fluid, amount, capacity) through the object entity
 * content packet: structural changes at once, amount-only changes at most every
 * {@link #AMOUNT_SYNC_TICKS} ticks (coalesced). The controller window, the hover tooltip and the
 * fluid rendering read that view. A loaded controller starts with the view of its saved judgment.
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
    /** A cell within reach changed (or the controller is new or loaded): judge on the next tick. */
    private boolean structureChanged = true;
    /** The next judgment is the first one after loading with a saved judgment (N22-7). */
    private boolean justLoaded;
    /** A search waits for the whole search area to load (tried every tick). */
    private boolean searchWaiting;
    /** The region is unloading: nothing about the tank changes for the others (N21-2). */
    private boolean unloading;
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
    public void onUnloading(Region region) {
        super.onUnloading(region);
        unloading = true;
    }

    @Override
    public void remove() {
        super.remove();
        unregister();
        // Nothing passes fluid into or out of this storage any more, also in the rest of this tick,
        // before its valves look their tank up again: a broken controller loses the fluid (5-10,
        // N19-2). Also right for any other removal: an unloading region saved this entity (and its
        // judgment) before, and its tank is dormant until it loads again (N21-2); an entity the engine
        // only replaces (region loading, placement) is one that no valve points at yet. Only this
        // storage changes: the pipe grid is untouched.
        storage.release();
        if (!unloading) {
            // Its tank's cells may now belong to other tanks. An unloading controller changes no
            // cell: the controllers nearby judge again only when a loaded cell changes (N20-7), and
            // its valves still count as its own while it is not loaded (TankStructure.effectiveValveOwner).
            TankRegistry.onTankReleased(getLevel(), kept);
        }
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

    /** Something near the controller changed: judge the tank again on the next tick (5-1). */
    void markStructureChanged() {
        structureChanged = true;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (structureChanged) {
            judge(justLoaded ? TankJudgment.Mode.LOAD : TankJudgment.Mode.CHANGE);
        } else if (searchWaiting) {
            judge(TankJudgment.Mode.SEARCH);
        }
        updateView();
    }

    /** Judges the tank ({@link TankJudgment}) and applies the result. */
    private void judge(TankJudgment.Mode mode) {
        Level level = getLevel();
        TankJudgment.Prior prior = storage.isActive() ? TankJudgment.Prior.ACTIVE : TankJudgment.Prior.INACTIVE;
        TankJudgment.Result result = TankJudgment.judge(tileX, tileY, new LevelTankCellLookup(level), mode, prior);
        // The judgment sees the natural growth as broken already (N23-4). Breaking it is a change
        // within reach: the controllers nearby judge again.
        for (GridPos cell : result.getNaturalGrowth()) {
            TankInteriorPlacement.breakNaturalGrowth(level, cell.x, cell.y);
        }
        structureChanged = false;
        justLoaded = false;
        searchWaiting = !result.isSettled();
        if (!result.appliesJudgment()) {
            return;
        }
        TankValidation tank = result.getTank();
        // A valid smaller tank loses the excess at once, an invalid one keeps everything (N13-4).
        storage.applyStructure(tank);
        if (tank != null) {
            setKeptTank(tank.getBounds());
            claimValves(tank);
        }
        // No tank: the kept tank is still remembered (N13-3) and the storage is inactive (5-9).
    }

    private void setKeptTank(TankBounds bounds) {
        if (!Objects.equals(bounds, kept)) {
            TankBounds old = kept;
            kept = bounds;
            TankRegistry.onTankReleased(getLevel(), old);
            markDirty();
        }
    }

    /** The valves of the recognized tank become this controller's (N13-3); loaded valves only. */
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
        // The judgment (N20-7, N22-7): the range above, the active state and the capacity.
        save.addBoolean("active", storage.isActive());
        save.addInt("capacity", storage.getCapacity());
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
        // The saved judgment applies at once (N22-7): an active tank works before the first tick. A
        // save without one (older formats are not read, N28-19) starts as a new controller: inactive,
        // judged as after a change.
        boolean judged = save.hasLoadDataByName("active");
        if (judged) {
            storage.restoreJudgment(kept != null && save.getBoolean("active", false, false),
                    Math.max(0, save.getInt("capacity", 0, false)));
        }
        // The view starts from the saved judgment: clients and the natural growth check of the
        // region loading now (its world time simulation, N20-1) see the tank at once.
        viewActive = storage.isActive();
        viewBounds = viewActive ? kept : null;
        viewFluid = storage.getFluid();
        viewAmount = storage.getAmount();
        viewCapacity = storage.getCapacity();
        structureChanged = true;
        justLoaded = judged;
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
