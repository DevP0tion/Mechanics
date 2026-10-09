package devp0tion.mechanics.pipe;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.LiquidTileSource;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.Pump;
import devp0tion.mechanics.core.PumpForm;
import devp0tion.mechanics.core.PumpResult;
import devp0tion.mechanics.core.PumpStatusText;
import devp0tion.mechanics.core.PumpTier;
import devp0tion.mechanics.tank.FluidNames;
import necesse.engine.localization.Localization;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.InventoryObjectEntity;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.Region;

import java.util.Objects;

/**
 * A pump's state: the core {@link Pump} in the level's pipe grid, its form, its liquid tile source
 * and its fuel slot.
 *
 * <ul>
 *     <li>Direction and form (N36): the output side is the object's rotation, the direction the
 *     player faced when placing it (N36-10); it is not saved, the rotation is. The form is saved
 *     ({@code form}); a pump without one is a ground pump (N36-32, N36-34, N36-35). The keys
 *     {@code sources} and {@code tile} of older saves, and their link flags toward sides a pump no
 *     longer links, are not read specially (N36-34).</li>
 *     <li>Server: a new pump registers in its first tick, not when its entity is made: the item
 *     path sets the rotation and the form after that (N36-10, N36-11). It starts a network of its
 *     own (N18-2). A ground pump always has its liquid tile source, also on land, where it gives
 *     nothing until the tile is liquid (N36-15, N36-21, N36-28); a valve pump has none (N36-20). A
 *     loaded pump restores its link flags, burn time, the fluid in the pump and the liquid tile
 *     source's left-over units. While its region loads, the engine's fresh entity registers
 *     nothing: the saved entity replaces it, or the region's loaded event registers it (A1).</li>
 *     <li>Every tick it compares the object's rotation with its output side: a cheat that turns a
 *     placed pump (the debug object tool, presets) turns its output too (N36-36,
 *     {@link PipeGrid#setPumpDirection}).</li>
 *     <li>It holds data only (N22-5): the pipe system's systems run the pump every tick
 *     ({@link PipeSystem#tick}); pipes that break are removed there without a drop (N12-5).</li>
 *     <li>The liquid tile source's 5x5 judgment (N20-7) is made when a new pump registers, with the
 *     cells loaded then, and is saved with the pump. A save without one (older formats are not read,
 *     N28-19) is judged when the pump registers, as when it is placed; so is a pump without an install
 *     number given one then (N28-17).</li>
 *     <li>Wire (11-3, N11-3): from tier 2 up, a wire signal on its tile switches it off.</li>
 *     <li>Manual pump: a click is one cycle, applied only on the server in the next tick of the
 *     systems (N22-5), at most every 20 ticks per pump however many players click (N3-2, N3-3,
 *     N31-3).</li>
 *     <li>Fuel (11-7, 11-8): the log-fueled pumps burn any log from one slot. Logs get in both ways
 *     (N31-4): the pump window ({@link PumpContainer}) with the one fuel slot, and a right click on
 *     the pump while holding logs that can go in; otherwise the right click opens the window
 *     ({@code PumpObject.interact}, N31-11).</li>
 *     <li>State (N36-44): what the last cycle did, and why it stopped for want of a source or a
 *     destination (N36-56), shown with the output direction and the form in the pump window and in
 *     the tooltip over any pump ({@link #onMouseHover}, N36-57, N36-60; words:
 *     {@link PumpStatusText}). The engine reports every cycle that ran ({@link PipeSystem}); a cycle
 *     not due yet keeps the last state, and a wire signal sets "switched off" at once, no cycle running
 *     then. Not saved: "대기 중" until the first cycle (N36-67).</li>
 *     <li>Clients get the link flags (drawn as cut faces, N16-4), the form, the state (only when
 *     it changed) and the slot (vanilla inventory sync); the fluid stays on the server.</li>
 * </ul>
 */
public class PumpObjectEntity extends InventoryObjectEntity {

    private final Pump pump;
    private LiquidTileSource tileSource;
    /** The form (N36-6): set by the item it was placed with (N36-11), saved; ground without one (N36-32). */
    private PumpForm form = PumpForm.GROUND;
    private boolean registered;
    private boolean deferred;
    /** A new pump waiting for its first tick to register (N36-10, N36-11). */
    private boolean pendingRegister;
    private boolean loadedFromSave;
    private boolean unloading;
    /** The object this entity was created for ({@link PipeSystem#isReplacedEntity}). */
    private int objectID = -1;
    private int links = LinkFlags.ALL_OPEN;

