package devp0tion.mechanics.objects;

import devp0tion.mechanics.client.PipeRendering;
import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.Pump;
import devp0tion.mechanics.core.PumpForm;
import devp0tion.mechanics.core.PumpSprites;
import devp0tion.mechanics.core.PumpTier;
import devp0tion.mechanics.pipe.PumpObjectEntity;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.registries.ContainerRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.container.object.OEInventoryContainer;
import necesse.inventory.item.Item;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

/**
 * Pumps (펌프, 11-3, 11-6): the manual pump (수동 펌프), the fire pump (화력 펌프) and the advanced
 * fire pump (고급 화력 펌프). Their state and logic are in {@link PumpObjectEntity} and the core
 * {@link Pump}.
 *
 * <ul>
 *     <li>Its output side is the direction the player faced when placing it, the object's rotation
 *     (N36-1, N36-10). Its form (N36-6) says where it pulls from: a ground pump from the liquid tile
 *     under it, a valve pump from the tank valve behind it (11-1, N36-3); may be placed on liquid,
 *     also the deep sea (11-9). The form is the item's data, switched in the inventory
 *     ({@link PumpObjectItem}, N36-11); picking the pump up drops an item of its form
 *     ({@link #getLootTable}), so form and direction change only by placing it again (N36-7, N36-12).</li>
 *     <li>Placement is rejected inside a recognized tank (N16-1); the item description says so
 *     (N11-6). No source, or a source of another fluid, refuses nothing (N36-15, N36-16, N36-53,
 *     replacing N17-1, N36-8). Placing the same pump over a placed one with another rotation does
 *     not replace it: it is picked up first (N36-12, N36-33; not in the description, N36-43).</li>
 *     <li>The manual pump pumps once per click (interact, 11-3). The log-fuelled pumps take logs
 *     both ways (N31-4): right clicked while the player holds logs that can go into their fuel
 *     slot, they take as many of them as fit from the held stack; otherwise, also while holding
 *     logs that cannot go in (the slot is full or holds another kind of log), the right click opens
 *     their fuel slot window (N31-11). The interaction hint reads "연료 넣기" / "Add fuel" while
 *     the held logs can go in, else the vanilla "Open" (N31-12). They are switched off by a wire
 *     signal (11-3, N11-3, in the item description).</li>
 *     <li>Pushes only into the basic pipe in front of it, or into the tank of the valve in front of it
 *     (9-3, 9-9, N36-1, N36-4). Cut links to valves are drawn on the pump (N16-4), only on the sides
 *     that link to a valve; its other sides are a wall for the parts beside them: no pipe arm,
 *     collision part or cut mark for the pump's flag there (N36-37, {@link PipeRendering#baseLinksFacing}).</li>
 * </ul>
 * Texture {@code objects/<stringID>.png}: a 128x128 sheet with a 32x64 cell (like the tank parts) per
 * rotation and form, the body drawn turned toward each output side ({@link PumpSprites}, N36-38,
 * N36-39, N36-41), also as the placement preview (N36-40); item icons per form
 * {@code items/<stringID>.png} (valve) and {@code items/<stringID>ground.png} (ground, N36-39).
 * A full-tile block like the tank parts: it blocks movement (N31-1) and is mined with any pickaxe,
 * tier 0, the engine default (N31-2).
 */
public class PumpObject extends GameObject {

    /** The interaction hint while held logs can go into the fuel slot, {@code [controls]} (N31-12). */
    public static final String ADD_FUEL_TIP = "mechanicsaddfueltip";

    private final PumpTier tier;
    private final String textureName;

    /** Loaded on clients only. */
    protected GameTexture texture;

    public PumpObject(PumpTier tier, String stringID, Color mapColor) {
        super(new Rectangle(32, 32));
        this.tier = tier;
        this.textureName = stringID;
        this.mapColor = mapColor;
        hoverHitbox = new Rectangle(0, -32, 32, 64);
        canPlaceOnLiquid = true;
        canPlaceOnShore = true;
        showsWire = tier.isWireControllable();
        // N36-33: no vanilla replacement of a placed pump by the same pump with another rotation (N36-12).
        replaceRotations = false;
    }

