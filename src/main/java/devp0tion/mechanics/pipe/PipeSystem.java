package devp0tion.mechanics.pipe;

import devp0tion.mechanics.core.BlockedFaceSync;
import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.PipeLayer;
import devp0tion.mechanics.core.PipeNode;
import devp0tion.mechanics.core.PipeTierRules;
import devp0tion.mechanics.core.PumpResult;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import devp0tion.mechanics.tank.TankValveObjectEntity;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.LevelDataRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.ObjectEntitySave;
import necesse.entity.DrawOnMapEntity;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.entity.manager.TileEntityList;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.RegionLevelDataComponent;
import necesse.level.maps.regionSystem.Region;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pipe system of one server level (N7-1, N22-5): the level's pipe engine ({@link PipeGrid}), the
 * underground pipe holders, and the one place the engine's systems run.
 *
 * <ul>
 *     <li>Systems (N22-5): {@link #tick} (the level data tick, after the entity ticks) reads the
 *     valves' wire signals and tanks from the level, then runs the engine's systems in their fixed
 *     order ({@link PipeGrid#runTick}). Object entities hold data only: basic pipes, pumps and valves
 *     register their components from their object entities, structure changes reach the engine at
 *     once (N22-4), a manual pump's click is queued for the next tick.</li>
 *     <li>Underground pipe holders (N22-2, N24-1): one {@link UndergroundPipeHolder} per underground
 *     pipe of a loaded region, in this system's own entity list, saved in the region's object entity
 *     section ({@link HolderSavePatches}). Placed and removed with the layer object; created from the
 *     save entry when the region loads, else (older worlds) from the region's old pipe records, else
 *     from the old level mirror, else as a new pipe.</li>
 *     <li>Unloaded regions (N23-2): the engine keeps no mirror; the network summaries are saved with
 *     the level ({@code SUMMARY}), and next to them the structure change numbers of the regions they
 *     pass ({@code REGIONCHANGES}, N28-1; see {@link PipeGrid} for why the level file). The old
 *     mirror ({@code MIRROR}) is only read, to migrate.</li>
 *     <li>Clients get the underground pipes' link flags and the faces blocked by another fluid
 *     ({@link PipeGrid#getFluidBlockedSides}, N13-2) per region when the region is sent to them
 *     ({@link PipeSyncPatches}) and per tile when either changes ({@link PacketUndergroundPipes},
 *     N26-2); basic pipes, pumps and valves sync through their object entities.</li>
 * </ul>
 */
public class PipeSystem extends LevelData implements RegionLevelDataComponent, RegionLoadedListenerEntityComponent {

    /** Registry stringID and level data key; must stay the same for saved worlds. */
    public static final String KEY = "mechanicspipes";

    private final PipeGrid grid = new PipeGrid(PipeTierRules.TABLE);
    /** The underground pipe holders of the loaded regions (N22-2); created with the level. */
    private TileEntityList<UndergroundPipeHolder> holders;
    private boolean holderRemoved;
    /** Old worlds: underground pipe records of the region data ({@code PIPE}), until the region loads. */
    private final Map<Long, List<PipeRecord>> pendingRegions = new HashMap<>();
    /** Old worlds: the level mirror ({@code MIRROR}), read only to migrate pipes without other state. */
    private final Map<String, PipeRecord> legacyMirror = new HashMap<>();
    /** The underground pipes' faces blocked by another fluid as last sent to clients (N13-2). */
    private final BlockedFaceSync undergroundBlocked = new BlockedFaceSync(grid, PipeLayer.UNDERGROUND);
    /** The registered valves' object entities, by tile: read every tick before the systems run (N22-5). */
    private final Map<Long, TankValveObjectEntity> valveEntities = new LinkedHashMap<>();
    private boolean scanned;

    public PipeSystem() {
        grid.setListener(new PipeGrid.Listener() {
            @Override
            public void onLinksChanged(int tileX, int tileY, PipeGrid.Part part) {
                PipeSystem.this.onLinksChanged(tileX, tileY, part);
            }

            @Override
            public void onPipeFluidChanged(int tileX, int tileY, PipeLayer layer) {
                PipeSystem.this.onPipeFluidChanged(tileX, tileY, layer);
            }

            @Override
            public void onPumpCycle(int tileX, int tileY, PumpResult result) {
                applyResult(result);
            }
        });
    }

    /** Registers the level data type (mod init). */
    public static void register() {
        LevelDataRegistry.registerLevelData(KEY, PipeSystem.class);
    }

    /** The level's pipe system, created when missing; {@code null} on clients. */
    public static PipeSystem get(Level level) {
        if (level == null || !level.isServer()) {
            return null;
        }
        LevelData data = level.getLevelData(KEY);
        if (data instanceof PipeSystem) {
            return (PipeSystem) data;
        }
        PipeSystem created = new PipeSystem();
        level.addLevelData(KEY, created);
        return created;
    }

    /** The level's pipe system if it exists (server only). */
    public static PipeSystem getIfExists(Level level) {
        if (level == null || !level.isServer()) {
            return null;
        }
        LevelData data = level.getLevelData(KEY);
        return data instanceof PipeSystem ? (PipeSystem) data : null;
    }

    /** Sends a region's underground pipe flags to a client that just got the region (server). */
    public static void sendRegionTo(Level level, Region region, ServerClient client) {
        PipeSystem system = getIfExists(level);
        if (system != null && region != null && client != null) {
            system.sendRegion(region, client);
        }
    }

    /** Creates the level's pipe system before region data holding its key is read. */
    public static void ensureForRegionData(Level level, LoadData save) {
        if (level == null || !level.isServer() || save == null || getIfExists(level) != null) {
            return;
        }
        for (LoadData data : save.getLoadDataByName("LEVELDATA")) {
            if (KEY.equals(data.getSafeString("key", null, false))) {
                get(level);
                return;
            }
        }
    }

    /**
     * Creates the level's pipe system before its level data tick (server). It was added for the tank
     * region keeping, whose level data loaded regions during that tick; that keeping is gone (N20-8,
     * {@code TankRegionsLevelData} only reads old data now). It stays as a guard: a region that any
     * level data loads during the tick may hold pipes, whose holders and object entities need the
     * system, and creating it then would change the level data map while the game iterates it
     * ({@code LevelDataManager.tick}).
     */
    public static void ensureBeforeLevelDataTick(Level level) {
        if (level != null && level.isServer() && getIfExists(level) == null) {
            get(level);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && level.isServer()) {
            grid.setTileLoadedLookup(level.regionManager::isTileLoaded);
        }
    }

    public PipeGrid getGrid() {
        return grid;
    }

    /**
     * Whether an object entity that is being removed is only replaced by another entity of the same
     * object, its tile still holding the object ({@code objectID}) it was created for. The engine
     * does that when a region loads (a fresh entity for every object, then the saved one replaces it:
     * {@code ObjectRegionLayer.loadSaveData}, {@code TileEntityList.addHidden}) and when an object is
     * placed (two entities in a row). When the object itself is removed, the tile holds another one
     * by then. A replaced part leaves the engine as an unloaded one does: pumps keep their valve
     * sources (N19-1), with no network rebuild.
     */
    public static boolean isReplacedEntity(ObjectEntity entity, int objectID) {
        return objectID >= 0 && entity.getLevel() != null
                && entity.getLevel().getObjectID(entity.tileX, entity.tileY) == objectID;
    }

    /**
     * Whether the entity's region is still loading: the engine made a fresh entity for every object
     * and replaces it with the saved one, if any (A1). Such an entity does not register its part yet;
     * the region's loaded event registers the parts no saved entity replaced ({@link #onRegionLoaded}).
     */
    public static boolean isRegionLoading(ObjectEntity entity) {
        Level level = entity.getLevel();
        return level != null && level.isServer()
                && !level.regionManager.isRegionLoadingCompleteByTile(entity.tileX, entity.tileY);
    }

    /**
     * The wrench toward a side ({@link PipeGrid#toggleSide}). The part on the other side changes too,
     * so its region is loaded first, as the level's object setter does: a part there would otherwise
     * not be in the engine, and its region file would undo the change when it loads. The region is
     * only loaded, never generated: when it was never generated (or does not load), nothing changes
     * ({@link PipeGrid.Check#NOT_LOADED}).
     */
    public PipeGrid.Check toggleSide(int tileX, int tileY, PipeGrid.Part part, Direction direction) {
        int x = tileX + direction.dx;
        int y = tileY + direction.dy;
        if (level.isTileWithinBounds(x, y) && !level.regionManager.isTileLoaded(x, y)) {
            int regionX = level.regionManager.getRegionCoordByTile(x);
            int regionY = level.regionManager.getRegionCoordByTile(y);
            // Loading a region that was never generated would create it empty: check that it exists.
            if (!level.regionManager.isRegionGenerated(regionX, regionY)) {
                return PipeGrid.Check.NOT_LOADED;
            }
            level.regionManager.ensureRegionIsLoadedButDontGenerate(regionX, regionY);
            if (!level.regionManager.isTileLoaded(x, y)) {
                return PipeGrid.Check.NOT_LOADED;
            }
        }
        return grid.toggleSide(tileX, tileY, part, direction);
    }

    /** A valve entered the engine: its wire signal and tank are read every tick ({@link #tick}). */
    public void addValveEntity(TankValveObjectEntity valve) {
        valveEntities.put(PipeGrid.key(valve.tileX, valve.tileY), valve);
    }

    /** A valve's entity is removed (picked up, unloaded or replaced). */
    public void removeValveEntity(TankValveObjectEntity valve) {
        valveEntities.remove(PipeGrid.key(valve.tileX, valve.tileY), valve);
    }

    /** Queues a manual pump's click for the next tick of the systems (N22-5). */
    public void queueClick(int tileX, int tileY) {
        grid.queueClick(tileX, tileY);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isServer()) {
            return;
        }
        if (!scanned) {
            // Created after some regions loaded: register their parts (idempotent).
            scanned = true;
            for (Region region : level.regionManager.collectLoadedRegions()) {
                registerRegion(region);
            }
        }
        if (holderRemoved && holders != null) {
            holderRemoved = false;
            holders.serverTick(holder -> {
            }, new ArrayList<DrawOnMapEntity>());
        }
        // Valves (N11-3, N27-4): the wire signal on each valve's tile and its tank, read from the level
        // before the pumps use them (this was each valve entity's own tick before N22-5).
        if (!valveEntities.isEmpty()) {
            for (TankValveObjectEntity valve : new ArrayList<>(valveEntities.values())) {
                valve.updateFromLevel();
            }
        }
        // The systems, in their fixed order (N22-5).
        grid.runTick();
    }

    // ------------------------------------------------------------------ link flag sync

    private void onLinksChanged(int tileX, int tileY, PipeGrid.Part part) {
        switch (part) {
            case BASIC_PIPE: {
                BasicPipeObjectEntity pipe = level.entityManager.getObjectEntity(tileX, tileY, BasicPipeObjectEntity.class);
                if (pipe != null) {
                    pipe.syncLinks();
                }
                break;
            }
            case VALVE: {
                TankValveObjectEntity valve = level.entityManager.getObjectEntity(tileX, tileY, TankValveObjectEntity.class);
                if (valve != null) {
                    valve.syncLinks();
                }
                break;
            }
            case PUMP: {
                PumpObjectEntity pump = level.entityManager.getObjectEntity(tileX, tileY, PumpObjectEntity.class);
                if (pump != null) {
                    pump.syncLinks();
                }
                break;
            }
            case UNDERGROUND_PIPE:
                sendUndergroundTile(tileX, tileY);
                break;
            default:
                break;
        }
    }

    /**
     * A pipe started or stopped holding fluid: the faces blocked by another fluid (N13-2), which
     * clients draw as cut, are synced where they may have changed: the pipes of its layer next to it,
     * itself, and the pipe of the other layer on its tile (their vertical face). Basic pipes sync
     * through their entities, underground pipes per tile, only when they changed.
     */
    private void onPipeFluidChanged(int tileX, int tileY, PipeLayer layer) {
        if (level == null) {
            return;
        }
        syncBlockedSides(tileX, tileY);
        if (layer == PipeLayer.BASE) {
            for (Direction d : Direction.values()) {
                syncBlockedSides(tileX + d.dx, tileY + d.dy);
            }
        }
        for (long tile : undergroundBlocked.changedTiles(tileX, tileY, layer)) {
            int x = PipeGrid.keyX(tile);
            int y = PipeGrid.keyY(tile);
            if (grid.getPipe(x, y, PipeLayer.UNDERGROUND) != null) {
                sendUndergroundTile(x, y);
            } else {
                // The pipe itself is gone: its object change tells the clients.
                undergroundBlocked.toSend(x, y);
            }
        }
    }

    private void syncBlockedSides(int tileX, int tileY) {
        BasicPipeObjectEntity pipe = level.entityManager.getObjectEntity(tileX, tileY, BasicPipeObjectEntity.class);
        if (pipe != null) {
            pipe.syncBlockedSides();
        }
    }

    /** Sends the underground pipe state of one tile to the clients that have it loaded. */
    public void sendUndergroundTile(int tileX, int tileY) {
        int blocked = undergroundBlocked.toSend(tileX, tileY);
        if (level.getServer() == null) {
            return;
        }
        level.getServer().network.sendToClientsWithTile(PacketUndergroundPipes.tile(level, tileX, tileY,
                undergroundLinks(tileX, tileY), blocked), level, tileX, tileY);
    }

    /** The underground pipe state of one tile as a tile update, for one client (its correction). */
    public PacketUndergroundPipes undergroundTilePacket(int tileX, int tileY) {
        return PacketUndergroundPipes.tile(level, tileX, tileY, undergroundLinks(tileX, tileY),
                grid.getFluidBlockedSides(tileX, tileY, PipeLayer.UNDERGROUND));
    }

    /** The link flags of the underground pipe at the tile, or -1 when there is none. */
    public int undergroundLinks(int tileX, int tileY) {
        PipeNode node = grid.getPipe(tileX, tileY, PipeLayer.UNDERGROUND);
        return node == null ? -1 : node.getLinks();
    }

    /** Sends a region's underground pipe state to a client that just got the region (D3). */
    public void sendRegion(Region region, ServerClient client) {
        List<long[]> entries = new ArrayList<>();
        forEachTile(region, (x, y) -> {
            PipeNode node = grid.getPipe(x, y, PipeLayer.UNDERGROUND);
            if (node != null) {
                entries.add(new long[]{x, y, node.getLinks(), undergroundBlocked.toSend(x, y)});
            }
        });
        client.sendPacket(PacketUndergroundPipes.region(level, region.regionX, region.regionY, entries));
    }

    // ------------------------------------------------------------------ underground pipe holders

    private TileEntityList<UndergroundPipeHolder> holders() {
        if (holders == null) {
            holders = new TileEntityList<>(level.entityManager, null, "mechanicsundergroundpipes", null, null);
        }
        return holders;
    }

    /** The holder at the tile, or {@code null}. */
    public UndergroundPipeHolder getHolder(int tileX, int tileY) {
        return holders == null ? null : holders.get(tileX, tileY, false);
    }

    /** Number of holders (diagnostics). */
    public int getHolderCount() {
        return holders == null ? 0 : holders.count();
    }

    private void removeHolder(UndergroundPipeHolder holder) {
        if (holder != null && !holder.removed()) {
            holder.setNode(null);
            holder.remove();
            holderRemoved = true;
        }
    }

    /**
     * An object on a tile changed (every object change goes through the region layer setter,
     * {@code TankChangePatches}). Keeps the underground pipes in step with the underground layer: a
     * placed pipe gets a holder and enters the engine (its vertical link starts cut over a basic pipe,
     * N16-4), a removed one leaves with its fluid (N12-1).
     */
    public static void onObjectChanged(Level level, int layerID, int tileX, int tileY, boolean regionReady) {
        if (layerID != UndergroundPipeLayer.ID || !regionReady || level == null || !level.isServer()) {
            return;
        }
        PipeSystem system = get(level);
        GameObject object = level.getObject(layerID, tileX, tileY);
        PipeNode node = system.grid.getPipe(tileX, tileY, PipeLayer.UNDERGROUND);
        UndergroundPipeHolder holder = system.getHolder(tileX, tileY);
        if (object instanceof UndergroundPipeObject) {
            MineralTier tier = ((UndergroundPipeObject) object).getMineralTier();
            if (node != null && node.getTier() == tier && holder != null && holder.getNode() == node) {
                return;
            }
            if (node != null) {
                system.grid.removePipe(tileX, tileY, PipeLayer.UNDERGROUND);
            }
            system.removeHolder(holder);
            UndergroundPipeHolder placed = new UndergroundPipeHolder(level, tileX, tileY);
            system.holders().addHidden(placed);
            placed.setNode(system.grid.placePipe(tileX, tileY, PipeLayer.UNDERGROUND, tier));
            system.sendUndergroundTile(tileX, tileY);
        } else if (node != null || holder != null) {
            if (node != null) {
                system.grid.removePipe(tileX, tileY, PipeLayer.UNDERGROUND);
            }
            system.removeHolder(holder);
            system.sendUndergroundTile(tileX, tileY);
        }
    }

    /**
     * A holder entry of a region's object entity section (N24-1, {@link HolderSavePatches}): the holder
     * goes to this list; it enters the engine when its region finished loading. Returns false for an
     * engine entry (no layer field), which the engine reads itself.
     */
    public static boolean loadHolderEntry(LoadData save, Level level) {
        if (save == null || !save.hasLoadDataByName(UndergroundPipeHolder.LAYER_FIELD)) {
            return false;
        }
        String layer = save.getUnsafeString(UndergroundPipeHolder.LAYER_FIELD, "", false);
        String type = save.getUnsafeString("stringID", "", false);
        if (level == null || !level.isServer()) {
            return true;
        }
        if (!UndergroundPipeLayer.STRING_ID.equals(layer) || !UndergroundPipeHolder.TYPE.equals(type)) {
            System.err.println("Mechanics: dropped an object entity entry of layer " + layer + " type " + type
                    + " on level " + level.getIdentifier());
            return true;
        }
        try {
            int x = save.getInt("x");
            int y = save.getInt("y");
            UndergroundPipeHolder holder = new UndergroundPipeHolder(level, x, y);
            holder.applyLoadData(save);
            PipeSystem system = get(level);
            system.removeHolder(system.getHolder(x, y));
            system.holders().addHidden(holder);
        } catch (Exception e) {
            System.err.println("Mechanics: underground pipe holder entry not read: " + e);
        }
        return true;
    }

    /** Adds a region's holders to its object entity section (N24-1, {@link HolderSavePatches}). */
    public static void addHolderEntries(Region region, SaveData save) {
        PipeSystem system = region == null ? null : getIfExists(region.manager.level);
        if (system == null || system.holders == null) {
            return;
        }
        List<SaveData> entries = new ArrayList<>();
        for (UndergroundPipeHolder holder : system.holders.getInRegion(region.regionX, region.regionY)) {
            if (holder.removed()) {
                continue;
            }
            SaveData entry = ObjectEntitySave.getSave(holder);
            entry.addUnsafeString(UndergroundPipeHolder.LAYER_FIELD, UndergroundPipeLayer.STRING_ID);
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            return;
        }
        SaveData section = objectEntitySection(save);
        for (SaveData entry : entries) {
            section.addSaveData(entry);
        }
    }

    private static Field saveComponentField;

    /** The region save's {@code OBJECTENTITIES} section, added when the region has no engine entity. */
    private static SaveData objectEntitySection(SaveData save) {
        try {
            if (saveComponentField == null) {
                Field field = SaveData.class.getDeclaredField("save");
                field.setAccessible(true);
                saveComponentField = field;
            }
            necesse.engine.save.SaveComponent component = (necesse.engine.save.SaveComponent) saveComponentField.get(save);
            necesse.engine.save.SaveComponent section = component.getFirstComponentByName("OBJECTENTITIES");
            if (section != null) {
                return new SaveData(section);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("Mechanics: cannot reach the region's object entity section: " + e);
        }
        SaveData section = new SaveData("OBJECTENTITIES");
        save.addSaveData(section);
        return section;
    }

    /**
     * Registers the parts of a loaded region (idempotent): every underground pipe takes its holder's
     * saved state, else (older worlds, N24-1 migration) the region's old record, else the old level
     * mirror, else starts new; holders without their pipe object are dropped. Basic pipes, pumps and
     * valves whose fresh entity no saved one replaced while the region loaded register now.
     */
    private void registerRegion(Region region) {
        List<PipeRecord> records = pendingRegions.remove(PipeGrid.key(region.regionX, region.regionY));
        Map<Long, PipeRecord> byTile = new HashMap<>();
        if (records != null) {
            for (PipeRecord record : records) {
                byTile.put(PipeGrid.key(record.x, record.y), record);
            }
        }
        forEachTile(region, (x, y) -> {
            GameObject under = level.getObject(UndergroundPipeLayer.ID, x, y);
            UndergroundPipeHolder holder = getHolder(x, y);
            PipeNode node = grid.getPipe(x, y, PipeLayer.UNDERGROUND);
            if (under instanceof UndergroundPipeObject) {
                MineralTier tier = ((UndergroundPipeObject) under).getMineralTier();
                if (holder != null && holder.getNode() != null && holder.getNode() == node && node.getTier() == tier) {
                    // Registered already.
                } else {
                    registerUnderground(x, y, tier, holder, byTile.get(PipeGrid.key(x, y)));
                }
            } else {
                if (node != null) {
                    grid.removePipe(x, y, PipeLayer.UNDERGROUND);
                }
                removeHolder(holder);
            }
            legacyMirror.remove(mirrorKey(x, y, PipeLayer.UNDERGROUND));
            legacyMirror.remove(mirrorKey(x, y, PipeLayer.BASE));
            ObjectEntity entity = level.entityManager.getObjectEntity(x, y);
            if (entity instanceof BasicPipeObjectEntity) {
                ((BasicPipeObjectEntity) entity).registerIfDeferred();
            } else if (entity instanceof PumpObjectEntity) {
                ((PumpObjectEntity) entity).registerIfDeferred();
            } else if (entity instanceof TankValveObjectEntity) {
                ((TankValveObjectEntity) entity).registerIfDeferred();
            }
        });
    }

    private void registerUnderground(int x, int y, MineralTier tier, UndergroundPipeHolder holder, PipeRecord record) {
        if (grid.getPipe(x, y, PipeLayer.UNDERGROUND) != null) {
            grid.unloadPipe(x, y, PipeLayer.UNDERGROUND);
        }
        UndergroundPipeHolder target = holder;
        if (target == null || target.removed()) {
            target = new UndergroundPipeHolder(level, x, y);
            holders().addHidden(target);
        }
        PipeNode node;
        if (target.isFromSave()) {
            node = grid.loadPipe(x, y, PipeLayer.UNDERGROUND, tier, target.getSavedLinks(), target.getSavedFluid(),
                    target.getSavedAmount(), true, target.getSavedHintDests(), target.getSavedHintCodes());
        } else {
            PipeRecord old = record != null && record.tier == tier ? record : legacyMirror.get(mirrorKey(x, y, PipeLayer.UNDERGROUND));
            if (old != null && old.tier == tier) {
                // Migrated (N24-1): the region's old record, else the old mirror.
                node = grid.loadPipe(x, y, PipeLayer.UNDERGROUND, tier, old.links, old.fluid, old.amount, true);
            } else {
                // No state anywhere: as when it was placed (vertical link cut over a basic pipe, N16-4).
                int links = grid.getPipe(x, y, PipeLayer.BASE) != null ? LinkFlags.withVertical(LinkFlags.ALL_OPEN, false)
                        : LinkFlags.ALL_OPEN;
                node = grid.loadPipe(x, y, PipeLayer.UNDERGROUND, tier, links, null, 0, true);
            }
        }
        target.setNode(node);
    }

    @Override
    public void onRegionLoaded(Region region) {
        if (isServer()) {
            registerRegion(region);
        }
    }

    @Override
    public void addRegionSaveData(Region region, SaveData save) {
        // Underground pipe state is saved by the holders in the object entity section (N24-1); the old
        // records (PIPE) are only read, to migrate.
    }

    @Override
    public void loadRegionSaveData(Region region, LoadData save) {
        List<PipeRecord> records = new ArrayList<>();
        for (LoadData pipe : save.getLoadDataByName("PIPE")) {
            PipeRecord record = PipeRecord.load(pipe, PipeLayer.UNDERGROUND);
            if (record != null) {
                records.add(record);
            }
        }
        if (!records.isEmpty()) {
            pendingRegions.put(PipeGrid.key(region.regionX, region.regionY), records);
        }
    }

    @Override
    public void onUnloadedRegion(Region region) {
        pendingRegions.remove(PipeGrid.key(region.regionX, region.regionY));
        if (holders == null) {
            return;
        }
        // The region file already has their state (saved before this): they leave the engine.
        for (UndergroundPipeHolder holder : new ArrayList<>(holders.getInRegion(region.regionX, region.regionY))) {
            if (grid.getPipe(holder.tileX, holder.tileY, PipeLayer.UNDERGROUND) == holder.getNode()) {
                grid.unloadPipe(holder.tileX, holder.tileY, PipeLayer.UNDERGROUND);
            }
            removeHolder(holder);
        }
    }

    // ------------------------------------------------------------------ pump results

    /**
     * Removes the objects of pipes that broke (N12-5: no drop). The engine already dropped them; the
     * object removal finds nothing left to remove there.
     */
    public void applyResult(PumpResult result) {
        if (result == null || level == null) {
            return;
        }
        for (PipeNode node : result.getBroken()) {
            int layerID = node.getLayer() == PipeLayer.BASE ? 0 : UndergroundPipeLayer.ID;
            removeObjectWithoutDrop(level, layerID, node.getTileX(), node.getTileY());
        }
    }

    /** Clears an object on a layer and tells the clients that have the tile. */
    public static void removeObjectWithoutDrop(Level level, int layerID, int tileX, int tileY) {
        level.objectLayer.setObject(layerID, tileX, tileY, 0);
        level.objectLayer.setObjectRotation(layerID, tileX, tileY, 0);
        if (level.getServer() != null) {
            level.getServer().network.sendToClientsWithTile(new PacketChangeObject(level, layerID, tileX, tileY, 0, 0),
                    level, tileX, tileY);
        }
    }

    // ------------------------------------------------------------------ level save (summaries)

    @Override
    public boolean shouldSave() {
        return !grid.getSummaries().isEmpty() || !legacyMirror.isEmpty();
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        for (PipeGrid.RouteSummary summary : grid.getSummaries()) {
            save.addSaveData(saveSummary(summary));
        }
        Map<Long, Integer> regionChanges = grid.getSavedRegionChanges();
        if (!regionChanges.isEmpty()) {
            // N28-1: region x, region y, number, for every region a summary passes.
            int[] values = new int[regionChanges.size() * 3];
            int i = 0;
            for (Map.Entry<Long, Integer> entry : regionChanges.entrySet()) {
                values[i++] = PipeGrid.keyX(entry.getKey());
                values[i++] = PipeGrid.keyY(entry.getKey());
                values[i++] = entry.getValue();
            }
            save.addIntArray("REGIONCHANGES", values);
        }
        if (!legacyMirror.isEmpty()) {
            // Old mirror entries of regions not loaded since the update: kept to migrate them later.
            SaveData mirror = new SaveData("MIRROR");
            for (PipeRecord record : legacyMirror.values()) {
                mirror.addSaveData(record.toSave(record.layer == PipeLayer.BASE ? "BASIC" : "UNDERGROUND"));
            }
            save.addSaveData(mirror);
        }
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        int[] regionChanges = save.getIntArray("REGIONCHANGES", new int[0], false);
        for (int i = 0; i + 2 < regionChanges.length; i += 3) {
            grid.loadRegionChange(PipeGrid.key(regionChanges[i], regionChanges[i + 1]), regionChanges[i + 2]);
        }
        for (LoadData summary : save.getLoadDataByName("SUMMARY")) {
            PipeGrid.RouteSummary loaded = loadSummary(summary);
            if (loaded != null) {
                grid.loadSummary(loaded);
            }
        }
        LoadData mirror = save.getFirstLoadDataByName("MIRROR");
        if (mirror != null) {
            for (LoadData pipe : mirror.getLoadDataByName("BASIC")) {
                putMirror(PipeRecord.load(pipe, PipeLayer.BASE));
            }
            for (LoadData pipe : mirror.getLoadDataByName("UNDERGROUND")) {
                putMirror(PipeRecord.load(pipe, PipeLayer.UNDERGROUND));
            }
        }
    }

    private void putMirror(PipeRecord record) {
        if (record != null && record.layer == PipeLayer.UNDERGROUND) {
            legacyMirror.put(mirrorKey(record.x, record.y, record.layer), record);
        }
    }

    private static String mirrorKey(int x, int y, PipeLayer layer) {
        return x + "," + y + "," + layer;
    }

    /** Values per run in a saved summary (11 before the region structure change number, N28-1). */
    private static final int RUN_INTS = 12;

    private static SaveData saveSummary(PipeGrid.RouteSummary summary) {
        SaveData save = new SaveData("SUMMARY");
        save.addInt("pumpX", summary.pumpX);
        save.addInt("pumpY", summary.pumpY);
        save.addInt("valveX", summary.valveX);
        save.addInt("valveY", summary.valveY);
        int[] runs = new int[summary.runs.length * RUN_INTS];
        for (int i = 0; i < summary.runs.length; i++) {
            PipeGrid.SummaryRun run = summary.runs[i];
            int o = i * RUN_INTS;
            runs[o] = run.firstX;
            runs[o + 1] = run.firstY;
            runs[o + 2] = run.firstLayer.ordinal();
            runs[o + 3] = run.lastX;
            runs[o + 4] = run.lastY;
            runs[o + 5] = run.lastLayer.ordinal();
            runs[o + 6] = run.count;
            runs[o + 7] = run.capacity;
            runs[o + 8] = run.lowestTier.ordinal();
            runs[o + 9] = run.full ? 1 : 0;
            runs[o + 10] = run.fluid == null ? -1 : run.fluid.ordinal();
            runs[o + 11] = run.regionChange;
        }
        save.addInt("runInts", RUN_INTS);
        save.addIntArray("runs", runs);
        return save;
    }

    private static PipeGrid.RouteSummary loadSummary(LoadData save) {
        try {
            int[] values = save.getIntArray("runs", new int[0], false);
            // Summaries saved before N28-1 have 11 values per run and no number: as if written at 0.
            int width = Math.max(11, save.getInt("runInts", 11, false));
            PipeGrid.SummaryRun[] runs = new PipeGrid.SummaryRun[values.length / width];
            PipeLayer[] layers = PipeLayer.values();
            for (int i = 0; i < runs.length; i++) {
                int o = i * width;
                int fluid = values[o + 10];
                runs[i] = new PipeGrid.SummaryRun(devp0tion.mechanics.core.TileBuckets.bucketOf(values[o], values[o + 1]),
                        values[o], values[o + 1], layers[values[o + 2]], values[o + 3], values[o + 4], layers[values[o + 5]],
                        values[o + 6], values[o + 7], MineralTier.values()[values[o + 8]], values[o + 9] != 0,
                        fluid < 0 ? null : FluidType.values()[fluid], width > 11 ? values[o + 11] : 0);
            }
            return new PipeGrid.RouteSummary(save.getInt("pumpX"), save.getInt("pumpY"), save.getInt("valveX"),
                    save.getInt("valveY"), runs);
        } catch (RuntimeException e) {
            System.err.println("Mechanics: network summary not read: " + e);
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private interface TileConsumer {
        void accept(int tileX, int tileY);
    }

    private static void forEachTile(Region region, TileConsumer consumer) {
        for (int y = region.tileYOffset; y < region.tileYOffset + region.tileHeight; y++) {
            for (int x = region.tileXOffset; x < region.tileXOffset + region.tileWidth; x++) {
                consumer.accept(x, y);
            }
        }
    }

    /** One pipe's state in the old formats (region data PIPE, level MIRROR), read to migrate them. */
    static final class PipeRecord {
        final int x;
        final int y;
        final PipeLayer layer;
        final MineralTier tier;
        final int links;
        final FluidType fluid;
        final int amount;

        PipeRecord(int x, int y, PipeLayer layer, MineralTier tier, int links, FluidType fluid, int amount) {
            this.x = x;
            this.y = y;
            this.layer = layer;
            this.tier = tier;
            this.links = links;
            this.fluid = fluid;
            this.amount = amount;
        }

        SaveData toSave(String name) {
            SaveData save = new SaveData(name);
            save.addInt("x", x);
            save.addInt("y", y);
            save.addEnum("tier", tier);
            save.addInt("links", links);
            if (fluid != null && amount > 0) {
                save.addEnum("fluid", fluid);
                save.addInt("amount", amount);
            }
            return save;
        }

        static PipeRecord load(LoadData save, PipeLayer layer) {
            MineralTier tier = save.getEnum(MineralTier.class, "tier", null, false);
            if (tier == null) {
                return null;
            }
            FluidType fluid = save.getEnum(FluidType.class, "fluid", null, false);
            int amount = save.getInt("amount", 0, false);
            return new PipeRecord(save.getInt("x", 0, false), save.getInt("y", 0, false), layer, tier,
                    LinkFlags.sanitize(save.getInt("links", LinkFlags.ALL_OPEN, false)), fluid == null ? null : fluid,
                    fluid == null ? 0 : Math.max(0, amount));
        }
    }

}
