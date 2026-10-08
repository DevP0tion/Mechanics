package devp0tion.mechanics.objects;

import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import devp0tion.mechanics.tank.TankValveObjectEntity;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.level.maps.Level;

import java.awt.Color;

/**
 * Tank valve (탱크 밸브): the inlet/outlet that pipes connect to (2-1, 2-2, 2-3).
 *
 * <ul>
 *     <li>Any number per tank, none needed, never on a corner (4-4, 4-5); takes the place of a
 *     mineral wall (5-13).</li>
 *     <li>Accepts incoming fluid automatically into its tank (N7-3, {@link TankValveObjectEntity});
 *     off while it receives a wire signal, on otherwise (N11-3).</li>
 *     <li>Has the tier of the mineral wall it was crafted from (N13-5 ②): one item for every tier
 *     ({@link TankValveObjectItem}), the tier kept when placed (in the object entity) and when picked
 *     up again ({@link #getLootTable}), shown in the item tooltip.</li>
 *     <li>Works for the one recognized tank with it in its border; in a wall shared by two recognized
 *     tanks it is a plain wall for both, and both tanks are recognized (N33-1,
 *     {@link TankValveObjectEntity#isPlainWall}). So it may be placed in a shared wall (N11-1
 *     dropped).</li>
 *     <li>Placement is rejected inside a recognized tank (N16-2); the item description says so, and
 *     the wire rule (N11-3, N11-6).</li>
 * </ul>
 */
public class TankValveObject extends TankBorderBlockObject {

    public TankValveObject(String textureName) {
        // Minimap color: the base shade of the texture palette (art choice, not a design value).
        super(textureName, new Color(158, 170, 184));
        showsWire = true;
    }

    @Override
    public Item generateNewObjectItem() {
        return new TankValveObjectItem(this);
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new TankValveObjectEntity(level, x, y);
    }

    /** The picked-up valve keeps its tier (N13-5 ②). */
    @Override
    public LootTable getLootTable(Level level, int layerID, int tileX, int tileY) {
        TankValveObjectEntity valve = getCurrentObjectEntity(level, tileX, tileY, TankValveObjectEntity.class);
        if (valve == null) {
            return super.getLootTable(level, layerID, tileX, tileY);
        }
        return new LootTable(new LootItem(getStringID(), TankValveObjectItem.tierData(valve.getTier()))
                .preventLootMultiplier());
    }

    @Override
    public void onWireUpdate(Level level, int layerID, int tileX, int tileY, int wireID, boolean active) {
        TankValveObjectEntity valve = getCurrentObjectEntity(level, tileX, tileY, TankValveObjectEntity.class);
        if (valve != null) {
            valve.updateWireSignal();
        }
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // Tier (N13-5 ②). The recipe's display item has no tier yet: it comes from the wall used.
        if (item.getGndData().getBoolean(TankValveObjectItem.TIER_FROM_WALL_KEY)) {
            tooltips.add(Localization.translate("itemtooltip", "tankvalvetierfromwall"), 400);
        } else {
            MineralTier tier = TankValveObjectItem.getTier(item);
            tooltips.add(new LocalMessage("itemtooltip", "tankvalvetier", "wall",
                    MineralWallObject.displayNameOf(tier)).translate(), 400);
        }
        // Placement rejection rules and the wire rule (N11-6): off while receiving a wire signal
        // (N11-3); not inside a recognized tank (N16-2).
        tooltips.add(Localization.translate("itemtooltip", "tankvalvetip"), 400);
        tooltips.add(TankInteriorPlacement.rejectedTooltip(), 400);
        return tooltips;
    }

}