    public PumpTier getTier() {
        return tier;
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        texture = GameTexture.fromFile("objects/" + textureName);
        PipeRendering.loadTextures();
    }

    @Override
    public Item generateNewObjectItem() {
        return new PumpObjectItem(this);
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new PumpObjectEntity(level, x, y, tier);
    }

    /** The picked-up pump keeps its form (N36-7, N36-12): placed again, it gets a new direction. */
    @Override
    public LootTable getLootTable(Level level, int layerID, int tileX, int tileY) {
        PumpObjectEntity pump = getCurrentObjectEntity(level, tileX, tileY, PumpObjectEntity.class);
        if (pump == null) {
            return super.getLootTable(level, layerID, tileX, tileY);
        }
        return new LootTable(new LootItem(getStringID(), PumpObjectItem.formData(pump.getForm()))
                .preventLootMultiplier());
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean canInteract(Level level, int x, int y, PlayerMob player) {
        return true;
    }

    /** Both sides: the slot's contents are synced to clients (vanilla inventory sync). */
    @Override
    public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
        if (!tier.usesLogFuel()) {
            return Localization.translate("controls", "usetip");
        }
        // N31-12: "Add fuel" while the held logs can go in, else the vanilla "Open".
        PumpObjectEntity pump = getCurrentObjectEntity(level, x, y, PumpObjectEntity.class);
        return Localization.translate("controls", canInsertHeldLogs(level, perspective, pump) ? ADD_FUEL_TIP : "opentip");
    }

    @Override
    public void interact(Level level, int x, int y, PlayerMob player) {
        super.interact(level, x, y, player);
        if (!level.isServer()) {
            // Server authority: the click and the container only happen on the server (D4).
            return;
        }
        if (tier.usesLogFuel()) {
            PumpObjectEntity pump = getCurrentObjectEntity(level, x, y, PumpObjectEntity.class);
            if (canInsertHeldLogs(level, player, pump)) {
                insertHeldLogs(level, player, pump);
                return;
            }
            // Nothing held that can go in (no logs, the slot full, another kind of log): the window (N31-11).
            OEInventoryContainer.openAndSendContainer(ContainerRegistry.OE_INVENTORY_CONTAINER, player.getServerClient(), level, x, y);
        } else {
            PumpObjectEntity pump = getCurrentObjectEntity(level, x, y, PumpObjectEntity.class);
            if (pump != null) {
                pump.click();
            }
        }
    }

    /**
     * Whether the player holds logs of which at least one can go into the pump's fuel slot (N31-4,
     * N31-11, N31-12): not when the slot is full or holds another kind of log. Both sides.
     */
    static boolean canInsertHeldLogs(Level level, PlayerMob player, PumpObjectEntity pump) {
        if (player == null || pump == null || !pump.getTier().usesLogFuel()) {
            return false;
        }
        InventoryItem held = player.getSelectedItemSlot().getItem(player.getInv());
        return held != null && pump.isItemValid(0, held) && pump.inventory.canAddItem(level, player, held, FUEL_PURPOSE) > 0;
    }

    /**
     * Log fuel by right click (N31-4, server): as many of the held logs as fit go into the pump's
     * fuel slot, taken from the held stack. Called when {@link #canInsertHeldLogs}; the window does
     * not open then.
     */
    static void insertHeldLogs(Level level, PlayerMob player, PumpObjectEntity pump) {
        PlayerInventorySlot slot = player.getSelectedItemSlot();
        InventoryItem held = slot.getItem(player.getInv());
        pump.inventory.addItem(level, player, held, FUEL_PURPOSE);
        if (held.getAmount() <= 0) {
            slot.setItem(player.getInv(), null);
        }
        slot.markDirty(player.getInv());
    }

