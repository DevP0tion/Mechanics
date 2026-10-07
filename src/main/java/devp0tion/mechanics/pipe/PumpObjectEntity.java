package devp0tion.mechanics.pipe;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.LiquidTileSource;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.Pump;
import devp0tion.mechanics.core.PumpResult;
import devp0tion.mechanics.core.PumpTier;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.InventoryObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A pump's state: the core {@link Pump} in the level's pipe grid, its liquid tile source and its
 * fuel slot.
 *
 * <ul>
 *     <li>Server: a new pump connects its sources (the liquid tile under it when it stands on
 *     liquid, then the valves next to it whose links are open, N17-1) and starts a network of its
 *     own (N18-2); a loaded one restores its sources in their order (N19-1), link flags, burn time,
 *     the fluid in the pump and the liquid tile source's left-over units. It ticks the pump every
 *     game tick; pipes that break are removed without a drop (N12-5).</li>
 *     <li>The liquid tile source's 5x5 judgment (N20-7) is made when a new pump registers, with the
 *     cells loaded then, and is saved with the pump. TODO(confirm): a pump saved before the judgment
 *     was stored is judged at its first tick after loading, with the cells loaded then (N20-7).</li>
 *     <li>Wire (11-3, N11-3): from tier 2 up, a wire signal on its tile switches it off.</li>
 *     <li>Manual pump: a click is one cycle, applied only on the server, at most every 20 ticks per
 *     pump (N3-2, N3-3; see the per-player TODO(design) in {@link Pump}).</li>
 *     <li>Fuel (11-7, 11-8): the log-fueled pumps burn any log from one slot. TODO(confirm): how
 *     logs get into the pump is not decided; a vanilla-style container with one fuel slot is the
 *     placeholder (the vanilla object inventory window).</li>
 *     <li>Clients get the link flags (drawn as cut faces, N16-4) and the slot (vanilla inventory
 *     sync); the fluid stays on the server.</li>
 * </ul>
 */
public class PumpObjectEntity extends InventoryObjectEntity {

    private final Pump pump;
    private LiquidTileSource tileSource;
    private boolean registered;
    private boolean loadedFromSave;
    private int links = LinkFlags.ALL_OPEN;

