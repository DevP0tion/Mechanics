package devp0tion.mechanics.objects;

import devp0tion.mechanics.registry.MechanicsTech;
import devp0tion.mechanics.tank.TankInteriorPlacement;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.registries.ObjectRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.recipe.Tech;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
import necesse.level.maps.multiTile.MultiTile;
import necesse.level.maps.multiTile.SidedRotationMultiTile;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

/**
 * Engineering workbench (공학 작업대): the crafting station for every mod element (13-1).
 *
 * <p>A 2x1 multi-tile like the vanilla workstation and the ExampleMod workstation. One class
 * covers both tiles: the master tile (placed by the item, opens the crafting menu) and the second
 * tile. Texture sheet {@code objects/<stringID>.png} is 64x224 and uses the ExampleMod
 * workstation layout:
 * <pre>
 *   y   0- 96  vertical bench (east: left column, master on top; west: right column, master below)
 *   y  96-160  north-facing bench, master on the left
 *   y 160-224  south-facing bench, master on the right
 * </pre>
 */
public class EngineeringWorkbenchObject extends CraftingStationObject {

    // Per rotation (0 north, 1 east, 2 south, 3 west): texture section x1, x2, y1, y2 and draw Y offset.
    private static final int[][] MASTER_SECTIONS = {
            {0, 32, 96, 160, -32},
            {0, 32, 0, 64, -32},
            {32, 64, 160, 224, -32},
            {32, 64, 64, 96, 0}
    };
    private static final int[][] SECOND_SECTIONS = {
            {32, 64, 96, 160, -32},
            {0, 32, 64, 96, 0},
            {0, 32, 160, 224, -32},
            {32, 64, 0, 64, -32}
    };

    // Per rotation: collision x offset, y offset, width, height inside the tile.
    private static final int[][] MASTER_COLLISION = {
            {6, 6, 26, 20},
            {4, 4, 24, 28},
            {0, 6, 26, 20},
            {4, 0, 24, 26}
    };
    private static final int[][] SECOND_COLLISION = {
            {0, 6, 26, 20},
            {4, 0, 24, 26},
            {6, 6, 26, 20},
            {4, 4, 24, 28}
    };

    private final String textureName;
    private final boolean master;
    /** Object ID of the other tile of the multi-tile. */
    protected int counterID;

    /** Loaded on clients only; always null on a dedicated server. */
    public GameTexture texture;

    protected EngineeringWorkbenchObject(String textureName, boolean master) {
        super(new Rectangle(32, 32));
        this.textureName = textureName;
        this.master = master;
        mapColor = new Color(96, 110, 128);
        isLightTransparent = true;
        hoverHitbox = new Rectangle(0, -16, 32, 48);
    }

    /**
     * Registers both tiles. The master uses {@code stringID}; the second tile uses
     * {@code stringID + "2"} and has no item.
     *
     * @return the object IDs of the master and the second tile
     */
    public static int[] register(String stringID) {
        EngineeringWorkbenchObject masterObject = new EngineeringWorkbenchObject(stringID, true);
        EngineeringWorkbenchObject secondObject = new EngineeringWorkbenchObject(stringID, false);
        // A negative broker value makes the game derive it from the recipe ingredients (1x their value).
        int masterID = ObjectRegistry.registerObject(stringID, masterObject, -1f, true);
        int secondID = ObjectRegistry.registerObject(stringID + "2", secondObject, 0f, false);
        masterObject.counterID = secondID;
        secondObject.counterID = masterID;
        return new int[]{masterID, secondID};
    }

    @Override
    public void loadTextures() {
        texture = GameTexture.fromFile("objects/" + textureName);
    }

    @Override
    public Tech[] getCraftingTechs() {
        // Like the vanilla and ExampleMod workstations, only the master tile defines the techs;
        // interacting with the second tile opens the master's menu.
        return master ? new Tech[]{MechanicsTech.ENGINEERING} : super.getCraftingTechs();
    }

    @Override
    public MultiTile getMultiTile(int rotation) {
        if (master) {
            return new SidedRotationMultiTile(0, 0, 2, 1, rotation, true, getID(), counterID);
        }
        return new SidedRotationMultiTile(1, 0, 2, 1, rotation, false, counterID, getID());
    }

    @Override
    public Rectangle getCollision(Level level, int x, int y, int rotation) {
        int[] c = (master ? MASTER_COLLISION : SECOND_COLLISION)[rotation & 3];
        return new Rectangle(x * 32 + c[0], y * 32 + c[1], c[2], c[3]);
    }

    private int[] section(int rotation) {
        return (master ? MASTER_SECTIONS : SECOND_SECTIONS)[rotation & 3];
    }

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList,
                             Level level, int tileX, int tileY,
                             TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameLight light = level.getLightLevel(tileX, tileY);
        int[] s = section(level.getObjectRotation(tileX, tileY));

        DrawOptionsList options = new DrawOptionsList();
        options.add(texture.initDraw()
                .section(s[0], s[1], s[2], s[3])
                .addObjectDamageOverlay(this, level, tileX, tileY)
                .light(light)
                .pos(drawX, drawY + s[4]));

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
    public void drawPreview(Level level, int tileX, int tileY, int rotation,
                            float alpha, PlayerMob player, GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        int[] s = section(rotation);
        texture.initDraw()
                .section(s[0], s[1], s[2], s[3])
                .alpha(alpha)
                .draw(drawX, drawY + s[4]);
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        // Placement rejection rules (N11-6): not inside a recognized tank (N16-2).
        tooltips.add(TankInteriorPlacement.rejectedTooltip(), 400);
        return tooltips;
    }

}
