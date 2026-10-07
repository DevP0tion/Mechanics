package devp0tion.mechanics.client;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.FluidType;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.PipeLayer;
import devp0tion.mechanics.items.MechanicsWrenchItem;
import devp0tion.mechanics.items.PacketWrenchPreview;
import devp0tion.mechanics.items.PacketWrenchPreviewRequest;
import devp0tion.mechanics.objects.BasicPipeObject;
import devp0tion.mechanics.objects.TankValveObject;
import devp0tion.mechanics.objects.UndergroundPipeObject;
import devp0tion.mechanics.pipe.UndergroundPipeLayer;
import devp0tion.mechanics.tank.FluidNames;
import devp0tion.mechanics.wrench.WrenchInfoText;
import devp0tion.mechanics.wrench.WrenchMode;
import devp0tion.mechanics.wrench.WrenchRefusal;
import devp0tion.mechanics.wrench.WrenchTargets;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameColor;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The engineering wrench's tooltip (clients): while the wrench is held and the cursor points at a
 * tile (the item's {@code onMouseHoverTile}, which the HUD calls every frame):
 *
 * <ul>
 *     <li>the tile's pipes, the main target first by the mode (N30-5): name, fluid name only (empty:
 *     "비어 있음" / "Empty"), linked directions, directions blocked by another fluid (N30-2,
 *     N12-2); the underground pipe of the tile too;</li>
 *     <li>the reason a right click there (toward the side the cursor is near, or the middle) would
 *     be refused, only when it would be (N30-1; {@link WrenchRefusal} lists every case).</li>
 * </ul>
 * The link flags and the faces blocked by another fluid are the ones the client draws (synced by
 * the server, {@link PipeRendering}). The fluids and the engine's click checks stay on the server:
 * the tooltip asks for them ({@link PacketWrenchPreviewRequest}, at most every
 * {@link #MIN_REQUEST_GAP_MS} ms, and every {@link #REFRESH_MS} ms for the same tile) and shows the
 * answer ({@link PacketWrenchPreview}) once it arrives; before that the fluid line and the engine's
 * reasons are left out.
 *
 * <p>TODO(game): not verified visually (the dedicated server cannot draw): the tooltip's place and
 * look, and the delay before the fluid appears.
 */
public final class WrenchTooltip {

    /** How often the answer for the pointed tile is refreshed (technical). */
    public static final long REFRESH_MS = 500;
    /** The least time between two requests (technical). */
    public static final long MIN_REQUEST_GAP_MS = 100;
    /** An answer older than this is not shown (technical). */
    public static final long MAX_AGE_MS = 2000;
    /** Answers kept per mode (technical). */
    private static final int KEPT = 64;

    private static final Object LOCK = new Object();
    private static Level answersLevel;
    private static final Map<WrenchMode, Map<Long, Answer>> ANSWERS = new EnumMap<>(WrenchMode.class);
    private static Level requestLevel;
    private static long requestTile;
    private static WrenchMode requestMode;
    private static long requestTime;

    private WrenchTooltip() {
    }

    private static final class Answer {
        final PacketWrenchPreview preview;
        final long time;

        Answer(PacketWrenchPreview preview, long time) {
            this.preview = preview;
            this.time = time;
        }
    }

    /** A received answer (network). */
    public static void onPreview(Level level, PacketWrenchPreview preview) {
        synchronized (LOCK) {
            if (level != answersLevel) {
                ANSWERS.clear();
                answersLevel = level;
            }
            ANSWERS.computeIfAbsent(preview.mode, mode -> new LinkedHashMap<Long, Answer>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Answer> eldest) {
                    return size() > KEPT;
                }
            }).put(PipeGrid.key(preview.tileX, preview.tileY), new Answer(preview, System.currentTimeMillis()));
        }
    }

    /** Forgets the answers for a tile, e.g. after a click there, so it is asked again soon. */
    public static void invalidate(Level level, int tileX, int tileY) {
        synchronized (LOCK) {
            if (level == answersLevel) {
                for (Map<Long, Answer> answers : ANSWERS.values()) {
                    answers.remove(PipeGrid.key(tileX, tileY));
                }
            }
        }
    }

    /** The current answer for the tile in the mode, asking the server for it when due; {@code null} when none. */
    private static PacketWrenchPreview preview(Level level, int tileX, int tileY, WrenchMode mode) {
        long now = System.currentTimeMillis();
        long tile = PipeGrid.key(tileX, tileY);
        Answer answer;
        boolean send;
        synchronized (LOCK) {
            Map<Long, Answer> answers = level == answersLevel ? ANSWERS.get(mode) : null;
            answer = answers == null ? null : answers.get(tile);
            boolean stale = answer == null || now - answer.time >= REFRESH_MS;
            boolean sameRequest = level == requestLevel && tile == requestTile && mode == requestMode;
            send = stale && now - requestTime >= (sameRequest ? REFRESH_MS : MIN_REQUEST_GAP_MS);
            if (send) {
                requestLevel = level;
                requestTile = tile;
                requestMode = mode;
                requestTime = now;
            }
        }
        if (send) {
            Client client = level.getClient();
            if (client != null) {
                client.network.sendPacket(new PacketWrenchPreviewRequest(tileX, tileY, mode));
            }
        }
        return answer != null && now - answer.time <= MAX_AGE_MS ? answer.preview : null;
    }

    /** Adds the tooltip for the cursor at level position (mouseX, mouseY), if there is anything to show. */
    public static void show(Level level, PlayerMob player, InventoryItem item, int mouseX, int mouseY) {
        if (level == null || !level.isClient() || player == null || item == null) {
            return;
        }
        int tileX = GameMath.getTileCoordinate(mouseX);
        int tileY = GameMath.getTileCoordinate(mouseY);
        if (!level.isTileWithinBounds(tileX, tileY)) {
            return;
        }
        WrenchMode mode = MechanicsWrenchItem.getMode(item);
        if (MechanicsWrenchItem.partAt(level, tileX, tileY, mode) == null) {
            // Nothing the wrench acts on, so no pipe either.
            return;
        }
        PacketWrenchPreview preview = preview(level, tileX, tileY, mode);
        Direction side = WrenchTargets.sideOf(mouseX, mouseY);
        WrenchRefusal refusal = MechanicsWrenchItem.reachRefusal(level, tileX, tileY, player, item);
        if (refusal == null && preview != null) {
            refusal = WrenchRefusal.of(preview.checkOf(side), side == null);
        }
        boolean basic = level.getObject(tileX, tileY) instanceof BasicPipeObject;
        boolean underground = level.getObject(UndergroundPipeLayer.ID, tileX, tileY) instanceof UndergroundPipeObject;
        List<WrenchInfoText.PipeSection> sections = new ArrayList<>(2);
        for (PipeLayer layer : WrenchTargets.pipeOrder(basic, underground, mode)) {
            sections.add(section(level, tileX, tileY, layer, preview));
        }
        List<WrenchInfoText.Line> lines = WrenchInfoText.build(sections, refusal, key -> Localization.translate("ui", key));
        if (lines.isEmpty()) {
            return;
        }
        StringTooltips tooltips = new StringTooltips();
        for (WrenchInfoText.Line line : lines) {
            if (line.warning) {
                tooltips.add(line.text, GameColor.RED);
            } else {
                tooltips.add(line.text);
            }
        }
        GameTooltipManager.addTooltip(tooltips, TooltipLocation.INTERACT_FOCUS);
    }

    /** One pipe's tooltip part, from what the client draws and the server's answer. */
    private static WrenchInfoText.PipeSection section(Level level, int tileX, int tileY, PipeLayer layer,
                                                      PacketWrenchPreview preview) {
        boolean base = layer == PipeLayer.BASE;
        GameObject object = base ? level.getObject(tileX, tileY) : level.getObject(UndergroundPipeLayer.ID, tileX, tileY);
        int own = base ? PipeRendering.baseLinks(level, tileX, tileY) : PipeRendering.undergroundLinks(level, tileX, tileY);
        int[] neighbours = new int[4];
        for (Direction d : Direction.values()) {
            int nx = tileX + d.dx;
            int ny = tileY + d.dy;
            neighbours[d.ordinal()] = base ? PipeRendering.baseLinks(level, nx, ny) : PipeRendering.undergroundLinks(level, nx, ny);
        }
        int partner;
        if (base) {
            partner = PipeRendering.undergroundLinks(level, tileX, tileY);
        } else {
            // An underground pipe links up to a basic pipe or a valve, never a pump (9-9).
            GameObject top = level.getObject(tileX, tileY);
            partner = top instanceof BasicPipeObject || top instanceof TankValveObject ? PipeRendering.baseLinks(level, tileX, tileY) : -1;
        }
        int linked = WrenchInfoText.linkedFaces(Math.max(own, 0), neighbours, partner);
        int blocked = base ? PipeRendering.baseBlockedSides(level, tileX, tileY) : PipeRendering.undergroundBlockedSides(level, tileX, tileY);
        boolean known = preview != null && (base ? preview.basePipe : preview.undergroundPipe);
        FluidType fluid = !known ? null : base ? preview.baseFluid : preview.undergroundFluid;
        return new WrenchInfoText.PipeSection(object.getDisplayName(), known, FluidNames.displayName(fluid), linked, blocked);
    }

}