    // Saved state waiting for init.
    private List<Pump.SourceSlot> savedSlots = new ArrayList<>();
    private boolean savedHasTile;
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
        PipeSystem system = PipeSystem.get(getLevel());
        if (system == null) {
            return;
        }
        PipeGrid grid = system.getGrid();
        pump.setFuelSupply(this::consumeLog);
        updateWire();
        LevelLiquidTileLookup lookup = new LevelLiquidTileLookup(getLevel());
        try {
            if (loadedFromSave) {
                if (savedHasTile) {
                    tileSource = new LiquidTileSource(lookup, tileX, tileY);
                    tileSource.setBuffered(savedBufferFluid, savedBufferFluid == null ? 0 : savedBuffer);
                    tileSource.setJudgment(savedJudgment);
                    pump.setTileSource(tileSource);
                }
                pump.setLinks(links);
                pump.setSourceSlots(savedSlots);
                grid.loadPump(tileX, tileY, pump);
            } else {
                if (LevelLiquidTileLookup.fluidAt(getLevel(), tileX, tileY) != null) {
                    tileSource = new LiquidTileSource(lookup, tileX, tileY);
                    // Judged when the pump is placed, with the cells loaded now (N20-7).
                    tileSource.judgeArea();
                    pump.setTileSource(tileSource);
                }
                FluidType tileFluid = tileSource == null ? null : tileSource.getTileFluid();
                if (grid.checkPumpPlacement(tileX, tileY, tileFluid) == PipeGrid.Check.OK) {
                    grid.placePump(tileX, tileY, pump);
                } else {
                    // The sources changed after the placement check (N17-1): connect only the tile,
                    // with the sides toward the valves cut, so links and sources agree.
                    List<Pump.SourceSlot> slots = new ArrayList<>();
                    if (tileSource != null) {
                        slots.add(Pump.SourceSlot.TILE);
                    }
                    int cut = LinkFlags.ALL_OPEN;
                    for (Direction d : Direction.values()) {
                        if (grid.getValve(tileX + d.dx, tileY + d.dy) != null) {
                            cut = LinkFlags.withSide(cut, d, false);
                        }
                    }
                    pump.setLinks(cut);
                    pump.setSourceSlots(slots);
                    grid.loadPump(tileX, tileY, pump);
                }
            }
            registered = true;
            links = pump.getLinks();
        } catch (IllegalStateException e) {
            System.err.println("Mechanics: pump at " + tileX + "," + tileY + " not registered: " + e.getMessage());
        }
    }

    @Override
    public void remove() {
        super.remove();
        PipeSystem system = PipeSystem.getIfExists(getLevel());
        if (system != null && registered && system.getGrid().getPump(tileX, tileY) == pump) {
            // Picked up or its region unloaded: either way it stops (N14-3); its state is saved here.
            system.getGrid().removePump(tileX, tileY);
        }
        registered = false;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (!registered) {
            return;
        }
        if (tileSource != null && tileSource.getJudgment() == null) {
            // Not judged yet: a pump saved before the judgment was stored (TODO(confirm) in the class
            // comment), or one whose tile was not loaded when it registered. Judged with the loaded cells.
            tileSource.judgeArea();
        }
        PumpResult result = pump.tick();
        if (!result.getBroken().isEmpty()) {
            PipeSystem system = PipeSystem.getIfExists(getLevel());
            if (system != null) {
                system.applyResult(result);
            }
        }
    }

    /** A click on the manual pump (11-3): server only, one cycle at most every 20 ticks (N3-3). */
    public PumpResult click() {
        if (!isServer() || !registered || pump.getTier().getPower() != PumpTier.Power.HAND_CLICK) {
            return null;
        }
        PumpResult result = pump.click();
        if (!result.getBroken().isEmpty()) {
            PipeSystem system = PipeSystem.getIfExists(getLevel());
            if (system != null) {
                system.applyResult(result);
            }
        }
        return result;
    }

    /** Reads the wire signal on the pump's tile: a signal switches tier 2 and up off (11-3, N11-3). */
    public void updateWire() {
        if (pump.getTier().isWireControllable()) {
            pump.setEnabled(!getLevel().wireManager.isWireActiveAny(tileX, tileY));
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
        List<Pump.SourceSlot> slots = live ? pump.getSourceSlots() : savedSlots;
        int[] codes = new int[slots.size()];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = slots.get(i).code();
        }
        save.addIntArray("sources", codes);
        save.addInt("burn", pump.getBurnTicksLeft());
        if (pump.getLastPushedFluid() != null) {
            save.addEnum("pushed", pump.getLastPushedFluid());
        }
        if (pump.getFluid() != null) {
            save.addEnum("fluid", pump.getFluid());
            save.addInt("amount", pump.getAmount());
        }
        boolean hasTile = live ? tileSource != null : savedHasTile;
        save.addBoolean("tile", hasTile);
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
        savedSlots = new ArrayList<>();
        for (int code : save.getIntArray("sources", new int[0], false)) {
            Pump.SourceSlot slot = Pump.SourceSlot.fromCode(code);
            if (slot != null) {
                savedSlots.add(slot);
            }
        }
        pump.setBurnTicksLeft(Math.max(0, save.getInt("burn", 0, false)));
        pump.setLastPushedFluid(save.getEnum(FluidType.class, "pushed", null, false));
        FluidType fluid = save.getEnum(FluidType.class, "fluid", null, false);
        int amount = save.getInt("amount", 0, false);
        if (fluid != null && amount > 0) {
            pump.setContents(fluid, amount);
        }
        savedHasTile = save.getBoolean("tile", false, false);
        savedBufferFluid = save.getEnum(FluidType.class, "bufferFluid", null, false);
        savedBuffer = Math.max(0, save.getInt("buffer", 0, false));
        savedJudgment = save.hasLoadDataByName("tileInfinite") ? save.getBoolean("tileInfinite", false, false) : null;
    }

    @Override
    public void setupContentPacket(PacketWriter writer) {
        super.setupContentPacket(writer);
        writer.putNextByteUnsigned(registered ? pump.getLinks() : links);
    }

    @Override
    public void applyContentPacket(PacketReader reader) {
        super.applyContentPacket(reader);
        links = LinkFlags.sanitize(reader.getNextByteUnsigned());
    }

}
