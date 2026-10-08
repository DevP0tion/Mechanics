package devp0tion.mechanics.items;

import devp0tion.mechanics.client.WrenchControls;
import devp0tion.mechanics.client.WrenchTooltip;
import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.PipeLayer;
import devp0tion.mechanics.objects.BasicPipeObject;
import devp0tion.mechanics.objects.PumpObject;
import devp0tion.mechanics.objects.TankValveObject;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import devp0tion.mechanics.pipe.PipeSystem;
import devp0tion.mechanics.pipe.PlacementCorrection;
import devp0tion.mechanics.pipe.UndergroundPipeLayer;
import devp0tion.mechanics.tank.TankValveObjectEntity;
import devp0tion.mechanics.wrench.WrenchMode;
import devp0tion.mechanics.wrench.WrenchRefusal;
import devp0tion.mechanics.wrench.WrenchTargets;
import necesse.engine.localization.Localization;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.itemAttack.ItemAttackDrawOptions;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemInteractAction;
import necesse.inventory.item.placeableItem.PlaceableItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.TilePosition;

import java.awt.geom.Line2D;

/**
 * The engineering wrench (공학 렌치, 9-6, 12-8, N30-3): the pipe tool. Its name differs from the
 * vanilla wire tool's (렌치 / Wrench, N30-3).
 *
 * <ul>
 *     <li>Left click: recovers the pipe it points at, the basic pipe first, else the underground
 *     pipe; in underground mode the underground pipe first (12-8, 10-2, 10-5, N30-5). The pipe's
 *     fluid is lost (N12-1).</li>
 *     <li>Right click toward a side of a tile: toggles that side's link of the basic pipe, pump or
 *     valve there (else of the underground pipe; in underground mode the underground pipe first):
 *     cut when linked, linked otherwise (12-8, 13-4, N16-3, N16-4, N30-5). Linking a valve to a
 *     pump is refused when its tank holds another fluid than the pump's other sources (N16-3). The
 *     neighbour's region is loaded first, so the change also reaches a part in a region that was
 *     not loaded and lasts ({@link PipeSystem#toggleSide}).</li>
 *     <li>Right click on the middle of a tile, its centre 16x16 pixels (N30-4): toggles the vertical
 *     link between the basic pipe or valve there and the underground pipe there (12-8, 13-5,
 *     N16-4), in both modes (N30-5).</li>
 *     <li>Mode (N30-5): basic (the default) or underground, stored on the item
 *     ({@link #MODE_KEY}), switched with a rebindable key, default the middle mouse button
 *     ({@link WrenchControls}), which then does not pipette.</li>
 *     <li>Holding it shows the underground pipes (9-6) and, pointing at a tile, a tooltip with the
 *     tile's pipes (fluid name, linked and blocked directions, N30-2) and the reason a right click
 *     there would be refused (N30-1; a refused click itself shows no message) ({@link WrenchTooltip}).</li>
 * </ul>
 * Server authority: both clicks reach the server through the vanilla item packets (attack and
 * level interact, with the clicked position and inventory slot), and only the server changes
 * anything, with the mode of its own copy of the item ({@link PacketWrenchMode} brings a switch
 * there); clients do not predict. When the server refuses an action, the client is sent the cell's
 * real state ({@link PlacementCorrection}).
 */
public class MechanicsWrenchItem extends PlaceableItem implements ItemInteractAction {

    /** Item stringID (players do not see it; the name is in the locale files, N30-3). */
    public static final String STRING_ID = "mechanicswrench";

    /** Half the size of a tile's middle area in pixels: the centre 16x16 pixels (N30-4, {@link WrenchTargets#sideOf}). */
    public static final int MIDDLE_HALF_SIZE = WrenchTargets.MIDDLE_HALF_SIZE;

    /** Item data key of the mode (N30-5): {@link WrenchMode#saveName()}; none means basic. */
    public static final String MODE_KEY = "wrenchmode";

    /** Wrench errors (left click). */
    public static final String NO_PIPE = "nopipe";

    public MechanicsWrenchItem() {
        super(1, false);
        this.controllerIsTileBasedPlacing = true;
        this.rarity = Item.Rarity.COMMON;
        this.attackXOffset = 8;
        this.attackYOffset = 8;
        this.worldDrawSize = 32;
    }

