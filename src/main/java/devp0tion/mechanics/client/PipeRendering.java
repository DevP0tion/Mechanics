package devp0tion.mechanics.client;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.LinkFlags;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.Pump;
import devp0tion.mechanics.core.PumpForm;
import devp0tion.mechanics.items.MechanicsWrenchItem;
import devp0tion.mechanics.objects.BasicPipeObject;
import devp0tion.mechanics.objects.PumpObject;
import devp0tion.mechanics.objects.TankValveObject;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import devp0tion.mechanics.pipe.BasicPipeObjectEntity;
import devp0tion.mechanics.pipe.PumpObjectEntity;
import devp0tion.mechanics.pipe.UndergroundPipeLayer;
import devp0tion.mechanics.tank.TankValveObjectEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

/**
 * Drawing of pipes and link faces (clients only).
 *
 * <p>Pipe sheets ({@code objects/<pipe>.png}, 160x32, drawn by {@code tools/textures/draw_pipes_pumps.py}):
 * five 32x32 cells: hub, arm north, east, south, west. Arms are drawn toward linked neighbours
 * (both facing flags open). The shared link sheet ({@code objects/pipelinks.png}, 192x32): cut
 * marks north, east, south, west, then the vertical link marker linked and cut.
 *
 * <p>Cut faces are drawn (N16-4): a pipe marks every side whose own flag is cut and every side
 * facing a part whose flag toward it is cut; a pump marks its cut links to valves. A pump's side
 * that cannot link to the part beside it (by its rotation and form, N36-2, N36-5, N36-20) is no part
 * there, like a wall: no arm, no collision part, not linked in the wrench's tooltip, no cut mark for
 * the pump's flag or a valve's flag there; a pipe's own cut flag there is still marked, as beside a
 * wall (N36-37, N36-55, {@link #baseLinksFacing}, {@link #pumpCutOptions}). A face between
 * two pipes holding different fluids is a dead end (N13-2) and is drawn the same way, from the
 * blocked faces the server syncs ({@link BasicPipeObjectEntity#getBlockedSides},
 * {@link ClientUndergroundPipes#getBlockedSides}): between two pipes of one layer, and the vertical
 * link marker between the basic and the underground pipe on a tile, which shows cut. The vertical
 * link marker is drawn while underground pipes are visible.
 *
 * <p>Underground pipes are visible only while the local player holds the wrench or an underground
 * pipe item (9-6, 10-6); they are then drawn over everything, like the vanilla wires.
 * <p>TODO(game): not verified visually (the dedicated server cannot draw).
 */
public final class PipeRendering {

    private static GameTexture links;

    private PipeRendering() {
    }

    /** Loads the shared link sheet (clients, from the objects' loadTextures). */
    public static void loadTextures() {
        if (links == null) {
            links = GameTexture.fromFile("objects/pipelinks");
        }
    }

    /** Whether the local player sees underground pipes: holding the wrench or an underground pipe item (9-6, 10-6). */
    public static boolean undergroundVisible(PlayerMob perspective) {
        if (perspective == null) {
            return false;
        }
        InventoryItem held = perspective.getSelectedItem();
        if (held == null) {
            return false;
        }
        if (held.item instanceof MechanicsWrenchItem) {
            return true;
        }
        return held.item instanceof ObjectItem && ((ObjectItem) held.item).getObject() instanceof UndergroundPipeObject;
    }

    /**
     * The link flags of the base layer part at a tile as the client knows them, or -1 when it is no
     * part. A valve that is a plain wall (N33-1, {@link TankValveObjectEntity#isPlainWall}) is no part
     * here, like a wall: no arm or cut mark toward it, no collision part, not linked in the wrench's
     * tooltip. Also read on the server, for the basic pipe's collision (N31-10): the object entities
     * keep the flags on both sides.
     */
    public static int baseLinks(Level level, int tileX, int tileY) {
        GameObject object = level.getObject(tileX, tileY);
        if (!(object instanceof BasicPipeObject || object instanceof TankValveObject || object instanceof PumpObject)) {
            return -1;
        }
        ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
        if (entity instanceof BasicPipeObjectEntity) {
            return ((BasicPipeObjectEntity) entity).getLinks();
        }
        if (entity instanceof TankValveObjectEntity) {
            TankValveObjectEntity valve = (TankValveObjectEntity) entity;
            return valve.isPlainWall() ? -1 : valve.getLinks();
        }
        if (entity instanceof PumpObjectEntity) {
            return ((PumpObjectEntity) entity).getLinks();
        }
        return LinkFlags.ALL_OPEN;
    }

    /**
     * {@link #baseLinks}, as seen by the part of kind {@code neighbourKind} beside the tile's side
     * {@code side}: a pump's side that does not link to that kind is no part, like a wall (N36-37):
     * its sides follow its rotation and form ({@link PumpObject#frontAt}, {@link PumpObject#formAt},
     * {@link Pump#accepts(Direction, PumpForm, Direction, PipeGrid.Part)}). So a basic pipe beside a
     * pump has no arm, no collision part and no link toward any side but the pump's front (N36-2), and
     * draws no mark for the pump's flag there; its own flag cut there is still its own cut mark, as
     * beside a wall (N36-55, N16-4). For basic pipes only the rotation counts, never the form, so the
     * server and the clients compute the same collision. Also read on the server (collision, N31-10).
     *
     * @param side the side of the part at the tile that faces the asking neighbour
     */
    public static int baseLinksFacing(Level level, int tileX, int tileY, Direction side, PipeGrid.Part neighbourKind) {
        int links = baseLinks(level, tileX, tileY);
        if (links >= 0 && level.getObject(tileX, tileY) instanceof PumpObject
                && !Pump.accepts(PumpObject.frontAt(level, tileX, tileY), PumpObject.formAt(level, tileX, tileY), side,
                neighbourKind)) {
            return -1;
        }
        return links;
    }

