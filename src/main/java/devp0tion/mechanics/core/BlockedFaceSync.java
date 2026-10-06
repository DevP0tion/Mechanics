package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The faces blocked by another fluid ({@link PipeGrid#getFluidBlockedSides}, N13-2) of one layer's
 * pipes as last sent to clients, so the game sends a tile again only when they changed. Tiles without
 * a blocked face are not kept.
 */
public final class BlockedFaceSync {

    private final PipeGrid grid;
    private final PipeLayer layer;
    private final Map<Long, Integer> sent = new HashMap<>();

    public BlockedFaceSync(PipeGrid grid, PipeLayer layer) {
        this.grid = grid;
        this.layer = layer;
    }

    /**
     * The pipe at the tile on {@code changedLayer} started or stopped holding fluid
     * ({@link PipeGrid.Listener#onPipeFluidChanged}). The faces that can change with it are those of
     * that pipe and of the pipes of its layer next to it, and the vertical face of the pipe of the
     * other layer on its tile. Returns the tiles of this sync's layer whose faces now differ from
     * what was sent ({@link PipeGrid#key} values); the game sends them ({@link #toSend}).
     */
    public List<Long> changedTiles(int tileX, int tileY, PipeLayer changedLayer) {
        List<Long> result = new ArrayList<>();
        check(tileX, tileY, result);
        if (changedLayer == layer) {
            for (Direction d : Direction.values()) {
                check(tileX + d.dx, tileY + d.dy, result);
            }
        }
        return result;
    }

    private void check(int tileX, int tileY, List<Long> out) {
        if (grid.getFluidBlockedSides(tileX, tileY, layer) != sentAt(tileX, tileY)) {
            out.add(PipeGrid.key(tileX, tileY));
        }
    }

    /** The faces last sent for the tile (0 when none were). */
    public int sentAt(int tileX, int tileY) {
        Integer faces = sent.get(PipeGrid.key(tileX, tileY));
        return faces == null ? 0 : faces;
    }

    /** The blocked faces of the pipe at the tile now, remembered as sent (a tile update or a region snapshot). */
    public int toSend(int tileX, int tileY) {
        int faces = grid.getFluidBlockedSides(tileX, tileY, layer);
        if (faces == 0) {
            sent.remove(PipeGrid.key(tileX, tileY));
        } else {
            sent.put(PipeGrid.key(tileX, tileY), faces);
        }
        return faces;
    }

}
