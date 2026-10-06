package devp0tion.mechanics.objects;

import devp0tion.mechanics.client.PipeRendering;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.pipe.UndergroundPipeLayer;
import devp0tion.mechanics.registry.MechanicsIngredients;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectHoverHitbox;
import necesse.level.maps.Level;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Underground pipe (지하 파이프, 9-1, 10-3): one per bar tier (9-11, N10-1), a separate item on the
 * mod's own object layer ({@link UndergroundPipeLayer}).
 *
 * <ul>
 *     <li>Passes under anything: walls, objects, liquid tiles and tank interiors (10-4, N15-4,
 *     N16-1, N17-4); base layer objects never see it.</li>
 *     <li>Links to adjacent underground pipes automatically (9-4); to the basic pipe on its tile
 *     only through the wrench, starting cut (N16-4); to the tank valve on its tile automatically
 *     (9-9, 13-5); never to pumps (9-9). Its state lives in the level store (N14-4).</li>
 *     <li>Visible only while the player holds the wrench or an underground pipe item (9-6, 10-6).</li>
 *     <li>Removed only with the wrench (10-5): unbreakable for every tool, and it has no hover box,
 *     so it is never targeted by mining or interaction.</li>
 *     <li>Belongs to the "any pipe" ingredient group (N10-3).</li>
 * </ul>
 */
public class UndergroundPipeObject extends GameObject {

    /** Sort offset that draws visible underground pipes over every object, like the vanilla wires. */
    private static final int OVERLAY_SORT = 1_000_000;

    private final MineralTier tier;
    private final String textureName;

    /** Loaded on clients only. */
    protected GameTexture texture;

    public UndergroundPipeObject(MineralTier tier, String stringID, Color mapColor) {
        super(new Rectangle());
        this.tier = tier;
        this.textureName = stringID;
        this.mapColor = mapColor;
        validObjectLayers.clear();
        validObjectLayers.add(UndergroundPipeLayer.ID);
        toolType = ToolType.UNBREAKABLE;
        drawDamage = false;
        isLightTransparent = true;
        canPlaceOnLiquid = true;
        canPlaceOnShore = true;
        addGlobalIngredient(MechanicsIngredients.ANY_PIPE);
    }

    /** The pipe's stringID (also its item's and texture's name): {@code <mineral>undergroundpipe}. */
    public static String stringIDOf(MineralTier tier) {
        return tier.name().toLowerCase(Locale.ROOT) + "undergroundpipe";
    }

    public MineralTier getMineralTier() {
        return tier;
    }

    @Override
    public GameMessage getNewLocalization() {
        return new LocalMessage("object", "undergroundpipe", "bar", new LocalMessage("item", tier.getBarStringID()));
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        texture = GameTexture.fromFile("objects/" + textureName);
        PipeRendering.loadTextures();
    }

    @Override
    public List<ObjectHoverHitbox> getHoverHitboxes(Level level, int layerID, int tileX, int tileY) {
        return Collections.emptyList();
    }

    @Override
    public void addLayerDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, int layerID,
                                  int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        if (!PipeRendering.undergroundVisible(perspective)) {
            return;
        }
        int links = PipeRendering.undergroundLinks(level, tileX, tileY);
        final DrawOptionsList options = PipeRendering.pipeOptions(level, tileX, tileY, camera.getTileDrawX(tileX),
                camera.getTileDrawY(tileY), texture, links, PipeRendering.undergroundBlockedSides(level, tileX, tileY), true,
                true, 0.85f);
        list.add(new LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return OVERLAY_SORT;
            }

            @Override
            public void draw(TickManager tickManager) {
                options.draw();
            }
        });
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        texture.initDraw().section(0, 32, 0, 32).alpha(alpha).draw(camera.getTileDrawX(tileX), camera.getTileDrawY(tileY));
    }

}
