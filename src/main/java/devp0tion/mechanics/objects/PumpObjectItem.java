package devp0tion.mechanics.objects;

import devp0tion.mechanics.core.PumpForm;
import devp0tion.mechanics.pipe.PumpObjectEntity;
import necesse.engine.localization.Localization;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventoryManager;
import necesse.inventory.container.Container;
import necesse.inventory.container.ContainerActionResult;
import necesse.inventory.container.slots.ContainerSlot;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

import java.util.function.Supplier;

/**
 * The pumps' item: the form (N36-6) is item data ({@link #FORM_KEY}), chosen before placing (N36-11).
 *
 * <ul>
 *     <li>A right click on the item in the player's own inventory or cloud inventory slot switches the
 *     form, valve to ground and back (N36-11, N36-65), the same on client and server. In any other
 *     slot (a chest, ...) nothing changes: the vanilla right click stays.</li>
 *     <li>Without form data (the creative item tab, {@code /give}) it is a ground pump (N36-32);
 *     crafted pumps are valve pumps (N36-14, the recipes' item data).</li>
 *     <li>Placing keeps the form in the pump's object entity ({@link #onPlaceObject}); picking the
 *     pump up drops an item of that form ({@code PumpObject.getLootTable}, N36-7).</li>
 *     <li>Pumps of different forms do not stack (N36-13).</li>
 *     <li>Icons per form (N36-39): {@code items/<stringID>.png} for the valve form,
 *     {@code items/<stringID>ground.png} for the ground form. The tooltip shows the form and how to
 *     switch it ({@code PumpObject.getItemTooltips}, N36-42, N36-54).</li>
 * </ul>
 */
public class PumpObjectItem extends ObjectItem {

    /** Item data key of the form (N36-11): {@link PumpForm#saveName()}; none means ground (N36-32). */
    public static final String FORM_KEY = "pumpform";

    /** The interaction hint of the right click in an own inventory slot, {@code [controls]}. */
    public static final String SWITCH_FORM_TIP = "mechanicspumpformtip";

    /**
     * The right click's results, one per new form: client and server compare them, and a mismatch
     * resends the inventory (vanilla {@code PacketContainerAction}). Not 0, which means nothing happened.
     */
    private static final int SWITCHED_TO_VALVE = 1;
    private static final int SWITCHED_TO_GROUND = 2;

    /** The ground form's icon; the valve form's is the item's own texture. Loaded on clients only. */
    protected GameTexture groundTexture;

    public PumpObjectItem(PumpObject object) {
        super(object);
    }

    /** The pump item's form: the stored one, or ground without one (N36-32). */
    public static PumpForm getForm(InventoryItem item) {
        GNDItemMap data = item == null ? null : item.getGndData();
        return PumpForm.fromSaveName(data != null && data.hasKey(FORM_KEY) ? data.getString(FORM_KEY) : null);
    }

    /** Stores {@code form} in item data. */
    public static void setForm(GNDItemMap data, PumpForm form) {
        data.setString(FORM_KEY, form.saveName());
    }

    /** Item data holding {@code form}. */
    public static GNDItemMap formData(PumpForm form) {
        GNDItemMap data = new GNDItemMap();
        setForm(data, form);
        return data;
    }

    // ------------------------------------------------------------------ form switch (N36-11, N36-65)

    /**
     * Whether the slot is one of the player's own inventory or cloud inventory slots, where the form
     * switches (N36-65, the vanilla pouches' range). Both sides see the same slot.
     */
    private static boolean isOwnInventorySlot(Container container, ContainerSlot slot) {
        PlayerMob player = container.getClient() == null ? null : container.getClient().playerMob;
        if (player == null || slot == null) {
            return false;
        }
        PlayerInventoryManager inv = player.getInv();
        return slot.getInventory() == inv.main || slot.getInventory() == inv.cloud;
    }

    @Override
    public String getInventoryRightClickControlTip(Container container, InventoryItem item, int slotIndex, ContainerSlot slot) {
        return isOwnInventorySlot(container, slot) ? Localization.translate("controls", SWITCH_FORM_TIP) : null;
    }

    /**
     * Every right click switches the slot's pumps to the other form (N36-65); the whole stack
     * switches. No action in other slots, so their vanilla right click (split a stack) stays: the
     * client then sends the plain right click, which the server applies without asking the item.
     */
    @Override
    public Supplier<ContainerActionResult> getInventoryRightClickAction(Container container, InventoryItem item, int slotIndex, ContainerSlot slot) {
        if (!isOwnInventorySlot(container, slot)) {
            return null;
        }
        return () -> {
            InventoryItem current = slot.getItem();
            if (current == null || current.item != this) {
                return new ContainerActionResult(null);
            }
            PumpForm next = getForm(current) == PumpForm.VALVE ? PumpForm.GROUND : PumpForm.VALVE;
            setForm(current.getGndData(), next);
            // The server sends the slot to the clients (vanilla inventory sync).
            slot.markDirty();
            return new ContainerActionResult(next == PumpForm.VALVE ? SWITCHED_TO_VALVE : SWITCHED_TO_GROUND);
        };
    }

    // ------------------------------------------------------------------ stacking (N36-13)

    @Override
    public boolean canCombineItem(Level level, PlayerMob player, InventoryItem me, InventoryItem them, String purpose) {
        return super.canCombineItem(level, player, me, them, purpose) && isSameGNDData(level, me, them, purpose);
    }

    /** The same form, also between a stored ground form and none (N36-32). */
    @Override
    public boolean isSameGNDData(Level level, InventoryItem me, InventoryItem them, String purpose) {
        return getForm(me) == getForm(them);
    }

    // ------------------------------------------------------------------ placing (N36-11)

    /**
     * The form goes to the new pump's object entity; the pump registers in its first tick, with the
     * form set by then ({@link PumpObjectEntity#setForm}).
     */
    @Override
    public boolean onPlaceObject(GameObject object, Level level, int layerID, int tileX, int tileY, int rotation,
                                 ServerClient client, InventoryItem item) {
        boolean placed = super.onPlaceObject(object, level, layerID, tileX, tileY, rotation, client, item);
        if (placed) {
            PumpObjectEntity pump = level.entityManager.getObjectEntity(tileX, tileY, PumpObjectEntity.class);
            if (pump != null) {
                pump.setForm(getForm(item));
            }
        }
        return placed;
    }

    // ------------------------------------------------------------------ icons (N36-39)

    @Override
    public void loadItemTextures() {
        // items/<stringID>: the valve form.
        super.loadItemTextures();
        groundTexture = GameTexture.fromFile("items/" + getStringID() + "ground");
    }

    @Override
    public GameSprite getItemSprite(InventoryItem item, PlayerMob perspective) {
        if (getForm(item) == PumpForm.GROUND && groundTexture != null) {
            return new GameSprite(groundTexture);
        }
        return super.getItemSprite(item, perspective);
    }

}
