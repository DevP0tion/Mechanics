package devp0tion.mechanics.items;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.objects.BasicPipeObject;
import devp0tion.mechanics.objects.PumpObject;
import devp0tion.mechanics.objects.TankValveObject;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import devp0tion.mechanics.pipe.PipeSystem;
import devp0tion.mechanics.pipe.PlacementCorrection;
import devp0tion.mechanics.pipe.UndergroundPipeLayer;
import necesse.engine.localization.Localization;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.gfx.drawOptions.itemAttack.ItemAttackDrawOptions;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemInteractAction;
import necesse.inventory.item.placeableItem.PlaceableItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

import java.awt.geom.Line2D;

/**
 * The wrench (렌치, 9-6, 12-8): the pipe tool.
 *
 * <ul>
 *     <li>Left click: recovers the pipe it points at, the basic pipe first, else the underground
 *     pipe (12-8, 10-2, 10-5). The pipe's fluid is lost (N12-1).</li>
 *     <li>Right click toward a side of a tile: toggles that side's link of the basic pipe, pump or
 *     valve there (else of the underground pipe): cut when linked, linked otherwise (12-8, 13-4,
 *     N16-3, N16-4). Linking a valve to a pump is refused when its tank holds another fluid than
 *     the pump's other sources (N16-3).</li>
 *     <li>Right click on the middle of a tile: toggles the vertical link between the basic pipe or
 *     valve there and the underground pipe there (12-8, 13-5, N16-4).</li>
 *     <li>Holding it shows the underground pipes (9-6).</li>
 * </ul>
 * Server authority: both clicks reach the server through the vanilla item packets (attack and
 * level interact, with the clicked position), and only the server changes anything; clients do
 * not predict. When the server refuses an action, the client is sent the cell's real state
 * ({@link PlacementCorrection}).
 *
 * <p>TODO(confirm): the item stringID {@link #STRING_ID} and the English name are provisional (the
 * vanilla {@code wrench} exists, V5).
 * <p>TODO(design): the middle area of a tile that counts as "the middle" ({@link #MIDDLE_HALF_SIZE}
 * pixels around the centre), and which part a side click acts on when a basic and an underground
 * pipe share the tile (the base layer part first), are not decided.
 * <p>TODO(design): a refused link gives the player no message.
 */
public class MechanicsWrenchItem extends PlaceableItem implements ItemInteractAction {

    /** Item stringID. TODO(confirm): provisional, the vanilla "wrench" is taken (V5). */
    public static final String STRING_ID = "mechanicswrench";

    /** Half the size of a tile's middle area, in pixels (the tile is 32). TODO(design): provisional. */
    public static final int MIDDLE_HALF_SIZE = 6;

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

    // ------------------------------------------------------------------ left click: recover a pipe

    /** The layer of the pipe the wrench recovers at the tile, or -1. */
    private static int pipeLayerAt(Level level, int tileX, int tileY) {
        if (level.getObject(tileX, tileY) instanceof BasicPipeObject) {
            return 0;
        }
        if (level.getObject(UndergroundPipeLayer.ID, tileX, tileY) instanceof UndergroundPipeObject) {
            return UndergroundPipeLayer.ID;
        }
        return -1;
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
        return pipeLayerAt(level, tileX, tileY) < 0 ? NO_PIPE : null;
    }

    @Override
    public InventoryItem onPlace(Level level, int x, int y, PlayerMob player, int seed, InventoryItem item, GNDItemMap mapContent) {
        if (!level.isServer()) {
            return item;
        }
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        int layerID = pipeLayerAt(level, tileX, tileY);
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

    /** What the wrench acts on at a tile: the base layer pipe, pump or valve, else the underground pipe. */
    private static PipeGrid.Part partAt(Level level, int tileX, int tileY) {
        GameObject base = level.getObject(tileX, tileY);
        if (base instanceof BasicPipeObject) {
            return PipeGrid.Part.BASIC_PIPE;
        }
        if (base instanceof PumpObject) {
            return PipeGrid.Part.PUMP;
        }
        if (base instanceof TankValveObject) {
            return PipeGrid.Part.VALVE;
        }
        if (level.getObject(UndergroundPipeLayer.ID, tileX, tileY) instanceof UndergroundPipeObject) {
            return PipeGrid.Part.UNDERGROUND_PIPE;
        }
        return null;
    }

    /** The side a click at level position (x, y) points to, or {@code null} for the tile's middle. */
    public static Direction sideOf(int x, int y) {
        int dx = Math.floorMod(x, 32) - 16;
        int dy = Math.floorMod(y, 32) - 16;
        if (Math.abs(dx) < MIDDLE_HALF_SIZE && Math.abs(dy) < MIDDLE_HALF_SIZE) {
            return null;
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dy >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    public boolean canLevelInteract(Level level, int x, int y, ItemAttackerMob attackerMob, InventoryItem item) {
        if (!attackerMob.isPlayer) {
            return false;
        }
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        if (!level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
            return false;
        }
        if (attackerMob.getPositionPoint().distance(tileX * 32 + 16, tileY * 32 + 16) > getPlaceRange(item, attackerMob)) {
            return false;
        }
        return partAt(level, tileX, tileY) != null;
    }

    @Override
    public boolean overridesObjectInteract(Level level, PlayerMob player, InventoryItem item) {
        return true;
    }

    @Override
    public InventoryItem onLevelInteract(Level level, int x, int y, ItemAttackerMob attackerMob, int attackHeight,
                                         InventoryItem item, ItemAttackSlot slot, int seed, GNDItemMap mapContent) {
        if (!level.isServer() || !attackerMob.isPlayer) {
            return item;
        }
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        PipeSystem system = PipeSystem.get(level);
        PipeGrid.Part part = partAt(level, tileX, tileY);
        PipeGrid.Check result = PipeGrid.Check.NOTHING_THERE;
        if (system != null && part != null) {
            Direction side = sideOf(x, y);
            result = side == null ? system.getGrid().toggleVertical(tileX, tileY)
                    : system.getGrid().toggleSide(tileX, tileY, part, side);
        }
        if (result != PipeGrid.Check.OK) {
            // Refused (N16-3) or nothing there: the client gets the cell's real state.
            PlacementCorrection.correct(level, (PlayerMob) attackerMob, tileX, tileY);
        }
        return item;
    }

    @Override
    public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
        tooltips.add(Localization.translate("itemtooltip", "mechanicswrenchtip"), 400);
        return tooltips;
    }

}