    @Override
    public GameSprite getAttackSprite(InventoryItem item, PlayerMob player) {
        return getItemSprite(item, player);
    }

    @Override
    public void setDrawAttackRotation(InventoryItem item, ItemAttackDrawOptions drawOptions, float attackDirX, float attackDirY,
                                      float attackProgress) {
        drawOptions.swingRotation(attackProgress);
    }

    // ------------------------------------------------------------------ mode (N30-5)

    /** The wrench item's mode; basic when it has none (N30-5). */
    public static WrenchMode getMode(InventoryItem item) {
        GNDItemMap data = item == null ? null : item.getGndData();
        return WrenchMode.fromSaveName(data != null && data.hasKey(MODE_KEY) ? data.getString(MODE_KEY) : null);
    }

    /** Stores the mode in the item (saved and synced with it). */
    public static void setMode(InventoryItem item, WrenchMode mode) {
        item.getGndData().setString(MODE_KEY, mode.saveName());
    }

    // ------------------------------------------------------------------ left click: recover a pipe

    /** The layer of the pipe the wrench recovers at the tile in the mode, or -1 (N30-5). */
    private static int pipeLayerAt(Level level, int tileX, int tileY, WrenchMode mode) {
        PipeLayer layer = WrenchTargets.recoverLayer(level.getObject(tileX, tileY) instanceof BasicPipeObject,
                level.getObject(UndergroundPipeLayer.ID, tileX, tileY) instanceof UndergroundPipeObject, mode);
        if (layer == null) {
            return -1;
        }
        return layer == PipeLayer.BASE ? 0 : UndergroundPipeLayer.ID;
    }

    @Override
    public String canPlace(Level level, int x, int y, PlayerMob player, Line2D playerPositionLine, InventoryItem item,
                           GNDItemMap mapContent) {
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        if (!level.isTileWithinBounds(tileX, tileY)) {
            return "outsidelevel";
        }
        if (level.isProtected(tileX, tileY)) {
            return "protected";
        }
        if (!isInPlaceRange(level, tileX * 32 + 16, tileY * 32 + 16, player, playerPositionLine, item)) {
            return "outofrange";
        }
        return pipeLayerAt(level, tileX, tileY, getMode(item)) < 0 ? NO_PIPE : null;
    }

    @Override
    public InventoryItem onPlace(Level level, int x, int y, PlayerMob player, int seed, InventoryItem item, GNDItemMap mapContent) {
        if (!level.isServer()) {
            return item;
        }
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        // The server's copy of the item, so its mode (N30-5).
        int layerID = pipeLayerAt(level, tileX, tileY, getMode(item));
        if (layerID < 0) {
            return item;
        }
        GameObject pipe = level.getObject(layerID, tileX, tileY);
        PipeSystem.removeObjectWithoutDrop(level, layerID, tileX, tileY);
        level.entityManager.pickups.add(new InventoryItem(pipe.getStringID()).getPickupEntity(level, tileX * 32 + 16, tileY * 32 + 16));
        return item;
    }

    @Override
    public InventoryItem onAttemptPlace(Level level, int x, int y, PlayerMob player, InventoryItem item, GNDItemMap mapContent,
                                        String error) {
        PlacementCorrection.correct(level, player, GameMath.getTileCoordinate(x), GameMath.getTileCoordinate(y));
        return item;
    }

    // ------------------------------------------------------------------ right click: links

    /**
     * The linkable part on the base layer at a tile: a basic pipe, pump or valve, or {@code null}.
     * A valve that is a plain wall (N33-1, {@link TankValveObjectEntity#isPlainWall}, synced to
     * clients) is none, like a wall: no tooltip there (N30-7) and the click acts on the underground
     * pipe of the tile, if any; toward it a part's own flag flips only ({@link PipeGrid#toggleSide}).
     * TODO(design): what the wrench shows and does on a valve in a shared wall is not decided
     * (N33-1); read as a plain wall, so no refusal reason either.
     */
    private static PipeGrid.Part basePartAt(Level level, int tileX, int tileY) {
        GameObject base = level.getObject(tileX, tileY);
        if (base instanceof BasicPipeObject) {
            return PipeGrid.Part.BASIC_PIPE;
        }
        if (base instanceof PumpObject) {
            return PipeGrid.Part.PUMP;
        }
        if (base instanceof TankValveObject) {
            TankValveObjectEntity valve = level.entityManager.getObjectEntity(tileX, tileY, TankValveObjectEntity.class);
            return valve != null && valve.isPlainWall() ? null : PipeGrid.Part.VALVE;
        }
        return null;
    }

