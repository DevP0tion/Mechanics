package devp0tion.mechanics.objects;

import devp0tion.mechanics.client.PipeRendering;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.pipe.BasicPipeObjectEntity;
import devp0tion.mechanics.registry.MechanicsIngredients;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;

/**
 * Basic pipe (기본 파이프, 9-1): one per bar tier (9-11), on the base layer.
 *
 * <ul>
 *     <li>Links to adjacent pipes, valves and pumps automatically (9-4, 2-3, 9-3); the wrench cuts
 *     and links them (12-8, 13-4). Its state (fluid, link flags; reached = holds fluid) lives in its
 *     object entity ({@link BasicPipeObjectEntity}, N19-6).</li>
 *     <li>May be placed on liquid tiles (N17-4, S8).</li>
 *     <li>Removed with a pickaxe (10-2) or the wrench's left click (12-8); its fluid is lost (N12-1).</li>
 *     <li>Cannot be placed inside a recognized tank (N16-1); the item description says so (N11-6).</li>
 *     <li>Belongs to the "any pipe" ingredient group (N10-3).</li>
 *     <li>Drawn flat on the ground with arms toward its links and marks on cut faces
 *     ({@link PipeRendering}).</li>
 * </ul>
 * TODO(design): whether a pipe blocks movement is undecided; it has no collision (like the vanilla
 * minecart track). The tool tier needed to mine it is undecided; the engine default is used
 * (pickaxe, tier 0).
 */
public class BasicPipeObject extends GameObject {

    private final MineralTier tier;
    private final String textureName;

    /** Loaded on clients only. */
    protected GameTexture texture;

    public BasicPipeObject(MineralTier tier, String stringID, Color mapColor) {
        super(new Rectangle());
        this.tier = tier;
        this.textureName = stringID;
        this.mapColor = mapColor;
        isLightTransparent = true;
        canPlaceOnLiquid = true;
        canPlaceOnShore = true;
        addGlobalIngredient(MechanicsIngredients.ANY_PIPE);
    }

    /** The pipe's stringID (also its item's and texture's name): {@code <mineral>pipe}, e.g. {@code ironpipe}. */
    public static String stringIDOf(MineralTier tier) {
        return tier.name().toLowerCase(Locale.ROOT) + "pipe";
    }

    public MineralTier getMineralTier() {
        return tier;
    }

    @Override
    public GameMessage getNewLocalization() {
        return new LocalMessage("object", "basicpipe", "bar", new LocalMessage("item", tier.getBarStringID()));
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        texture = GameTexture.fromFile("objects/" + textureName);
        PipeRendering.loadTextures();
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new BasicPipeObjectEntity(level, x, y);
    }

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, int tileX, int tileY,
                             TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        int links = PipeRendering.baseLinks(level, tileX, tileY);
        final DrawOptionsList options = PipeRendering.pipeOptions(level, tileX, tileY, camera.getTileDrawX(tileX),
                camera.getTileDrawY(tileY), texture, links < 0 ? LinkFlags.ALL_OPEN : links, false,
                PipeRendering.undergroundVisible(perspective), 1f);
        tileList.add(tm -> options.draw());
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        texture.initDraw().section(0, 32, 0, 32).alpha(alpha).draw(camera.getTileDrawX(tileX), camera.getTileDrawY(tileY));
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // Placement rejection rules (N11-6): not inside a recognized tank (N16-1).
        tooltips.add(TankInteriorPlacement.rejectedTooltip(), 400);
        return tooltips;
    }

}
