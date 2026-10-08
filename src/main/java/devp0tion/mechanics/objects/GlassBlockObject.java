package devp0tion.mechanics.objects;

import devp0tion.mechanics.core.GlassBlockSprites;
import devp0tion.mechanics.core.TankBounds;
import devp0tion.mechanics.core.TileCover;
import devp0tion.mechanics.tank.TankRegistry;
import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectHoverHitbox;
import necesse.level.gameObject.WallObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

/**
 * Glass block (유리 블럭): fills a multiblock tank's interior (2-1, 2-2) in interior condition (1)
 * all glass, or (3) on the tank floor (5-5, 5-11).
 *
 * <p>Drawn in one of two styles, chosen on the client every frame (the view of the tanks is synced):
 * <ul>
 *     <li><b>Ceiling</b>, in the interior of a recognized tank ({@link TankRegistry#findInteriorBounds},
 *     N34-1): a glass pane at wall-top height, 16 px above the tile, with no front face. A tile
 *     without ceiling glass to its north adds a 16 px rim above that (N34-2), so the pane reaches
 *     the north wall's top edge. Ceiling glass beside ceiling glass joins into one pane with a
 *     border only on its outer edge (N34-3). The tank fluid drawn on the floor under it, and the
 *     fill level band on the north wall ({@code TankFluidRendering}, N34-4), show through the partly
 *     transparent pane. Sheet {@code objects/<stringID>_ceiling.png}.</li>
 *     <li><b>Wall</b>, anywhere else, also in a tank under construction or a broken one (N34-5):
 *     drawn like a vanilla wall, a roof and a front face from quarter sprites by adjacency
 *     ({@code WallObject}'s selection, {@link GlassBlockSprites#wallPieces}), lit and faded
 *     behind the player like a vanilla wall. It joins toward every neighbour that blocks the whole
 *     tile ({@link #blocksWholeTile}: walls, rocks, other glass, tank parts; not furniture with a
 *     smaller collision). Only the glass draws the join: vanilla walls are not changed and draw
 *     their own edge toward the glass. See-through like the ceiling (N34-7): the sheet is partly
 *     transparent, so every screen quarter is drawn by exactly one glass tile (vanilla draws some
 *     roof quarters twice; the quarter row between two glass walls on top of each other is drawn
 *     by the lower one). Sheet {@code objects/<stringID>_wall.png}.</li>
 * </ul>
 * Both sheets and their layouts: {@link GlassBlockSprites}; drawn by
 * {@code tools/textures/draw_tank_parts.py}. The item icon ({@code items/<stringID>.png}) stays.
 *
 * <p>Both styles are sorted drawables with the vanilla wall's sort offset {@link #SORT_Y} (20) in
 * their tile row. For the ceiling that keeps it after the tank's north border and the fill band
 * ({@code y0 * 32 + 24}) and before the south border ({@code y1 * 32 + 16} for tank parts,
 * {@code + 20} for walls), so the south wall's roof covers the pane's last 16 px. The block still
 * does not draw a full tile ({@code drawsFullTile} false): the floor and the fluid are drawn under it.
 *
 * <p>The hover hitbox matches what is drawn (N34-6), as the vanilla wall's does: the wall style
 * {@code (0, -16, 32, 48)} like the vanilla wall, the ceiling {@code (0, -16, 32, 32)}, or
 * {@code (0, -32, 32, 48)} with the rim. So the ceiling glass of the first interior row, not the
 * north wall, is under the cursor over the north wall's front face.
 *
 * <p>The placement preview ({@link #drawPreview}) uses the style the tile would get.
 *
 * <p>Inside a recognized tank only glass blocks, the tank floor tile (once it exists) and underground
 * pipes may be placed (N16-1); the item description says so (N11-6).
 *
 * <p>It blocks movement with a full-tile collision like the other blocks (N31-1), and is mined
 * with any pickaxe, tier 0, the engine default (N31-2).
 *
 * <p>TODO(game): not verified visually: the raised pane and the rim against the north wall's top
 * edge, the seams of the joined pane, the pane over the shader fluid and the band, the ceiling's
 * light (the light of its own tile, while the pane is drawn up to 32 px above it), the glass wall
 * next to vanilla walls, rocks and furniture, its transparency, lighting and fade (the row
 * between two glass walls on top of each other is lit and sorted as the lower one's), and the
 * hover areas.
 */
public class GlassBlockObject extends GameObject {

    /** The sort offset of both styles in their tile row: the vanilla wall's ({@code WallObject}, 20). */
    public static final int SORT_Y = 20;

    /** The wall style's hover hitbox: the vanilla wall's. */
    private static final Rectangle WALL_HOVER = new Rectangle(0, -16, 32, 48);

    private final String textureName;