    // The state (N36-44): set on the server from the cycles, synced to clients; not saved, so a new or
    // loaded pump reads "대기 중" until its first cycle (N36-67).
    private PumpResult.Status status = PumpResult.Status.WAITING;
    private PumpResult.Detail detail = PumpResult.Detail.NONE;
    /** The fluid the last cycle moved ({@link PumpResult.Status#PUMPED}), else {@code null}. */
    private FluidType stateFluid;

    // Saved state waiting for init.
    private FluidType savedBufferFluid;
    private int savedBuffer;
    /** The liquid tile source's saved 5x5 judgment (N20-7); {@code null} when none was saved. */
    private Boolean savedJudgment;

    public PumpObjectEntity(Level level, int tileX, int tileY, PumpTier tier) {
        super(level, tileX, tileY, 1);
        this.pump = new Pump(tier);
    }

    public PumpTier getTier() {
        return pump.getTier();
    }

    /** The core pump (server). */
    public Pump getPump() {
        return pump;
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
        if (!loadedFromSave) {
            // A new pump: the engine sets its rotation, and the item its form, after the entity is
            // made (N36-10, N36-11). It registers in its first tick, before the pipe system's tick.
            pendingRegister = true;
            return;
        }
        register();
    }

    /** Registers a pump whose fresh entity no saved one replaced while its region loaded. */
    void registerIfDeferred() {
        if (deferred && !removed()) {
            deferred = false;
            register();
        }
    }

    /**
     * Adds the pump to the grid, once: from {@link #init} for a loaded pump, from the first tick for a
     * new one, or from the region's loaded event ({@link #registerIfDeferred}).
     */
    private void register() {
        if (registered) {
            return;
        }
        PipeSystem system = PipeSystem.get(getLevel());
        if (system == null) {
            return;
        }
        PipeGrid grid = system.getGrid();
        pump.setFuelSupply(this::consumeLog);
        updateWire();
        pump.setDirection(facing());
        pump.setForm(form);
        try {
            if (form == PumpForm.GROUND) {
                tileSource = newTileSource(loadedFromSave);
            }
            if (loadedFromSave) {
                pump.setLinks(links);
                grid.loadPump(tileX, tileY, pump);
            } else {
                grid.placePump(tileX, tileY, pump);
            }
            registered = true;
            links = pump.getLinks();
        } catch (IllegalStateException e) {
            System.err.println("Mechanics: pump at " + tileX + "," + tileY + " not registered: " + e.getMessage());
        }
    }

    /**
     * A ground pump's liquid tile source, also on land: it gives nothing until the tile is liquid,
     * and its area is judged again once it is (N36-15, N36-21, N36-28). {@code restore}: with the
     * saved left-over units and judgment; without a judgment it is judged now, with the cells loaded
     * now, as when the pump is placed (N20-7, N28-19).
     */
    private LiquidTileSource newTileSource(boolean restore) {
        LiquidTileSource source = new LiquidTileSource(new LevelLiquidTileLookup(getLevel()), tileX, tileY);
        if (restore) {
            source.setBuffered(savedBufferFluid, savedBufferFluid == null ? 0 : savedBuffer);
            source.setJudgment(savedJudgment);
        }
        if (source.getJudgment() == null) {
            source.judgeArea();
        }
        pump.setTileSource(source);
        return source;
    }

    /** The output side: the object's rotation (N36-10), 0 north to 3 west as {@link Direction}. */
    private Direction facing() {
        return Direction.values()[getLevel().getObjectRotation(tileX, tileY) & 3];
    }

    /** The pump's form (N36-6). */
    public PumpForm getForm() {
        return form;
    }

