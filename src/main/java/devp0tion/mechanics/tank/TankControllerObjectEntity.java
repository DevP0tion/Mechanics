package devp0tion.mechanics.tank;

import devp0tion.mechanics.client.TankFluidRendering;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.GridPos;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.TankFloorRecord;
import devp0tion.mechanics.core.TankJudgment;
import devp0tion.mechanics.core.TankStatusText;
import devp0tion.mechanics.core.TankStorage;
import devp0tion.mechanics.core.TankStructure;
import devp0tion.mechanics.core.TankValidation;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

import java.util.ArrayList;
import java.util.List;
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
 *     <li>Its judgment is saved: the kept tank (the range), whether it is active, its capacity and
 *     its lowest tier (N20-7, N22-7, N29-1). A controller loaded while part of its tank is not loaded
 *     keeps the saved judgment, so an active tank works from the moment the controller loads; a
 *     change in a loaded cell judges the tank again with the loaded cells only (N21-3), the unloaded
 *     border cells counting with the saved lowest tier (N29-1). The regions a tank spans are not kept
 *     loaded together (N20-8). With no valid kept tank, a rectangle with all its cells loaded is
 *     recognized at once; one touching unloaded cells is tried again when cells of the search area
 *     load (N29-2).</li>
 *     <li>Natural growth found inside its active tank when it judges the tank is broken without
 *     drops ({@link TankInteriorPlacement#breakNaturalGrowth}, N23-4, N26-3, N29-5).</li>
 *     <li>It records the interior floor tiles when it recognizes its tank, and at a later judgment
 *     of the active tank sets the floors that grass or snow spread onto back to the recorded ones
 *     ({@link TankFloorRecord}, {@link TankInteriorPlacement#revertFloor}, N29-9). The record is
 *     saved with the judgment.</li>
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
    /** A search waits for cells to load (tried again when cells of the search area load, N29-2). */
    private boolean searchWaiting;
    /** The search area's loaded tiles at the last judgment (technical, {@link TankStructure#loadedSignature}). */
    private long searchSignature;
    /** The lowest tier of the last valid judgment (N29-1): saved, and used while border cells are unloaded. */
    private MineralTier judgedTier;
    /** The interior floors of the active tank, recorded when it was recognized (N29-9); saved. */
    private TankFloorRecord floorRecord;
    /** The last judgment found its tank contested by another controller's (N29-8, {@link #isContested}). */
    private boolean contested;
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
        } else if (searchWaiting
                && TankStructure.loadedSignature(tileX, tileY, new LevelTankCellLookup(getLevel())) != searchSignature) {
            judge(TankJudgment.Mode.SEARCH);
        }
        updateView();
    }

    /**
     * Whether the last judgment left the controller without a tank because another controller's
     * tank contests a valve (N29-8): it judges again after a change within reach of that tank too
     * ({@link TankRegistry#onTileChanged}).
     */
    boolean isContested() {
        return contested;
    }

    /** Judges the tank ({@link TankJudgment}) and applies the result. */
    private void judge(TankJudgment.Mode mode) {
        Level level = getLevel();
        boolean wasActive = storage.isActive();
        TankJudgment.Prior prior = wasActive ? TankJudgment.Prior.ACTIVE : TankJudgment.Prior.INACTIVE;
        LevelTankCellLookup lookup = new LevelTankCellLookup(level);
        TankJudgment.Result result = TankJudgment.judge(tileX, tileY, lookup, mode, prior, judgedTier);
        // The judgment sees the natural growth as broken already (N23-4). Breaking it is a change
        // within reach: the controllers nearby judge again.
        for (GridPos cell : result.getNaturalGrowth()) {
            TankInteriorPlacement.breakNaturalGrowth(level, cell.x, cell.y);
        }
        structureChanged = false;
        justLoaded = false;
        contested = result.getKind() == TankJudgment.Kind.CONTESTED;
        searchWaiting = !result.isSettled();
        if (searchWaiting) {
            searchSignature = TankStructure.loadedSignature(tileX, tileY, lookup);
        }
        if (result.appliesJudgment()) {
            TankValidation tank = result.getTank();
            // A valid smaller tank loses the excess at once, an invalid one keeps everything (N13-4).
            storage.applyStructure(tank);
            if (tank != null) {
                judgedTier = tank.getLowestTier();
                setKeptTank(tank.getBounds());
                claimValves(tank);
            }
            // No tank: the kept tank is still remembered (N13-3) and the storage is inactive (5-9).
        }
        judgeFloors(lookup, wasActive);
    }

    /**
     * N29-9: records the interior floors of a newly recognized tank; for a tank that stays active,
     * sets the loaded floors that grass or snow spread onto back to the record. Setting a floor is a
     * change within reach: the controllers nearby judge again (and find nothing more to set back).
     */
    private void judgeFloors(LevelTankCellLookup lookup, boolean wasActive) {
        List<TankFloorRecord.Revert> reverts = new ArrayList<>();
        floorRecord = TankFloorRecord.afterJudgment(floorRecord, storage.isActive() ? kept : null, wasActive, lookup, reverts);
        for (TankFloorRecord.Revert revert : reverts) {
            TankInteriorPlacement.revertFloor(getLevel(), revert.tileX, revert.tileY, revert.floor);
        }
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
        if (judgedTier != null) {
            save.addEnum("tier", judgedTier);
        }
        if (floorRecord != null) {
            saveFloorRecord(save);
        }
    }

    /**
     * N29-9: the recorded interior floors (of the kept tank), at most 5x5 cells: the floor tiles by
     * name once each ({@code floorTiles}) and per interior cell, in reading order, the index of its
     * floor there, or -1 for none recorded yet ({@code floorCells}). Names keep the record right
     * when the tile ids change with the game version or the mods.
     */
    private void saveFloorRecord(SaveData save) {
        int[] floors = floorRecord.getFloors();
        List<String> names = new ArrayList<>();
        int[] cells = new int[floors.length];
        for (int i = 0; i < floors.length; i++) {
            String name = floors[i] < 0 ? null : TileRegistry.getTileStringID(floors[i]);
            if (name == null) {
                cells[i] = -1;
                continue;
            }
            int index = names.indexOf(name);
            if (index < 0) {
                index = names.size();
                names.add(name);
            }
            cells[i] = index;
        }
        save.addStringArray("floorTiles", names.toArray(new String[0]));
        save.addIntArray("floorCells", cells);
    }

    /** The saved record of the kept tank's interior floors, or {@code null} (none saved, N29-9). */
    private static TankFloorRecord loadFloorRecord(LoadData save, TankBounds kept) {
        String[] names = save.getStringArray("floorTiles", null, false);
        int[] cells = save.getIntArray("floorCells", null, false);
        if (kept == null || names == null || cells == null) {
            return null;
        }
        int[] floors = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            // A name the game does not know any more (a removed mod's tile) is no record (-1).
            floors[i] = cells[i] < 0 || cells[i] >= names.length ? TankFloorRecord.UNKNOWN : TileRegistry.getTileID(names[cells[i]]);
        }
        return TankFloorRecord.restore(kept, floors);
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
        judgedTier = save.getEnum(MineralTier.class, "tier", null, false);
        if (judged) {
            storage.restoreJudgment(kept != null && save.getBoolean("active", false, false),
                    Math.max(0, save.getInt("capacity", 0, false)));
        }
        // N29-9: a judgment saved before the floor record existed has none: the next judgment of the
        // active tank records the floors and sets nothing back.
        floorRecord = storage.isActive() ? loadFloorRecord(save, kept) : null;
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

    /** {@code <fluid name> <current>/<max>} (6-2, 6-9); "비어 있음" / "Empty" when empty (N31-7). */
    public String getStatusText() {
        return TankStatusText.format(FluidNames.displayName(viewFluid), viewAmount, viewCapacity, FluidNames.emptyText());
    }

}
