package devp0tion.mechanics.objects;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

/**
 * A tank part that takes the place of a mineral wall in a multiblock tank's border (5-13): the
 * tank controller and the tank valve. A full-tile block like a wall.
 *
 * <p>Texture {@code objects/<textureName>.png}: one 32x64 sprite, the lower 32 pixels on the tile
 * and the upper 32 above it (the 1x1 tall-object layout of the ExampleMod's ExampleObject), drawn
 * by {@code tools/textures/draw_tank_parts.py}. Item icon {@code items/<stringID>.png}.
 *
 * <p>TODO(design): the tool and tier needed to mine the tank parts are undecided; the engine
 * default is used (pickaxe, tier 0).
 */
public abstract class TankBorderBlockObject extends GameObject {

    private final String textureName;

    /** Loaded on clients only; always null on a dedicated server. */
    protected GameTexture texture;

    protected TankBorderBlockObject(String textureName, Color mapColor) {
        super(new Rectangle(32, 32));
        this.textureName = textureName;
        this.mapColor = mapColor;
        hoverHitbox = new Rectangle(0, -32, 32, 64);
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
                .pos(drawX, drawY - 32);
        list.add(new LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return 16;
            }

            @Override
            public void draw(TickManager tickManager) {
                options.draw();
            }
        });
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha,
                            PlayerMob player, GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        texture.initDraw()
                .alpha(alpha)
                .draw(drawX, drawY - 32);
    }

}