    /** Loaded on clients only; always null on a dedicated server. */
    protected GameTexture ceilingTexture;
    protected GameTexture wallTexture;

    public GlassBlockObject(String textureName) {
        super(new Rectangle(32, 32));
        this.textureName = textureName;
        // Minimap color: the base shade of the texture palette (art choice, not a design value).
        mapColor = new Color(170, 214, 230);
        isLightTransparent = true;
        hoverHitbox = new Rectangle(WALL_HOVER);
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        ceilingTexture = GameTexture.fromFile("objects/" + textureName + "_ceiling");
        wallTexture = GameTexture.fromFile("objects/" + textureName + "_wall");
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList,
                             Level level, int tileX, int tileY,
                             TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        TankBounds tank = TankRegistry.findInteriorBounds(level, tileX, tileY);
        final SharedTextureDrawOptions options;
        if (tank != null) {
            options = new SharedTextureDrawOptions(ceilingTexture).addObjectDamageOverlay(this, level, tileX, tileY);
            GameLight light = level.getLightLevel(tileX, tileY);
            for (GlassBlockSprites.Piece piece : GlassBlockSprites.ceilingPieces(ceilingNeighbours(level, tank, tileX, tileY))) {
                add(options, piece, drawX, drawY).light(light);
            }
        } else {
            options = new SharedTextureDrawOptions(wallTexture).addObjectDamageOverlay(this, level, tileX, tileY);
            GameLight[] lights = level.getRelative(tileX, tileY, Level.adjacentGettersWithCenter,
                    level::getLightLevelWall, GameLight[]::new);
            float fade = wallFadeAlpha(tileX, tileY, camera, perspective);
            for (GlassBlockSprites.Piece piece : wallPieces(level, tileX, tileY)) {
                SharedTextureDrawOptions.Wrapper wrapper = add(options, piece, drawX, drawY);
                float alpha = piece.fades ? fade : 1f;
                // As WallObject.applyLights, and its all-joined fast path (always smooth, alpha 1).
                if (piece.fastPath) {
                    wrapper.advColor(WallObject.getAdvancedLight(lights, 1f, piece.lightX, piece.lightY));
                } else if (Settings.smoothLighting) {
                    wrapper.advColor(WallObject.getAdvancedLight(lights, alpha, piece.lightX, piece.lightY));
                } else {
                    wrapper.light(piece.lightY == 1 ? lights[7] : lights[4]).alpha(alpha);
                }
            }
        }
        list.add(new LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return SORT_Y;
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
        TankBounds tank = TankRegistry.findInteriorBounds(level, tileX, tileY);
        SharedTextureDrawOptions options;
        List<GlassBlockSprites.Piece> pieces;
        if (tank != null) {
            options = new SharedTextureDrawOptions(ceilingTexture);
            pieces = GlassBlockSprites.ceilingPieces(ceilingNeighbours(level, tank, tileX, tileY));
        } else {
            options = new SharedTextureDrawOptions(wallTexture);
            pieces = wallPieces(level, tileX, tileY);
        }
        for (GlassBlockSprites.Piece piece : pieces) {
            add(options, piece, drawX, drawY).alpha(alpha);
        }
        options.draw();
    }

    private static SharedTextureDrawOptions.Wrapper add(SharedTextureDrawOptions options, GlassBlockSprites.Piece piece,
                                                        int drawX, int drawY) {
        return options.addSprite(piece.sheetX, piece.sheetY, GlassBlockSprites.CELL)
                .pos(drawX + piece.offsetX, drawY + piece.offsetY);
    }

    /** Per neighbour (adjacent order): a glass block also drawn as a ceiling, in the same tank (N34-3). */
    private static boolean[] ceilingNeighbours(Level level, TankBounds tank, int tileX, int tileY) {
        boolean[] ceiling = new boolean[Level.adjacentGetters.length];
        for (int i = 0; i < ceiling.length; i++) {
            Point offset = Level.adjacentGetters[i];
            ceiling[i] = isCeilingGlass(level, tank, tileX + offset.x, tileY + offset.y);
        }
        return ceiling;
    }

    private static boolean isCeilingGlass(Level level, TankBounds tank, int tileX, int tileY) {
        // Interiors of different tanks never touch (a border lies between), so the own tank decides.
        return tank.isInterior(tileX, tileY) && level.getObject(tileX, tileY) instanceof GlassBlockObject;
    }

    /**
     * The wall style's pieces: joined toward neighbours that block the whole tile (N34-5), each
     * screen quarter drawn once (N34-7).
     */
    private static List<GlassBlockSprites.Piece> wallPieces(Level level, int tileX, int tileY) {
        GameObject[] adjacent = level.getAdjacentObjects(tileX, tileY);
        boolean[] joined = new boolean[adjacent.length];
        for (int i = 0; i < adjacent.length; i++) {
            Point offset = Level.adjacentGetters[i];
            joined[i] = blocksWholeTile(level, adjacent[i], tileX + offset.x, tileY + offset.y);
        }
        // As WallObject: a joined wall above or below that draws its own top (a window).
        boolean forceDrawTop = joined[GlassBlockSprites.TOP] && drawsWallTop(adjacent[GlassBlockSprites.TOP]);
        boolean forceRemoveBot = joined[GlassBlockSprites.BOTTOM] && drawsWallTop(adjacent[GlassBlockSprites.BOTTOM]);
        // The quarter row between this and a glass wall above / below is drawn by the lower one.
        boolean glassAbove = isWallGlass(level, adjacent[GlassBlockSprites.TOP], tileX, tileY - 1);
        boolean glassBelow = isWallGlass(level, adjacent[GlassBlockSprites.BOTTOM], tileX, tileY + 1);
        return GlassBlockSprites.wallPieces(joined, forceDrawTop, forceRemoveBot, glassAbove, glassBelow);
    }

    /** A glass block drawn in the wall style: not in the interior of a recognized tank. */
    private static boolean isWallGlass(Level level, GameObject object, int tileX, int tileY) {
        return object instanceof GlassBlockObject && TankRegistry.findInteriorBounds(level, tileX, tileY) == null;
    }

    private static boolean drawsWallTop(GameObject object) {
        return object instanceof WallObject && ((WallObject) object).isWallDrawingTop();
    }

    /**
     * Whether an object blocks the whole tile (N34-5): it is solid there and its collision
     * rectangles ({@code getCollisions}, the ones movement collides with) cover the tile. The
     * engine has no flag for this: {@code isSolid} is true for any collision, and furniture keeps
     * a full collision in its constructor but overrides {@code getCollision} with a smaller one.
     * Walls, rocks, glass and tank parts cover the tile; chairs, tables, doors (4 px strips) and
     * pipes do not.
     */
    public static boolean blocksWholeTile(Level level, GameObject object, int tileX, int tileY) {
        if (object == null || !object.isSolid(level, tileX, tileY)) {
            return false;
        }
        List<Rectangle> collisions = object.getCollisions(level, tileX, tileY, level.getObjectRotation(tileX, tileY));
        if (collisions == null || collisions.isEmpty()) {
            return false;
        }
        int[] rects = new int[collisions.size() * 4];
        int i = 0;
        for (Rectangle r : collisions) {
            if (r != null) {
                rects[i] = r.x - tileX * 32;
                rects[i + 1] = r.y - tileY * 32;
                rects[i + 2] = r.width;
                rects[i + 3] = r.height;
            }
            i += 4;
        }
        return TileCover.coversTile(rects);
    }

    /**
     * The vanilla wall's fade ({@code WallObject.addWallDrawOptions}): 0.5 while the player or the
     * cursor is behind the wall, applied to the roof's top edge.
     */
    private static float wallFadeAlpha(int tileX, int tileY, GameCamera camera, PlayerMob perspective) {
        if (perspective == null || Settings.hideUI || Settings.hideCursor) {
            return 1f;
        }
        Rectangle alphaRec = new Rectangle(tileX * 32 - 16, tileY * 32 - 32, 64, 48);
        if (perspective.getCollision().intersects(alphaRec)) {
            return 0.5f;
        }
        if (alphaRec.contains(camera.getX() + WindowManager.getWindow().mousePos().sceneX,
                camera.getY() + WindowManager.getWindow().mousePos().sceneY)) {
            return 0.5f;
        }
        return 1f;
    }

    // ------------------------------------------------------------------ hover

    @Override
    protected ObjectHoverHitbox getHoverHitbox(Level level, int layerID, int tileX, int tileY) {
        TankBounds tank = TankRegistry.findInteriorBounds(level, tileX, tileY);
        if (tank == null) {
            return super.getHoverHitbox(level, layerID, tileX, tileY); // the wall style: WALL_HOVER
        }
        // The ceiling (N34-6): the pane from 16 px above the tile, or 32 px with the rim, to the
        // tile's middle.
        int top = isCeilingGlass(level, tank, tileX, tileY - 1)
                ? -GlassBlockSprites.CEILING_RAISE
                : -GlassBlockSprites.CEILING_RAISE - GlassBlockSprites.CELL;
        int bottom = 32 - GlassBlockSprites.CEILING_RAISE;
        return new ObjectHoverHitbox(layerID, tileX, tileY, 0, top, 32, bottom - top, hoverHitboxSortY);
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // N11-6: the interior placement rule (N16-2) from the glass block's side.
        tooltips.add(Localization.translate("itemtooltip", "glassblocktip"), 400);
        return tooltips;
    }

}
