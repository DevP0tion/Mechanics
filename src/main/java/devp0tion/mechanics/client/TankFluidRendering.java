package devp0tion.mechanics.client;

import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TankFillBand;
import devp0tion.mechanics.tank.TankControllerObjectEntity;
import devp0tion.mechanics.tank.TankRegistry;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameResources;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.ShaderSprite;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawables.LevelDrawUtils;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.LevelTileDamageDrawOptions;
import necesse.gfx.drawables.LevelTileLightDrawOptions;
import necesse.gfx.drawables.LevelTileLiquidDrawOptions;
import necesse.gfx.drawables.LevelTileLiquidRegionDrawOptions;
import necesse.gfx.drawables.LevelTileTerrainDrawOptions;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTextureSection;
import necesse.level.gameTile.GameTile;
import necesse.level.gameTile.LiquidTile;
import necesse.level.maps.Level;
import necesse.level.maps.biomes.Biome;
import necesse.level.maps.liquidManager.LiquidManager;
import necesse.level.maps.regionSystem.Region;
import net.bytebuddy.asm.Advice;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Draws the fluid of recognized tanks with the vanilla liquid wave shader, in every interior
 * condition (6-3, 6-7), and the fill level as a band on the tank's north wall (N34-4). The glass
 * blocks are drawn on top as the tank's glass ceiling at wall-top height ({@code GlassBlockObject},
 * N34-1), partly transparent so the fluid and the band show through. How the vanilla liquid drawing
 * works: domain rules S3, S4.
 *
 * <p>Two patches (S4), both only acting on client levels:
 * <ol>
 *     <li>{@link LiquidDrawablesPatch}: after the level has collected its draw lists
 *     ({@code LevelDrawUtils.addTileBasedDrawProcesses}), every interior cell of a recognized tank
 *     holding fluid adds the fluid's vanilla liquid tile sprite to the liquid list, the one drawn
 *     with the liquid shader in the tile stage (the floor fluid). Each recognized tank holding fluid
 *     also adds its fill level band to the sorted list ({@link #addFillBand}).</li>
 *     <li>{@link LiquidDataPatch}: the shader reads each region's liquid data texture (depth, salt
 *     water, shore, transparency), which is land for a tank's interior. After the game writes a
 *     tile's values ({@code LiquidManager.updateTextures}), interior cells of a recognized tank
 *     holding fluid get liquid values instead. Tanks queue their cells for that update whenever
 *     their bounds or fluid change ({@link #onTankViewChanged}).</li>
 * </ol>
 *
 * <p>The fill level band (N34-4): over the inner side of the north border's 32 px front face,
 * across the interior columns, {@link TankFillBand#height} px high from the face's bottom edge
 * (the fill percentage of the controller's synced amount and capacity, the glass rim's height
 * included). It is drawn with the same liquid shader and sprites as the floor fluid, in its own
 * sorted drawable between the north border and the first interior row's glass
 * ({@link #BAND_SORT_Y}); crude oil gets a flat band in its floor colour instead. The band is
 * rebuilt every frame from the synced amount, so an amount-only change shows on the next frame
 * after it arrives (the controller coalesces those to every {@code AMOUNT_SYNC_TICKS} ticks), with
 * no easing. Valves and controllers in the north border are drawn before the band (sort 16), so
 * the band covers the lower part of their front face like the wall's.
 *
 * <p>Everything is in this class: set {@link #ENABLED} to {@code false} to switch the rendering
 * off (the patches then do nothing), or delete the class and the one call to
 * {@link #onTankViewChanged} in the controller's object entity to remove it. {@link #BAND_ENABLED}
 * switches only the band off.
 *
 * <p>TODO(game): not verified visually; the dedicated server cannot draw. Needs checking on a
 * client: the fluid sprite, the wave shader, the deep look ({@link #FLUID_HEIGHT}), region edges,
 * the glass ceiling on top, and the band: the shader outside the tile stage, its sprites cut to
 * the band, its colour and light (see {@link #addFillBand}).
 * <p>Only recognized tanks are drawn: an inactive tank (wall broken, fluid kept, 5-9) does not draw
 * its fluid (N31-5).
 */
public final class TankFluidRendering {

    /** Switch for the whole fluid rendering. */
    public static final boolean ENABLED = true;

    /**
     * Switch for the fill level band on the north wall (N34-4). Off, the tank shows only the floor
     * fluid under the glass ceiling; there is no other fallback.
     */
    private static final boolean BAND_ENABLED = true;

    /**
     * The band's sort offset in the north border's row ({@code LevelSortedDrawable}: row * 32 +
     * offset): after the border's own drawing (walls 20, tank parts 16) and before the first
     * interior row's glass ceiling (32 + {@code GlassBlockObject.SORT_Y}).
     */
    private static final int BAND_SORT_Y = 24;

    /**
     * The liquid height written into the liquid data texture for tank cells (vanilla: below 0 is
     * depth, -10 deepest), which picks the shallow/deep look: the deepest depth the engine uses,
     * so a tank's fluid has the deep look of the vanilla liquid tile (N31-9).
     * TODO(game): the deep look is not verified visually.
     */
    private static final int FLUID_HEIGHT = LiquidManager.minDepth;

    private TankFluidRendering() {
    }

    // ------------------------------------------------------------------ patch 1: liquid draw list

    /**
     * {@code LevelDrawUtils.addTileBasedDrawProcesses(...)} (private, queues the collection of the
     * tile stage and the objects; argument 12 is the sorted list the objects are added to).
     */
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
                           @Advice.Argument(11) OrderableDrawables objectTileDrawables,
                           @Advice.Argument(12) List<LevelSortedDrawable> sortedDrawables) {
            TankFluidRendering.addFluidDrawables(level, camera, tileArea, liquidDrawables, objectTileDrawables,
                    sortedDrawables);
        }

    }

    /**
     * Adds the fluid of every recognized tank in the drawn area to the liquid draw list, and its
     * fill level band to the sorted list. A fluid without a vanilla liquid tile (crude oil, N17-5)
     * is drawn as a flat colour in the object tile list instead ({@link #CRUDE_OIL_COLOR}).
     */
    public static void addFluidDrawables(Level level, GameCamera camera, LevelDrawUtils.DrawArea tileArea,
                                         LevelTileLiquidDrawOptions liquidDrawables, OrderableDrawables objectTileDrawables,
                                         List<LevelSortedDrawable> sortedDrawables) {
        if (!ENABLED || level == null || !level.isClient() || camera == null || tileArea == null
                || liquidDrawables == null) {
            return;
        }
        for (TankControllerObjectEntity tank : TankRegistry.getControllers(level)) {
            TankBounds bounds = tank.getTankBounds();
            FluidType fluid = tank.getFluid();
            if (BAND_ENABLED && bounds != null && fluid != null && sortedDrawables != null) {
                addFillBand(level, camera, tileArea, tank, bounds, fluid, sortedDrawables);
            }
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
     * The colour crude oil is drawn with: it has no vanilla liquid tile to borrow (N17-5). Dark
     * brown, as decided (N31-8).
     */
    public static final Color CRUDE_OIL_COLOR = new Color(28, 22, 18, 230);

    /**
     * Draws a fluid without a vanilla liquid tile as a flat colour on the interior cells. In the
     * object tile list at order -1, before the object tile drawables at the default order; the glass
     * ceiling is a sorted drawable (N34-1), drawn after the whole tile list anyway.
     */
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

    // ------------------------------------------------------------------ fill level band (N34-4)

    /**
     * Adds a tank's fill level band: on the north border's front face (screen rows of the border
     * row, {@code [drawY, drawY + 32]}), over each interior column, the bottom
     * {@link TankFillBand#height} px. Fill = amount / capacity of the controller's synced view.
     *
     * <p>A fluid with a vanilla liquid tile is drawn as the floor fluid is ({@code LiquidTile
     * .addFullDrawables}): the same four splatting sprites (fresh/salt, shallow/deep) and liquid
     * colour, cut to the band's height from the bottom of the sprite the tile of the border row
     * would have, between {@code GameResources.liquidShader.use(level)} and {@code stop()} in its
     * own sorted drawable. The shader's liquid data coordinates point at the centre of the first
     * interior cell below the column, so the band shows that cell's liquid values (written by
     * {@link #writeTankLiquidData}) without blending into the wall's land values. Biome blending is
     * not applied (the base biome of that cell).
     *
     * <p>Light: the sorted stage comes after the tile light pass that darkens the floor fluid, so
     * the band multiplies its colour by the light of that interior cell itself, as the crude oil
     * floor does. TODO(game): not verified that the liquid shader multiplies the vertex colour by
     * that light (vanilla passes its liquid colours as the vertex colour, so it is expected to),
     * nor how the shader's transparency looks over the wall's face.
     *
     * <p>Crude oil (no vanilla liquid tile) gets a flat band in {@link #CRUDE_OIL_COLOR}, lit the
     * same way, like its floor.
     */
    private static void addFillBand(Level level, GameCamera camera, LevelDrawUtils.DrawArea tileArea,
                                    TankControllerObjectEntity tank, TankBounds bounds, FluidType fluid,
                                    List<LevelSortedDrawable> sortedDrawables) {
        int height = TankFillBand.height(tank.getAmount(), tank.getCapacity());
        if (height <= 0) {
            return;
        }
        int wallY = bounds.y;
        int fluidY = bounds.y + 1;
        int bandY = camera.getTileDrawY(wallY) + TankFillBand.MAX_HEIGHT - height;
        int sortY = wallY * 32 + BAND_SORT_Y;
        if (!fluid.hasLiquidTile()) {
            final List<TextureDrawOptions> flat = new ArrayList<>();
            for (int x = bounds.x + 1; x < bounds.getMaxX(); x++) {
                if (tileArea.isIn(x, wallY)) {
                    flat.add(Renderer.initQuadDraw(32, height)
                            .colorLight(CRUDE_OIL_COLOR, level.getLightLevel(x, fluidY))
                            .pos(camera.getTileDrawX(x), bandY));
                }
            }
            if (!flat.isEmpty()) {
                sortedDrawables.add(new BandDrawable(tank, sortY, () -> flat.forEach(TextureDrawOptions::draw)));
            }
            return;
        }
        LiquidTile liquid = liquidTileOf(fluid);
        if (liquid == null) {
            return;
        }
        final LevelTileLiquidDrawOptions band = new LevelTileLiquidDrawOptions(level);
        boolean any = false;
        for (int x = bounds.x + 1; x < bounds.getMaxX(); x++) {
            if (!tileArea.isIn(x, wallY) || !liquid.isUsingNewTerrainSplatting(level, x, fluidY)) {
                continue;
            }
            any |= addLiquidBandColumn(band, level, liquid, x, wallY, fluidY, height, camera.getTileDrawX(x), bandY);
        }
        if (any) {
            sortedDrawables.add(new BandDrawable(tank, sortY, () -> {
                GameResources.liquidShader.use(level);
                try {
                    band.draw();
                } finally {
                    GameResources.liquidShader.stop();
                }
            }));
        }
    }

    /** One column of a liquid band, as {@code LiquidTile.addFullDrawables} builds a floor cell. */
    private static boolean addLiquidBandColumn(LevelTileLiquidDrawOptions band, Level level, LiquidTile liquid,
                                               int x, int wallY, int fluidY, int height, int drawX, int drawY) {
        LevelTileLiquidRegionDrawOptions regionOptions = band.getByTile(level, x, fluidY);
        Region region = level.regionManager.getRegionByTile(x, fluidY, false);
        if (regionOptions == null || region == null) {
            return false;
        }
        Biome biome = level.getBiome(x, fluidY);
        LiquidTile.TextureIndexes indexes = liquid.getTextureIndexes(level, x, fluidY, biome);
        Point sprite = liquid.getTileSprite(level, x, wallY);
        GameTextureSection shallowFresh = bandSprite(liquid, indexes.freshShallow, indexes.freshAnimTime, level, x, wallY, sprite, height);
        GameTextureSection deepFresh = bandSprite(liquid, indexes.freshDeep, indexes.freshAnimTime, level, x, wallY, sprite, height);
        GameTextureSection shallowSalt = bandSprite(liquid, indexes.saltShallow, indexes.saltAnimTime, level, x, wallY, sprite, height);
        GameTextureSection deepSalt = bandSprite(liquid, indexes.saltDeep, indexes.saltAnimTime, level, x, wallY, sprite, height);
        // The centre of the cell's texel in the region's liquid data texture (one-texel border).
        float dataX = (x - region.tileXOffset + 1 + 0.5f) / (region.tileWidth + 2);
        float dataY = (fluidY - region.tileYOffset + 1 + 0.5f) / (region.tileHeight + 2);
        regionOptions.add(shallowFresh)
                .addShaderSprite(new ShaderSprite(1, deepFresh))
                .addShaderSprite(new ShaderSprite(2, shallowSalt))
                .addShaderSprite(new ShaderSprite(3, deepSalt))
                .addShaderSprite(4, dataX, dataX, dataY, dataY)
                .colorLight(liquid.getNewSplattingLiquidColor(level, x, fluidY, biome), level.getLightLevel(x, fluidY))
                .pos(drawX, drawY);
        return true;
    }

    /** The bottom {@code height} px of a splatting sprite, as the floor cell at (x, y) would draw it. */
    private static GameTextureSection bandSprite(LiquidTile liquid, int index, int animTime, Level level,
                                                 int x, int y, Point sprite, int height) {
        GameTextureSection section = liquid.getNewSplattingSection(index);
        int frame = liquid.getNewSplattingFrame(section, level, animTime);
        return liquid.getNewSplattingTexture(section, frame, level, x, y)
                .sprite(sprite.x, sprite.y, 32)
                .section(0, 32, 32 - height, 32);
    }

    /** A sorted drawable at an absolute sort value (the band is not an object's drawing). */
    private static final class BandDrawable extends LevelSortedDrawable {

        private final int sortY;
        private final Runnable drawer;

        BandDrawable(Object owner, int sortY, Runnable drawer) {
            super(owner, false);
            this.sortY = sortY;
            this.drawer = drawer;
            init();
        }

        @Override
        public int getSortY() {
            return sortY;
        }

        @Override
        public void draw(TickManager tickManager) {
            drawer.run();
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
     *
     * <p>Amount-only changes are ignored here on purpose: the liquid data does not depend on the
     * amount, and the fill level band (N34-4) reads the synced amount and capacity every frame
     * ({@link #addFillBand}), so a new amount shows as soon as the content packet has set it.
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
