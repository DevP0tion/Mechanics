package devp0tion.mechanics.pipe;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.entity.manager.EntityManager;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelDataManager;
import necesse.level.maps.regionSystem.Region;
import net.bytebuddy.asm.Advice;

/**
 * Engine hooks for multiplayer consistency of the pipe content.
 *
 * <ul>
 *     <li>{@link PlaceRefused}: a server-refused object placement by one of this mod's rules is
 *     corrected on the client ({@link PlacementCorrection}).</li>
 *     <li>{@link ClientLoadedRegion}: when the server sends a client a region (and its entities),
 *     the region's underground pipe flags go with it ({@link PacketUndergroundPipes}), so they are
 *     known before the player picks up a wrench.</li>
 *     <li>{@link RegionDataApplied}: the level's {@link PipeSystem} exists before a region's saved
 *     underground pipe state is read, also when the level file was saved before it was created.</li>
 * </ul>
 */
public final class PipeSyncPatches {

    private PipeSyncPatches() {
    }

    /** {@code ObjectItem.onAttemptPlace(level, x, y, player, item, mapContent, error)}: called with the refusal reason. */
    @ModMethodPatch(target = ObjectItem.class, name = "onAttemptPlace",
            arguments = {Level.class, int.class, int.class, PlayerMob.class, InventoryItem.class, GNDItemMap.class, String.class})
    public static class PlaceRefused {

        @Advice.OnMethodExit
        static void onExit(@Advice.Argument(0) Level level, @Advice.Argument(3) PlayerMob player,
                           @Advice.Argument(5) GNDItemMap mapContent, @Advice.Argument(6) String error) {
            PlacementCorrection.onObjectPlaceRefused(level, player, mapContent, error);
        }

    }

    /** {@code EntityManager.onServerClientLoadedRegion(region, client)}: the server sent the client a region. */
    @ModMethodPatch(target = EntityManager.class, name = "onServerClientLoadedRegion",
            arguments = {Region.class, ServerClient.class})
    public static class ClientLoadedRegion {

        @Advice.OnMethodExit
        static void onExit(@Advice.FieldValue("level") Level level, @Advice.Argument(0) Region region,
                           @Advice.Argument(1) ServerClient client) {
            PipeSystem.sendRegionTo(level, region, client);
        }

    }

    /** {@code LevelDataManager.applyRegionSaveData(region, save)}: before a region's level data is read. */
    @ModMethodPatch(target = LevelDataManager.class, name = "applyRegionSaveData",
            arguments = {Region.class, LoadData.class})
    public static class RegionDataApplied {

        @Advice.OnMethodEnter
        static void onEnter(@Advice.This LevelDataManager manager, @Advice.Argument(1) LoadData save) {
            PipeSystem.ensureForRegionData(manager.level, save);
        }

    }

}