    /**
     * What the side right click acts on at a tile in the mode: the base layer pipe, pump or valve,
     * else the underground pipe; the underground pipe first in underground mode (N30-5).
     */
    public static PipeGrid.Part partAt(Level level, int tileX, int tileY, WrenchMode mode) {
        return WrenchTargets.sidePart(basePartAt(level, tileX, tileY),
                level.getObject(UndergroundPipeLayer.ID, tileX, tileY) instanceof UndergroundPipeObject, mode);
    }

    /** The side a click at level position (x, y) points to, or {@code null} for the tile's middle (N30-4). */
    public static Direction sideOf(int x, int y) {
        return WrenchTargets.sideOf(x, y);
    }

    /**
     * The right click's own refusals at a tile, before the pipe engine's (N30-1): a protected tile,
     * or beyond the place range. {@code null} when neither.
     */
    public static WrenchRefusal reachRefusal(Level level, int tileX, int tileY, ItemAttackerMob mob, InventoryItem item) {
        if (level.isProtected(tileX, tileY)) {
            return WrenchRefusal.PROTECTED;
        }
        if (mob.getPositionPoint().distance(tileX * 32 + 16, tileY * 32 + 16) > ((PlaceableItem) item.item).getPlaceRange(item, mob)) {
            return WrenchRefusal.OUT_OF_RANGE;
        }
        return null;
    }

    @Override
    public boolean canLevelInteract(Level level, int x, int y, ItemAttackerMob attackerMob, InventoryItem item) {
        if (!attackerMob.isPlayer) {
            return false;
        }
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        if (!level.isTileWithinBounds(tileX, tileY) || reachRefusal(level, tileX, tileY, attackerMob, item) != null) {
            return false;
        }
        return partAt(level, tileX, tileY, getMode(item)) != null;
    }

    @Override
    public boolean overridesObjectInteract(Level level, PlayerMob player, InventoryItem item) {
        return true;
    }

    @Override
    public InventoryItem onLevelInteract(Level level, int x, int y, ItemAttackerMob attackerMob, int attackHeight,
                                         InventoryItem item, ItemAttackSlot slot, int seed, GNDItemMap mapContent) {
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        if (level.isClient()) {
            // The tile's tooltip asks the server again soon (N30-1, N30-2).
            WrenchTooltip.invalidate(level, tileX, tileY);
        }
        if (!level.isServer() || !attackerMob.isPlayer) {
            return item;
        }
        PipeSystem system = PipeSystem.get(level);
        // The server's copy of the item, so its mode (N30-5).
        PipeGrid.Part part = partAt(level, tileX, tileY, getMode(item));
        PipeGrid.Check result = PipeGrid.Check.NOTHING_THERE;
        if (system != null && part != null) {
            Direction side = sideOf(x, y);
            result = side == null ? system.getGrid().toggleVertical(tileX, tileY)
                    : system.toggleSide(tileX, tileY, part, side);
        }
        if (result != PipeGrid.Check.OK) {
            // Refused (N16-3) or nothing there: no message (N30-1, the tooltip previews the reason);
            // the client gets the cell's real state.
            PlacementCorrection.correct(level, (PlayerMob) attackerMob, tileX, tileY);
        }
        return item;
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public void onMouseHoverTile(InventoryItem item, GameCamera camera, PlayerMob perspective, int mouseX, int mouseY,
                                 TilePosition pos, boolean isDebug) {
        super.onMouseHoverTile(item, camera, perspective, mouseX, mouseY, pos, isDebug);
        if (!isDebug) {
            WrenchTooltip.show(pos.level, perspective, item, mouseX, mouseY);
        }
    }

    @Override
    public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
        // Controls (12-8, 13-4, 13-5) with the mode key (N30-5, drawn as the bound key's icon) and
        // the tooltip preview (N30-1).
        tooltips.add(Localization.translate("itemtooltip", "mechanicswrenchtip"), 400);
        return tooltips;
    }

}