    /**
     * The faces of the basic pipe at a tile blocked by another fluid (N13-2; sides and vertical), or 0.
     * Also read on the server, for the basic pipe's collision (N31-13): its object entity keeps the
     * faces on both sides (synced to clients).
     */
    public static int baseBlockedSides(Level level, int tileX, int tileY) {
        ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
        return entity instanceof BasicPipeObjectEntity ? ((BasicPipeObjectEntity) entity).getBlockedSides() : 0;
    }

    /** The link flags of the underground pipe at a tile, or -1 when there is none. */
    public static int undergroundLinks(Level level, int tileX, int tileY) {
        if (!(level.getObject(UndergroundPipeLayer.ID, tileX, tileY) instanceof UndergroundPipeObject)) {
            return -1;
        }
        return ClientUndergroundPipes.getLinks(level, tileX, tileY);
    }

    /** The faces of the underground pipe at a tile blocked by another fluid (N13-2; sides and vertical), or 0. */
    public static int undergroundBlockedSides(Level level, int tileX, int tileY) {
        return ClientUndergroundPipes.getBlockedSides(level, tileX, tileY);
    }

    /**
     * The draw options of a pipe at a tile: hub, arms toward linked neighbours, cut marks.
     *
     * @param sheet       the pipe's sheet
     * @param ownLinks    the pipe's own flags
     * @param blockedSides the faces blocked by another fluid (N13-2): sides, and the vertical link to
     *                     the other layer, drawn as cut
     * @param underground whether it is an underground pipe (neighbours on the underground layer)
     * @param showVertical whether to draw the vertical link marker
     */
    public static DrawOptionsList pipeOptions(Level level, int tileX, int tileY, int drawX, int drawY, GameTexture sheet,
                                              int ownLinks, int blockedSides, boolean underground, boolean showVertical,
                                              float alpha) {
        GameLight light = level.getLightLevel(tileX, tileY);
        DrawOptionsList options = new DrawOptionsList();
        options.add(cell(sheet, 0, drawX, drawY, light, alpha));
        for (Direction d : Direction.values()) {
            int nx = tileX + d.dx;
            int ny = tileY + d.dy;
            int neighbour = underground ? undergroundLinks(level, nx, ny)
                    : baseLinksFacing(level, nx, ny, d.opposite(), PipeGrid.Part.BASIC_PIPE);
            boolean own = LinkFlags.isSideOpen(ownLinks, d);
            boolean other = neighbour >= 0 && LinkFlags.isSideOpen(neighbour, d.opposite());
            boolean blocked = LinkFlags.isSideOpen(blockedSides, d);
            if (own && other && !blocked) {
                options.add(cell(sheet, 1 + d.ordinal(), drawX, drawY, light, alpha));
            }
            if (!own || neighbour >= 0 && !other || blocked) {
                options.add(cell(links, d.ordinal(), drawX, drawY, light, alpha));
            }
        }
        if (showVertical) {
            int otherLinks = underground ? baseLinksForVertical(level, tileX, tileY) : undergroundLinks(level, tileX, tileY);
            if (otherLinks >= 0) {
                boolean linked = LinkFlags.isVerticalOpen(ownLinks) && LinkFlags.isVerticalOpen(otherLinks)
                        && !LinkFlags.isVerticalOpen(blockedSides);
                options.add(cell(links, linked ? 4 : 5, drawX, drawY, light, alpha));
            }
        }
        return options;
    }

    /** The vertical partner of an underground pipe: the basic pipe or valve on its tile (9-9, N16-4). */
    private static int baseLinksForVertical(Level level, int tileX, int tileY) {
        GameObject object = level.getObject(tileX, tileY);
        if (object instanceof PumpObject) {
            return -1;
        }
        return baseLinks(level, tileX, tileY);
    }

    /**
     * Cut marks of a pump's links to the valves next to it (N16-4), only on the sides that link to a
     * valve: its front, and a valve pump's back (N36-3, N36-4, N36-17, N36-18). None on the other sides,
     * like a wall (N36-37; N36-5, N36-20), and none toward a plain wall (N33-1).
     *
     * @param front the pump's output side (its rotation, {@link PumpObject#frontAt})
     * @param form  the pump's form ({@link PumpObject#formAt})
     */
    public static DrawOptionsList pumpCutOptions(Level level, int tileX, int tileY, int drawX, int drawY, int pumpLinks,
                                                 Direction front, PumpForm form) {
        GameLight light = level.getLightLevel(tileX, tileY);
        DrawOptionsList options = new DrawOptionsList();
        for (Direction d : Direction.values()) {
            if (!Pump.accepts(front, form, d, PipeGrid.Part.VALVE)) {
                continue;
            }
            int nx = tileX + d.dx;
            int ny = tileY + d.dy;
            if (!(level.getObject(nx, ny) instanceof TankValveObject)) {
                continue;
            }
            int valve = baseLinks(level, nx, ny);
            if (valve < 0) {
                continue;
            }
            if (!LinkFlags.isSideOpen(pumpLinks, d) || !LinkFlags.isSideOpen(valve, d.opposite())) {
                options.add(cell(links, d.ordinal(), drawX, drawY, light, 1f));
            }
        }
        return options;
    }

    private static necesse.gfx.drawOptions.texture.TextureDrawOptions cell(GameTexture texture, int index, int drawX,
                                                                            int drawY, GameLight light, float alpha) {
        return texture.initDraw().section(index * 32, index * 32 + 32, 0, 32).light(light).alpha(alpha).pos(drawX, drawY);
    }

}
