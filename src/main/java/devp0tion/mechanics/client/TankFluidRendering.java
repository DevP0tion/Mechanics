package devp0tion.mechanics.client;

import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.tank.TankControllerObjectEntity;
import devp0tion.mechanics.tank.TankRegistry;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.drawables.LevelDrawUtils;
import necesse.gfx.drawables.LevelTileDamageDrawOptions;
import necesse.gfx.drawables.LevelTileLightDrawOptions;
import necesse.gfx.drawables.LevelTileLiquidDrawOptions;
import necesse.gfx.drawables.LevelTileTerrainDrawOptions;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.level.gameTile.GameTile;
import necesse.level.gameTile.LiquidTile;
import necesse.level.maps.Level;
import necesse.level.maps.liquidManager.LiquidManager;
import necesse.level.maps.regionSystem.Region;
import net.bytebuddy.asm.Advice;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * Draws the fluid of recognized tanks with the vanilla liquid wave shader, in every interior
 * condition (6-3, 6-7). Glass blocks draw their own partly transparent texture on top of it
 * ({@code GlassBlockObject}). How the vanilla liquid drawing works: domain rules S3, S4.
 *
 * <p>Two patches (S4), both only acting on client levels:
 * <ol>
 *     <li>{@link LiquidDrawablesPatch}: after the level has collected its tile-stage draw lists
 *     ({@code LevelDrawUtils.addTileBasedDrawProcesses}), every interior cell of a recognized tank
 *     holding fluid adds the fluid's vanilla liquid tile sprite to the liquid list, the one drawn
 *     with the liquid shader.</li>
 *     <li>{@link LiquidDataPatch}: the shader reads each region's liquid data texture (depth, salt
 *     water, shore, transparency), which is land for a tank's interior. After the game writes a
 *     tile's values ({@code LiquidManager.updateTextures}), interior cells of a recognized tank
 *     holding fluid get liquid values instead. Tanks queue their cells for that update whenever
 *     their view changes ({@link #onTankViewChanged}).</li>
 * </ol>
 *
 * <p>Everything is in this class: set {@link #ENABLED} to {@code false} to switch the rendering
 * off (the patches then do nothing), or delete the class and the one call to
 * {@link #onTankViewChanged} in the controller's object entity to remove it.
 *
 * <p>TODO(game): not verified visually; the dedicated server cannot draw. Needs checking on a
 * client: the fluid sprite, the wave shader, region edges and glass blocks on top.
 * <p>TODO(design): whether an inactive tank (wall broken, fluid kept, 5-9) still shows its fluid
 * is undecided; only recognized tanks are drawn.
 */
public final class TankFluidRendering {

    /** Switch for the whole fluid rendering. */
    public static final boolean ENABLED = true;

    /**
     * The liquid height written into the liquid data texture for tank cells (vanilla: below 0 is
     * depth, -10 deepest), which picks the shallow/deep look.
     * TODO(design): the look of a tank's fluid (shallow or deep) is undecided; -1, the shallowest
     * liquid depth, is a rendering placeholder.
     */
    private static final int FLUID_HEIGHT = -1;

    private TankFluidRendering() {
    }

    // ------------------------------------------------------------------ patch 1: liquid draw list

    /** {@code LevelDrawUtils.addTileBasedDrawProcesses(...)} (private, collects the tile stage). */
    @ModMethodPatch(target = LevelDrawUtils.class, name = "addTileBasedDrawProcesses",
            arguments = {TickManager.class, GameCamera.class, LevelDrawUtils.DrawArea.class,
                    LevelDrawUtils.DrawArea.class, LevelTileTerrainDrawOptions.class,
                    LevelTileLiquidDrawOptions.class, LevelTileTerrainDrawOptions.class,
                    LevelTileDamageDrawOptions.class, LevelTileLightDrawOptions.class,
                    SharedTextureDrawOptions.class, SharedTextureDrawOptions.class, OrderableDrawables.class,
                    List.class, boolean.class, PlayerMob.class})
    public static class LiquidDrawablesPatch {

        @Advice.OnMethodExit
        static void onExit(@Advice.FieldValue("level") Level level,
                           @Advice.Argument(1) GameCamera camera,
                           @Advice.Argument(2) LevelDrawUtils.DrawArea tileArea,
                           @Advice.Argument(5) LevelTileLiquidDrawOptions liquidDrawables,
                           @Advice.Argument(11) OrderableDrawables objectTileDrawables) {
            TankFluidRendering.addFluidDrawables(level, camera, tileArea, liquidDrawables, objectTileDrawables);
        }

    }

    /**
     * Adds the fluid of every recognized tank in the drawn area to the liquid draw list. A fluid
     * without a vanilla liquid tile (crude oil, N17-5) is drawn as a flat colour in the object tile
     * list under the glass blocks instead ({@link #CRUDE_OIL_COLOR}).
     */
    public static void addFluidDrawables(Level level, GameCamera camera, LevelDrawUtils.DrawArea tileArea,
                                         LevelTileLiquidDrawOptions liquidDrawables, OrderableDrawables objectTileDrawables) {
        if (!ENABLED || level == null || !level.isClient() || camera == null || tileArea == null
                || liquidDrawables == null) {
            return;
        }
        for (TankControllerObjectEntity tank : TankRegistry.getControllers(level)) {
            TankBounds bounds = tank.getTankBounds();
            FluidType fluid = tank.getFluid();
            if (bounds != null && fluid != null && !fluid.hasLiquidTile() && objectTileDrawables != null) {
                addFlatFluid(level, camera, tileArea, bounds, objectTileDrawables);
                continue;
            }
            LiquidTile liquid = liquidTileOf(fluid);
            if (bounds == null || liquid == null) {
                continue;
            }
            for (int y = bounds.y + 1; y < bounds.getMaxY(); y++) {
                for (int x = bounds.x + 1; x < bounds.getMaxX(); x++) {
                    // Only the new terrain splatting has the shader sprites; legacy liquids are skipped.
                    if (!tileArea.isIn(x, y) || !liquid.isUsingNewTerrainSplatting(level, x, y)) {
                        continue;
                    }
                    liquid.addFullDrawables(liquidDrawables, level, x, y, camera.getTileDrawX(x), camera.getTileDrawY(y));
                }
            }
        }
    }

    /**
     * The colour crude oil is drawn with: it has no vanilla liquid tile to borrow (N17-5).
     * TODO(design): provisional dark colour; crude oil's look is undecided.
     */
    public static final Color CRUDE_OIL_COLOR = new Color(28, 22, 18, 230);

    /** Draws a fluid without a vanilla liquid tile as a flat colour on the interior cells, under glass blocks. */
    private static void addFlatFluid(Level level, GameCamera camera, LevelDrawUtils.DrawArea tileArea, TankBounds bounds,
                                     OrderableDrawables objectTileDrawables) {
        for (int y = bounds.y + 1; y < bounds.getMaxY(); y++) {
            for (int x = bounds.x + 1; x < bounds.getMaxX(); x++) {
                if (!tileArea.isIn(x, y)) {
                    continue;
                }
                final TextureDrawOptions options = Renderer.initQuadDraw(32, 32)
                        .colorLight(CRUDE_OIL_COLOR, level.getLightLevel(x, y))
                        .pos(camera.getTileDrawX(x), camera.getTileDrawY(y));
                objectTileDrawables.add(-1, tm -> options.draw());
            }
        }
    }

    // ------------------------------------------------------------------ patch 2: liquid data texture

    /** {@code LiquidManager.updateTextures(tileX, tileY)}. */
    @ModMethodPatch(target = LiquidManager.class, name = "updateTextures", arguments = {int.class, int.class})
    public static class LiquidDataPatch {

        @Advice.OnMethodExit
        static void onExit(@Advice.FieldValue("level") Level level,
                           @Advice.Argument(0) int tileX, @Advice.Argument(1) int tileY) {
            TankFluidRendering.writeTankLiquidData(level, tileX, tileY);
        }

    }

    /** Gives a recognized tank's interior cell holding fluid liquid values in the liquid data texture. */
    public static void writeTankLiquidData(Level level, int tileX, int tileY) {
        if (!ENABLED || level == null || !level.isClient()) {
            return;
        }
        TankControllerObjectEntity tank = TankRegistry.findTankWithInterior(level, tileX, tileY);
        FluidType fluid = tank == null ? null : tank.getFluid();
        LiquidTile liquid = liquidTileOf(fluid);
        if (liquid == null) {
            return;
        }
        Region region = level.regionManager.getRegionByTile(tileX, tileY, false);
        if (region == null) {
            return;
        }
        // The same encoding as LiquidManager.updateTextures.
        int smoothGreen = FLUID_HEIGHT < 0
                ? GameMath.lerp((float) FLUID_HEIGHT / -10.0f, 127, 0)
                : GameMath.lerp((float) FLUID_HEIGHT / 10.0f, 128, 255);
        int smoothBlue = fluid == FluidType.SEAWATER ? 255 : 0;
        int nearestRed = 0; // not a shore
        int nearestGreen = GameMath.lerp(liquid.getMinLiquidAlpha(level), 0, 255);
        int nearestBlue = GameMath.lerp(liquid.getMaxLiquidAlpha(level), 0, 255);
        int[] values = {smoothGreen, smoothBlue, nearestRed, nearestGreen, nearestBlue};

        // A region's texture has a one-texel border copied from its neighbours, as in the vanilla method.
        int regionTileX = tileX - region.tileXOffset;
        int regionTileY = tileY - region.tileYOffset;
        write(region, regionTileX + 1, regionTileY + 1, values);
        boolean left = regionTileX == 0;
        boolean right = regionTileX == region.tileWidth - 1;
        if (regionTileY == 0) {
            Region top = level.regionManager.getRegionByTile(tileX, tileY - 1, false);
            if (top != null) {
                write(top, regionTileX + 1, top.tileHeight + 1, values);
            }
            Region topLeft = left ? level.regionManager.getRegionByTile(tileX - 1, tileY - 1, false) : null;
            if (topLeft != null) {
                write(topLeft, topLeft.tileWidth + 1, topLeft.tileHeight + 1, values);
            }
            Region topRight = right ? level.regionManager.getRegionByTile(tileX + 1, tileY - 1, false) : null;
            if (topRight != null) {
                write(topRight, 0, topRight.tileHeight + 1, values);
            }
        }
        if (regionTileY == region.tileHeight - 1) {
            Region bottom = level.regionManager.getRegionByTile(tileX, tileY + 1, false);
            if (bottom != null) {
                write(bottom, regionTileX + 1, 0, values);
            }
            Region bottomLeft = left ? level.regionManager.getRegionByTile(tileX - 1, tileY + 1, false) : null;
            if (bottomLeft != null) {
                write(bottomLeft, bottomLeft.tileWidth + 1, 0, values);
            }
            Region bottomRight = right ? level.regionManager.getRegionByTile(tileX + 1, tileY + 1, false) : null;
            if (bottomRight != null) {
                write(bottomRight, 0, 0, values);
            }
        }
        Region leftRegion = left ? level.regionManager.getRegionByTile(tileX - 1, tileY, false) : null;
        if (leftRegion != null) {
            write(leftRegion, leftRegion.tileWidth + 1, regionTileY + 1, values);
        }
        Region rightRegion = right ? level.regionManager.getRegionByTile(tileX + 1, tileY, false) : null;
        if (rightRegion != null) {
            write(rightRegion, 0, regionTileY + 1, values);
        }
    }

    private static void write(Region region, int textureX, int textureY, int[] values) {
        region.liquidData.updateTextureByRegion(textureX, textureY, values[0], values[1], values[2], values[3], values[4]);
    }

    // ------------------------------------------------------------------ tank changes

    /**
     * A tank's client view changed (or the controller went away: new bounds and fluid
     * {@code null}). Queues the liquid data texture update of the interior cells involved, so they
     * get liquid values or go back to the game's own.
     */
    public static void onTankViewChanged(Level level, TankBounds oldBounds, FluidType oldFluid,
                                         TankBounds newBounds, FluidType newFluid) {
        if (!ENABLED || level == null || !level.isClient()) {
            return;
        }
        if (Objects.equals(oldBounds, newBounds) && oldFluid == newFluid) {
            return;
        }
        queueInterior(level, oldBounds);
        if (!Objects.equals(oldBounds, newBounds)) {
            queueInterior(level, newBounds);
        }
    }

    private static void queueInterior(Level level, TankBounds bounds) {
        if (bounds == null) {
            return;
        }
        for (int y = bounds.y + 1; y < bounds.getMaxY(); y++) {
            for (int x = bounds.x + 1; x < bounds.getMaxX(); x++) {
                level.liquidManager.queueTextureUpdate(x, y);
            }
        }
    }

    /** The vanilla liquid tile a fluid is drawn with (D7, S10), or {@code null} (also for crude oil). */
    private static LiquidTile liquidTileOf(FluidType fluid) {
        if (fluid == null || !fluid.hasLiquidTile()) {
            return null;
        }
        GameTile tile = TileRegistry.getTile(fluid.getLiquidTileStringID());
        return tile instanceof LiquidTile ? (LiquidTile) tile : null;
    }

}