    /** The inventory purpose of logs put in by right click. */
    private static final String FUEL_PURPOSE = "pumpfuel";

    @Override
    public void onWireUpdate(Level level, int layerID, int tileX, int tileY, int wireID, boolean active) {
        PumpObjectEntity pump = getCurrentObjectEntity(level, tileX, tileY, PumpObjectEntity.class);
        if (pump != null) {
            pump.updateWire();
        }
    }

    // ------------------------------------------------------------------ drawing

    /** The output side of the pump at a tile: the object's rotation (N36-10), which the engine syncs to clients. */
    public static Direction frontAt(Level level, int tileX, int tileY) {
        return Direction.values()[level.getObjectRotation(tileX, tileY) & 3];
    }

    /**
     * The form of the pump at a tile (N36-6): its object entity's, ground without one (N36-32). Clients
     * read the form their entity was sent ({@link PumpObjectEntity#getForm}).
     */
    public static PumpForm formAt(Level level, int tileX, int tileY) {
        ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
        return entity instanceof PumpObjectEntity ? ((PumpObjectEntity) entity).getForm() : PumpForm.GROUND;
    }

    /**
     * The cell of its rotation and form (N36-38, N36-39, N36-41; {@link PumpSprites}), 32 px above the
     * tile, and the cut marks toward valves on the sides that link to one (N16-4, N36-37).
     */
    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, int tileX, int tileY,
                             TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameLight light = level.getLightLevel(tileX, tileY);
        PumpObjectEntity pump = getCurrentObjectEntity(level, tileX, tileY, PumpObjectEntity.class);
        Direction front = frontAt(level, tileX, tileY);
        PumpForm form = pump == null ? PumpForm.GROUND : pump.getForm();
        int[] cell = PumpSprites.section(front.ordinal(), form);
        final TextureDrawOptions options = texture.initDraw()
                .section(cell[0], cell[1], cell[2], cell[3])
                .addObjectDamageOverlay(this, level, tileX, tileY)
                .light(light)
                .pos(drawX, drawY - 32);
        final DrawOptionsList cuts = PipeRendering.pumpCutOptions(level, tileX, tileY, drawX, drawY,
                pump == null ? LinkFlags.ALL_OPEN : pump.getLinks(), front, form);
        list.add(new LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return 16;
            }

            @Override
            public void draw(TickManager tickManager) {
                options.draw();
                cuts.draw();
            }
        });
    }

    /**
     * The placement preview looks as the pump will once placed (N36-40): the cell of the placement
     * rotation and of the form of the held pump item, the one being placed ({@link PumpObjectItem#getForm};
     * ground when no pump item is held, e.g. a preset's preview, N36-32, N36-35).
     */
    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        InventoryItem held = player == null ? null : player.getSelectedItem();
        PumpForm form = held != null && held.item instanceof PumpObjectItem
                ? PumpObjectItem.getForm(held) : PumpForm.GROUND;
        int[] cell = PumpSprites.section(rotation, form);
        texture.initDraw().section(cell[0], cell[1], cell[2], cell[3]).alpha(alpha)
                .draw(camera.getTileDrawX(tileX), camera.getTileDrawY(tileY) - 32);
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // The form and how to switch it (N36-42, N36-54); no direction line.
        boolean valve = PumpObjectItem.getForm(item) == PumpForm.VALVE;
        tooltips.add(Localization.translate("itemtooltip", valve ? "pumpformvalvetip" : "pumpformgroundtip"), 400);
        tooltips.add(Localization.translate("itemtooltip", "pumpformswitchtip"), 400);
        // Placement rejection rule (N11-6): not inside a recognized tank (N16-1); the sources rule
        // (N17-1) is gone with N36-8. The wire rule for tier 2 and up (N11-3).
        tooltips.add(TankInteriorPlacement.rejectedTooltip(), 400);
        if (tier.isWireControllable()) {
            tooltips.add(Localization.translate("itemtooltip", "pumpwiretip"), 400);
        }
        return tooltips;
    }

}
