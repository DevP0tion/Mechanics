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
import devp0tion.mechanics.objects.BasicPipeObject;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import devp0tion.mechanics.tank.TankRegionsLevelData;
import devp0tion.mechanics.tank.TankValveObjectEntity;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.LevelDataRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.RegionLevelDataComponent;
import necesse.level.maps.regionSystem.Region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pipe network manager of one server level (N7-1): the level's {@link PipeGrid} and the
 * underground pipes' state.
 *
 * <ul>
 *     <li>Basic pipes, valves and pumps register themselves from their object entities (the basic
 *     pipe's state lives there, N19-6); underground pipes are registered from their layer objects
 *     and their state lives here (N14-4).</li>
 *     <li>Underground pipe state is saved per region, into the region's own save file next to its
 *     layer objects ({@link RegionLevelDataComponent}), so objects and state are always saved
 *     together and a crash cannot leave them out of step.</li>
 *     <li>Mirror (N14-3, N15-1): every pipe's last state stays in the grid while its region is
 *     unloaded, and the whole mirror is saved with the level, so paths through unloaded regions are
 *     known also after a restart. A region's own data replaces its mirror when it loads.</li>
 *     <li>Clients get the underground pipes' link flags and the faces blocked by another fluid
 *     ({@link PipeGrid#getFluidBlockedSides}, N13-2; the only underground state they draw) per region
 *     when the region is sent to them ({@link PipeSyncPatches}) and per tile when either changes
 *     ({@link PacketUndergroundPipes}); fluid amounts stay on the server. Basic pipes sync their link
 *     flags and the faces blocked by another fluid themselves ({@link BasicPipeObjectEntity}).</li>
 *     <li>The grid clock advances every level tick (the 20-tick cycle windows of the transport cap,
 *     N14-2).</li>
 * </ul>
 */
public class PipeSystem extends LevelData implements RegionLevelDataComponent, RegionLoadedListenerEntityComponent {

    /** Registry stringID and level data key; must stay the same for saved worlds. */
    public static final String KEY = "mechanicspipes";

    private final PipeGrid grid = new PipeGrid(PipeTierRules.TABLE);
    /** Underground pipe records read from region files, until the region finishes loading. */
    private final Map<Long, List<PipeRecord>> pendingRegions = new HashMap<>();
    /** The underground pipes' faces blocked by another fluid as last sent to clients (N13-2). */
    private final BlockedFaceSync undergroundBlocked = new BlockedFaceSync(grid, PipeLayer.UNDERGROUND);
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
     * Creates the level's pipe system before its level data tick (server) when the level has tank
     * region data. That data's tick loads the regions of kept tanks (N15-6, {@link TankRegionsLevelData}),
     * and the valves and pipes in them get the pipe system when they are created: creating it then
     * would change the level data map while the game iterates it ({@code LevelDataManager.tick}).
     * This happens on levels saved before round 4b, which have tanks but no pipe system.
     */
    public static void ensureBeforeLevelDataTick(Level level) {
        if (level != null && level.isServer() && getIfExists(level) == null
                && level.getLevelData(TankRegionsLevelData.KEY) != null) {
            get(level);
        }
    }

    /** The level's pipe system if it exists (server only). */
    public static PipeSystem getIfExists(Level level) {
        if (level == null || !level.isServer()) {
            return null;
        }
        LevelData data = level.getLevelData(KEY);
        return data instanceof PipeSystem ? (PipeSystem) data : null;
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
     * by then. A replaced part must leave the grid as an unloaded one does: pumps keep their valve
     * sources (N19-1) and pipes their state, with no network rebuild.
     */
    public static boolean isReplacedEntity(ObjectEntity entity, int objectID) {
        return objectID >= 0 && entity.getLevel() != null
                && entity.getLevel().getObjectID(entity.tileX, entity.tileY) == objectID;
    }

    /**
     * The wrench toward a side ({@link PipeGrid#toggleSide}). The part on the other side changes too,
     * so its region is loaded first, as the level's object setter does: a pipe there would otherwise
     * only be a read-only mirror (N14-3) whose region file undoes the change when it loads, and a pump
     * or valve there would not be in the grid at all.
     */
    public PipeGrid.Check toggleSide(int tileX, int tileY, PipeGrid.Part part, Direction direction) {
        level.regionManager.getRegionByTile(tileX + direction.dx, tileY + direction.dy, true);
        return grid.toggleSide(tileX, tileY, part, direction);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isServer()) {
            return;
        }
        if (!scanned) {
            // Created after some regions loaded: register their underground pipes (idempotent).
            scanned = true;
            for (Region region : level.regionManager.collectLoadedRegions()) {
                registerRegion(region);
            }
        }
        grid.tick();
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

    // ------------------------------------------------------------------ underground objects

    /**
     * An object on a tile changed (every object change goes through the region layer setter,
     * {@code TankChangePatches}). Keeps the grid's underground pipes in step with the underground
     * layer: placed pipes are added (the vertical link starts cut over a basic pipe, N16-4),
     * removed ones dropped with their fluid (N12-1).
     */
    public static void onObjectChanged(Level level, int layerID, int tileX, int tileY, boolean regionReady) {
        if (layerID != UndergroundPipeLayer.ID || !regionReady || level == null || !level.isServer()) {
            return;
        }
        PipeSystem system = get(level);
        GameObject object = level.getObject(layerID, tileX, tileY);
        PipeNode node = system.grid.getPipe(tileX, tileY, PipeLayer.UNDERGROUND);
        if (object instanceof UndergroundPipeObject) {
            MineralTier tier = ((UndergroundPipeObject) object).getMineralTier();
            if (node != null && node.getTier() == tier) {
                return;
            }
            if (node != null) {
                system.grid.removePipe(tileX, tileY, PipeLayer.UNDERGROUND);
            }
            system.grid.placePipe(tileX, tileY, PipeLayer.UNDERGROUND, tier);
            system.sendUndergroundTile(tileX, tileY);
        } else if (node != null) {
            system.grid.removePipe(tileX, tileY, PipeLayer.UNDERGROUND);
            system.sendUndergroundTile(tileX, tileY);
        }
    }

    /**
     * Registers the underground pipes of a loaded region: each takes its state from the region's
     * file, else from the mirror, else starts new. Mirror entries without a matching object are
     * dropped.
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
            PipeNode node = grid.getPipe(x, y, PipeLayer.UNDERGROUND);
            if (under instanceof UndergroundPipeObject) {
                MineralTier tier = ((UndergroundPipeObject) under).getMineralTier();
                PipeRecord record = byTile.get(PipeGrid.key(x, y));
                if (record != null && record.tier == tier) {
                    grid.loadPipe(x, y, PipeLayer.UNDERGROUND, tier, record.links, record.fluid, record.amount, true);
                } else if (node != null && node.getTier() == tier) {
                    grid.loadPipe(x, y, PipeLayer.UNDERGROUND, tier, node.getLinks(), node.getFluid(), node.getAmount(), true);
                } else {
                    if (node != null) {
                        grid.removePipe(x, y, PipeLayer.UNDERGROUND);
                    }
                    grid.placePipe(x, y, PipeLayer.UNDERGROUND, tier);
                }
            } else if (node != null) {
                grid.removePipe(x, y, PipeLayer.UNDERGROUND);
            }
            PipeNode base = grid.getPipe(x, y, PipeLayer.BASE);
            if (base != null && !base.isLoaded() && !(level.getObject(x, y) instanceof BasicPipeObject)) {
                // A mirror of a basic pipe that is not in the region file any more.
                grid.removePipe(x, y, PipeLayer.BASE);
            }
        });
    }

    @Override
    public void onRegionLoaded(Region region) {
        if (isServer()) {
            registerRegion(region);
        }
    }

    @Override
    public void addRegionSaveData(Region region, SaveData save) {
        forEachTile(region, (x, y) -> {
            PipeNode node = grid.getPipe(x, y, PipeLayer.UNDERGROUND);
            if (node != null) {
                save.addSaveData(PipeRecord.of(node).toSave("PIPE"));
            }
        });
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
        pendingRegions.put(PipeGrid.key(region.regionX, region.regionY), records);
    }

    @Override
    public void onUnloadedRegion(Region region) {
        pendingRegions.remove(PipeGrid.key(region.regionX, region.regionY));
        // The region file already has the state; the grid keeps a read-only mirror (N14-3).
        forEachTile(region, (x, y) -> grid.unloadPipe(x, y, PipeLayer.UNDERGROUND));
    }

    // ------------------------------------------------------------------ pump results

    /**
     * Removes the objects of pipes that broke (N12-5: no drop). The grid already dropped them; the
     * object removal finds nothing left to remove there.
     */
    public void applyResult(PumpResult result) {
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

    // ------------------------------------------------------------------ level save (mirror)

    @Override
    public boolean shouldSave() {
        // Always: the data must exist before regions with underground pipes load again.
        return true;
    }

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        SaveData mirror = new SaveData("MIRROR");
        for (PipeNode node : grid.getPipes()) {
            mirror.addSaveData(PipeRecord.of(node).toSave(node.getLayer() == PipeLayer.BASE ? "BASIC" : "UNDERGROUND"));
        }
        save.addSaveData(mirror);
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);
        LoadData mirror = save.getFirstLoadDataByName("MIRROR");
        if (mirror == null) {
            return;
        }
        for (LoadData pipe : mirror.getLoadDataByName("BASIC")) {
            loadMirror(PipeRecord.load(pipe, PipeLayer.BASE));
        }
        for (LoadData pipe : mirror.getLoadDataByName("UNDERGROUND")) {
            loadMirror(PipeRecord.load(pipe, PipeLayer.UNDERGROUND));
        }
    }

    private void loadMirror(PipeRecord record) {
        if (record == null || grid.getPipe(record.x, record.y, record.layer) != null) {
            return;
        }
        try {
            grid.loadPipe(record.x, record.y, record.layer, record.tier, record.links, record.fluid, record.amount, false);
        } catch (IllegalStateException e) {
            // The tile is taken (a part registered earlier): the region's own data wins.
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

    /** One pipe's saved state. */
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

        static PipeRecord of(PipeNode node) {
            return new PipeRecord(node.getTileX(), node.getTileY(), node.getLayer(), node.getTier(), node.getLinks(),
                    node.getFluid(), node.getAmount());
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