    /**
     * Sets the form. The item path sets it right after placing the object, before the new pump
     * registers in its first tick (N36-11). A placed pump keeps its form (N36-7); should it change
     * anyway, the grid follows ({@link PipeGrid#setPumpForm}).
     */
    public void setForm(PumpForm form) {
        Objects.requireNonNull(form, "form");
        if (this.form == form) {
            return;
        }
        this.form = form;
        // Clients draw the form and show it in the window and the tooltip (N36-39, N36-44).
        markDirty();
        if (registered) {
            if (form == PumpForm.GROUND && tileSource == null) {
                tileSource = newTileSource(false);
            }
            PipeSystem system = PipeSystem.getIfExists(getLevel());
            if (system != null) {
                system.getGrid().setPumpForm(tileX, tileY, form);
            }
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
        if (system != null && registered && system.getGrid().getPump(tileX, tileY) == pump) {
            // Either way it stops (N14-3); its state is saved here.
            if (unloading || PipeSystem.isReplacedEntity(this, objectID)) {
                // Its region unloaded, or only this entity is replaced: not a structure change (N28-1).
                system.getGrid().unloadPump(tileX, tileY);
            } else {
                system.getGrid().removePump(tileX, tileY);
            }
        }
        registered = false;
    }

    @Override
    public void serverTick() {
        // The inventory's own sync: the pump runs in the pipe system's systems (N22-5).
        super.serverTick();
        if (pendingRegister && !PipeSystem.isRegionLoading(this)) {
            // A new pump, its rotation and form set by now (N36-10, N36-11). Entities tick before the
            // pipe system, so it runs in this tick's systems already.
            pendingRegister = false;
            register();
        }
        if (registered) {
            Direction facing = facing();
            if (facing != pump.getDirection()) {
                // Only a cheat turns a placed pump (N36-36): its output follows the rotation.
                PipeSystem system = PipeSystem.getIfExists(getLevel());
                if (system != null) {
                    system.getGrid().setPumpDirection(tileX, tileY, facing);
                }
            }
        }
    }

    /**
     * A click on the manual pump (11-3): server only, queued for the next tick of the systems
     * (N22-5), one cycle at most every 20 ticks (N3-3).
     */
    public void click() {
        if (!isServer() || !registered || pump.getTier().getPower() != PumpTier.Power.HAND_CLICK) {
            return;
        }
        PipeSystem system = PipeSystem.getIfExists(getLevel());
        if (system != null) {
            system.queueClick(tileX, tileY);
        }
    }

    /**
     * Reads the wire signal on the pump's tile: a signal switches tier 2 and up off (11-3, N11-3). No
     * cycle runs while it is off, so the state says so here (N36-44); the first cycle after the signal
     * ends replaces it.
     */
    public void updateWire() {
        if (pump.getTier().isWireControllable()) {
            boolean off = getLevel().wireManager.isWireActiveAny(tileX, tileY);
            pump.setEnabled(!off);
            if (off) {
                setState(PumpResult.Status.DISABLED, PumpResult.Detail.NONE, null);
            }
        }
    }

    /**
     * The state of a cycle that ran (server; from {@link PipeSystem}, and "switched off" from
     * {@link #updateWire}): synced to clients only when it changed. A cycle that was not due yet
     * ({@link PumpResult.Status#WAITING}: the click cooldown) keeps the last state.
     */
    void setState(PumpResult.Status status, PumpResult.Detail detail, FluidType fluid) {
        if (!isServer() || status == PumpResult.Status.WAITING) {
            return;
        }
        if (status != this.status || detail != this.detail || fluid != stateFluid) {
            this.status = status;
            this.detail = detail;
            this.stateFluid = fluid;
            markDirty();
        }
    }

    /** The output line: the output direction and the form (N36-44), in the game's language; both sides. */
    public String getOutputText() {
        return PumpStatusText.outputLine(facing(), form, key -> Localization.translate("ui", key));
    }

    /** The state line (N36-44, N36-56, N36-67), in the game's language; both sides. */
    public String getStateText() {
        return PumpStatusText.stateLine(status, detail, form, FluidNames.displayName(stateFluid),
                key -> Localization.translate("ui", key));
    }

    /**
     * N36-57: the cursor over the pump shows the window's lines, whatever the player holds, on every
     * pump (the manual pump has no window). The wrench's own tooltip stacks with it (N36-60).
     */
    @Override
    public void onMouseHover(PlayerMob perspective, boolean debug) {
        super.onMouseHover(perspective, debug);
        if (isClient()) {
            GameTooltipManager.addTooltip(new StringTooltips(getOutputText(), getStateText()), TooltipLocation.INTERACT_FOCUS);
        }
    }

    private boolean consumeLog() {
        InventoryItem item = inventory.getItem(0);
        if (item == null || !isLog(item)) {
            return false;
        }
        inventory.setAmount(0, item.getAmount() - 1);
        return true;
    }

    private static boolean isLog(InventoryItem item) {
        return item.item.isGlobalIngredient(GlobalIngredientRegistry.getGlobalIngredient(PumpTier.LOG_FUEL_INGREDIENT));
    }

    /** Only logs go into the fuel slot (11-7, 11-8); the manual pump takes nothing (N1-2). */
    @Override
    public boolean isItemValid(int slot, InventoryItem item) {
        return pump.getTier().usesLogFuel() && (item == null || isLog(item));
    }

    // The fuel slot is not a storage: no settlement storage, nearby crafting or quick stacking.

    @Override
    public boolean canUseForNearbyCrafting() {
        return false;
    }

    @Override
    public boolean canQuickStackInventory() {
        return false;
    }

    @Override
    public boolean canRestockInventory() {
        return false;
    }

    @Override
    public boolean canSortInventory() {
        return false;
    }

    @Override
    public boolean canSetInventoryName() {
        return false;
    }

    @Override
    public InventoryRange getSettlementStorage() {
        return null;
    }

    /** The link flags (both sides: clients get them synced). */
    public int getLinks() {
        return links;
    }

    /** Called when the grid changed the flags: sync them to clients. */
    void syncLinks() {
        if (pump.getLinks() != links) {
            links = pump.getLinks();
            markDirty();
        }
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        boolean live = registered;
        save.addInt("links", live ? pump.getLinks() : links);
        // N36-6: the form; the direction is the object's rotation, saved by the engine (N36-10).
        save.addEnum("form", form);
        save.addInt("burn", pump.getBurnTicksLeft());
        if (pump.getInstallNumber() >= 0) {
            // N28-17: the level-wide placement order.
            save.addLong("install", pump.getInstallNumber());
        }
        if (pump.getLastPushedFluid() != null) {
            save.addEnum("pushed", pump.getLastPushedFluid());
        }
        if (pump.getFluid() != null) {
            save.addEnum("fluid", pump.getFluid());
            save.addInt("amount", pump.getAmount());
        }
        FluidType bufferFluid = tileSource != null ? tileSource.getBufferedFluid() : savedBufferFluid;
        int buffer = tileSource != null ? tileSource.getBuffered() : savedBuffer;
        if (bufferFluid != null && buffer > 0) {
            save.addEnum("bufferFluid", bufferFluid);
            save.addInt("buffer", buffer);
        }
        Boolean judgment = tileSource != null ? tileSource.getJudgment() : savedJudgment;
        if (judgment != null) {
            save.addBoolean("tileInfinite", judgment);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        loadedFromSave = true;
        links = LinkFlags.sanitize(save.getInt("links", LinkFlags.ALL_OPEN, false));
        // Without a form, a ground pump (N36-32, N36-34, N36-35).
        form = save.getEnum(PumpForm.class, "form", PumpForm.GROUND, false);
        pump.setBurnTicksLeft(Math.max(0, save.getInt("burn", 0, false)));
        // A save without an install number gets the next one when the pump registers (N28-17, N28-19).
        pump.setInstallNumber(save.getLong("install", -1L, false));
        pump.setLastPushedFluid(save.getEnum(FluidType.class, "pushed", null, false));
        FluidType fluid = save.getEnum(FluidType.class, "fluid", null, false);
        int amount = save.getInt("amount", 0, false);
        if (fluid != null && amount > 0) {
            pump.setContents(fluid, amount);
        }
        savedBufferFluid = save.getEnum(FluidType.class, "bufferFluid", null, false);
        savedBuffer = Math.max(0, save.getInt("buffer", 0, false));
        savedJudgment = save.hasLoadDataByName("tileInfinite") ? save.getBoolean("tileInfinite", false, false) : null;
    }

    @Override
    public void setupContentPacket(PacketWriter writer) {
        super.setupContentPacket(writer);
        writer.putNextByteUnsigned(registered ? pump.getLinks() : links);
        // The form (drawing, N36-39) and the state (N36-44, N36-57).
        writer.putNextByteUnsigned(form.ordinal());
        writer.putNextByteUnsigned(status.ordinal());
        writer.putNextByteUnsigned(detail.ordinal());
        writer.putNextByte((byte) (stateFluid == null ? -1 : stateFluid.ordinal()));
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        links = LinkFlags.sanitize(reader.getNextByteUnsigned());
        // Clients only: the server's form is in the grid (setForm).
        form = valueAt(PumpForm.values(), reader.getNextByteUnsigned(), PumpForm.GROUND);
        status = valueAt(PumpResult.Status.values(), reader.getNextByteUnsigned(), PumpResult.Status.WAITING);
        detail = valueAt(PumpResult.Detail.values(), reader.getNextByteUnsigned(), PumpResult.Detail.NONE);
        stateFluid = valueAt(FluidType.values(), reader.getNextByte(), null);
    }

    /** The value at a synced ordinal, or {@code fallback} for one out of range. */
    private static <T> T valueAt(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

}
