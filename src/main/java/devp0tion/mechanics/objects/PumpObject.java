package devp0tion.mechanics.objects;

import devp0tion.mechanics.client.PipeRendering;
import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.Pump;
import devp0tion.mechanics.core.PumpTier;
import devp0tion.mechanics.pipe.LevelLiquidTileLookup;
import devp0tion.mechanics.pipe.PumpObjectEntity;
import devp0tion.mechanics.tank.TankControllerObjectEntity;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import devp0tion.mechanics.tank.TankRegistry;
import devp0tion.mechanics.tank.TankValveObjectEntity;
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
import necesse.inventory.container.object.OEInventoryContainer;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Pumps (펌프, 11-3, 11-6): the manual pump (수동 펌프), the fire pump (화력 펌프) and the advanced
 * fire pump (고급 화력 펌프). Their state and logic are in {@link PumpObjectEntity} and the core
 * {@link Pump}.
 *
 * <ul>
 *     <li>Pulls from the liquid tile under it and from tank valves linked to its sides (11-1,
 *     N16-3); may be placed on liquid, also the deep sea (11-9).</li>
 *     <li>Placement is rejected when the sources it would connect hold different fluids (N17-1),
 *     and inside a recognized tank (N16-1); the item description says so (N11-6).</li>
 *     <li>The manual pump pumps once per click (interact, 11-3); the fire pumps open their fuel slot
 *     (TODO(confirm), see {@link PumpObjectEntity}) and are switched off by a wire signal (11-3,
 *     N11-3, in the item description).</li>
 *     <li>Pushes only into adjacent basic pipes (9-3, 9-9). Cut links to valves are drawn on the
 *     pump (N16-4).</li>
 * </ul>
 * Texture {@code objects/<stringID>.png}: one 32x64 sprite like the tank parts; item icon
 * {@code items/<stringID>.png} (drawn by {@code tools/textures/draw_pipes_pumps.py}).
 * TODO(design): whether a pump blocks movement and the tool tier needed to mine it are undecided;
 * it is a full-tile block like the tank parts, mined with any pickaxe.
 */
public class PumpObject extends GameObject {

    /** canPlace error when the sources would hold different fluids (N17-1). */
    public static final String MIXED_SOURCES_ERROR = "pumpmixedsources";

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
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new PumpObjectEntity(level, x, y, tier);
    }

    // ------------------------------------------------------------------ placement (N17-1)

    @Override
    public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
        String error = super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
        if (error != null) {
            return error;
        }
        return Pump.canConnectSources(sourceFluidsIfPlaced(level, x, y)) ? null : MIXED_SOURCES_ERROR;
    }

    /**
     * The fluids of the sources a pump placed at the tile would connect: the liquid tile under it
     * and the tanks of the valves next to it whose side toward it is not cut. Both sides read the
     * same synced state (valve flags and the controller's fluid view); the server's answer is final
     * and a rejected client prediction is corrected ({@code PlacementCorrection}).
     */
    public static List<FluidType> sourceFluidsIfPlaced(Level level, int x, int y) {
        List<FluidType> fluids = new ArrayList<>();
        if (level.regionManager.isTileLoaded(x, y)) {
            fluids.add(LevelLiquidTileLookup.fluidAt(level, x, y));
        }
        for (Direction d : Direction.values()) {
            TankValveObjectEntity valve = level.entityManager.getObjectEntity(x + d.dx, y + d.dy, TankValveObjectEntity.class);
            if (valve == null || !LinkFlags.isSideOpen(valve.getLinks(), d.opposite())) {
                continue;
            }
            TankControllerObjectEntity controller = TankRegistry.findValveController(level, x + d.dx, y + d.dy, valve.getOwner());
            fluids.add(controller == null ? null : controller.getFluid());
        }
        return fluids;
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean canInteract(Level level, int x, int y, PlayerMob player) {
        return true;
    }

    @Override
    public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
        return Localization.translate("controls", tier.usesLogFuel() ? "opentip" : "usetip");
    }

    @Override
    public void interact(Level level, int x, int y, PlayerMob player) {
        super.interact(level, x, y, player);
        if (!level.isServer()) {
            // Server authority: the click and the container only happen on the server (D4).
            return;
        }
        if (tier.usesLogFuel()) {
            OEInventoryContainer.openAndSendContainer(ContainerRegistry.OE_INVENTORY_CONTAINER, player.getServerClient(), level, x, y);
        } else {
            PumpObjectEntity pump = getCurrentObjectEntity(level, x, y, PumpObjectEntity.class);
            if (pump != null) {
                pump.click();
            }
        }
    }

    @Override
    public void onWireUpdate(Level level, int layerID, int tileX, int tileY, int wireID, boolean active) {
        PumpObjectEntity pump = getCurrentObjectEntity(level, tileX, tileY, PumpObjectEntity.class);
        if (pump != null) {
            pump.updateWire();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, int tileX, int tileY,
                             TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameLight light = level.getLightLevel(tileX, tileY);
        final TextureDrawOptions options = texture.initDraw()
                .addObjectDamageOverlay(this, level, tileX, tileY)
                .light(light)
                .pos(drawX, drawY - 32);
        PumpObjectEntity pump = getCurrentObjectEntity(level, tileX, tileY, PumpObjectEntity.class);
        final DrawOptionsList cuts = PipeRendering.pumpCutOptions(level, tileX, tileY, drawX, drawY,
                pump == null ? LinkFlags.ALL_OPEN : pump.getLinks());
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

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        texture.initDraw().alpha(alpha).draw(camera.getTileDrawX(tileX), camera.getTileDrawY(tileY) - 32);
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // Placement rejection rules (N11-6): sources of different fluids (N17-1); not inside a
        // recognized tank (N16-1). The wire rule for tier 2 and up (N11-3).
        tooltips.add(Localization.translate("itemtooltip", "pumpsourcestip"), 400);
        tooltips.add(TankInteriorPlacement.rejectedTooltip(), 400);
        if (tier.isWireControllable()) {
            tooltips.add(Localization.translate("itemtooltip", "pumpwiretip"), 400);
        }
        return tooltips;
    }

}
