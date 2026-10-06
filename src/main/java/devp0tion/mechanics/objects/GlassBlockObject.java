package devp0tion.mechanics.objects;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

/**
 * Glass block (유리 블럭): fills a multiblock tank's interior (2-1, 2-2) in interior condition (1)
 * all glass, or (3) on the tank floor (5-5, 5-11).
 *
 * <p>Drawn with its own texture by the normal object drawing (6-7), in the object tile list: after
 * the tiles and the tank fluid, which the liquid shader draws in the tile stage under it (S4), and
 * before the sorted objects. The texture {@code objects/<stringID>.png} (32x32) is partly
 * transparent so the fluid shows through; drawn by {@code tools/textures/draw_tank_parts.py}.
 *
 * <p>Inside a recognized tank only glass blocks (and the tank floor tile and underground pipes, once
 * they exist) may be placed (N16-2); the item description says so (N11-6).
 *
 * <p>TODO(design): whether a glass block blocks movement is undecided; it has a full-tile
 * collision like the other blocks.
 * <p>TODO(design): the tool and tier needed to mine it are undecided; the engine default is used
 * (pickaxe, tier 0).
 */
public class GlassBlockObject extends GameObject {

    private final String textureName;

    /** Loaded on clients only; always null on a dedicated server. */
    protected GameTexture texture;

    public GlassBlockObject(String textureName) {
        super(new Rectangle(32, 32));
        this.textureName = textureName;
        // Minimap color: the base shade of the texture palette (art choice, not a design value).
        mapColor = new Color(170, 214, 230);
        isLightTransparent = true;
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        texture = GameTexture.fromFile("objects/" + textureName);
    }

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList,
                             Level level, int tileX, int tileY,
                             TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameLight light = level.getLightLevel(tileX, tileY);
        final TextureDrawOptions options = texture.initDraw()
                .addObjectDamageOverlay(this, level, tileX, tileY)
                .light(light)
                .pos(drawX, drawY);
        tileList.add(tm -> options.draw());
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha,
                            PlayerMob player, GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        texture.initDraw()
                .alpha(alpha)
                .draw(drawX, drawY);
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // N11-6: the interior placement rule (N16-2) from the glass block's side.
        tooltips.add(Localization.translate("itemtooltip", "glassblocktip"), 400);
        return tooltips;
    }

}
