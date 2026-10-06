package devp0tion.mechanics.objects;

import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.tank.TankValveObjectEntity;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryItemsRemoved;
import necesse.inventory.item.Item;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.inventory.recipe.RecipeCraftedEvent;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

import java.util.Locale;
import java.util.Objects;

/**
 * The tank valve's item: one item for every tier (N13-5 ②). The tier is the mineral wall the valve
 * was crafted from, kept in the item data ({@link #TIER_KEY}).
 *
 * <ul>
 *     <li>Crafting: the valve recipe takes any mineral wall (N10-3); {@link #onCrafted} reads which
 *     wall was used from the recipe's crafted event (vanilla {@code Recipe.onCrafted}) and stores its
 *     tier in the crafted valve.</li>
 *     <li>Placing keeps the tier in the valve's object entity ({@link #onPlaceObject}); picking the
 *     valve up drops an item with that tier ({@code TankValveObject.getLootTable}).</li>
 *     <li>Valves of different tiers do not stack.</li>
 *     <li>The tooltip shows the tier ({@code TankValveObject.getItemTooltips}).</li>
 * </ul>
 */
public class TankValveObjectItem extends ObjectItem {

    /** Item data key of the valve's tier: the lower-case {@link MineralTier} name, e.g. {@code iron}. */
    public static final String TIER_KEY = "mineraltier";

    /**
     * Item data key set only on the recipe's display item: the tier will be that of the mineral wall
     * used, so the recipe tooltip says so instead of showing a tier. Removed when crafted.
     */
    public static final String TIER_FROM_WALL_KEY = "tierfromwall";

    /**
     * The tier of a valve item without tier data (made in creative mode or with commands, not
     * crafted) and of a placed valve without a saved tier (placed before tiers existed).
     * TODO(design): provisional, the lowest tier (copper); the value for such valves is undecided.
     */
    public static final MineralTier DEFAULT_TIER = MineralTier.COPPER;

    public TankValveObjectItem(TankValveObject object) {
        super(object);
    }

    /** The tier stored in item data, or {@code null} when there is none. */
    public static MineralTier getStoredTier(GNDItemMap data) {
        String name = data.hasKey(TIER_KEY) ? data.getString(TIER_KEY) : null;
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return MineralTier.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The valve item's tier: the stored one, or {@link #DEFAULT_TIER}. */
    public static MineralTier getTier(InventoryItem item) {
        MineralTier tier = getStoredTier(item.getGndData());
        return tier == null ? DEFAULT_TIER : tier;
    }

    /** Stores {@code tier} in item data. */
    public static void setTier(GNDItemMap data, MineralTier tier) {
        data.setString(TIER_KEY, tier.name().toLowerCase(Locale.ROOT));
    }

    /** Item data holding {@code tier}. */
    public static GNDItemMap tierData(MineralTier tier) {
        GNDItemMap data = new GNDItemMap();
        setTier(data, tier);
        return data;
    }

    /**
     * The valve recipe's crafted listener: stores the tier of the mineral wall used (the recipe's
     * "any mineral wall" ingredient, N10-3, exactly one wall) in the crafted valve (N13-5 ②).
     */
    public static void onCrafted(RecipeCraftedEvent event) {
        GNDItemMap data = event.resultItem.getGndData();
        data.clearItem(TIER_FROM_WALL_KEY);
        MineralTier tier = null;
        if (event.itemsUsed != null) {
            for (InventoryItemsRemoved used : event.itemsUsed) {
                MineralTier wallTier = wallTierOf(used.invItem == null ? null : used.invItem.item);
                if (wallTier != null) {
                    // One wall per valve (N10-3); the lowest is kept should several ever be used.
                    tier = tier == null ? wallTier : MineralTier.lowest(tier, wallTier);
                }
            }
        }
        if (tier != null) {
            setTier(data, tier);
        }
    }

    /** The tier of a mineral wall item, or {@code null} for any other item. */
    private static MineralTier wallTierOf(Item item) {
        if (item instanceof ObjectItem) {
            GameObject object = ((ObjectItem) item).getObject();
            if (object instanceof MineralWallObject) {
                return ((MineralWallObject) object).getMineralTier();
            }
        }
        return null;
    }

    @Override
    public boolean canCombineItem(Level level, PlayerMob player, InventoryItem me, InventoryItem them, String purpose) {
        return super.canCombineItem(level, player, me, them, purpose) && isSameGNDData(level, me, them, purpose);
    }

    @Override
    public boolean isSameGNDData(Level level, InventoryItem me, InventoryItem them, String purpose) {
        return Objects.equals(getStoredTier(me.getGndData()), getStoredTier(them.getGndData()));
    }

    @Override
    public boolean onPlaceObject(GameObject object, Level level, int layerID, int tileX, int tileY, int rotation,
                                 ServerClient client, InventoryItem item) {
        boolean placed = super.onPlaceObject(object, level, layerID, tileX, tileY, rotation, client, item);
        if (placed) {
            TankValveObjectEntity valve = level.entityManager.getObjectEntity(tileX, tileY, TankValveObjectEntity.class);
            if (valve != null) {
                valve.setTier(getTier(item));
            }
        }
        return placed;
    }

}
