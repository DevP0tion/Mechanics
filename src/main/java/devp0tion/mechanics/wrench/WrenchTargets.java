package devp0tion.mechanics.wrench;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.PipeLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which part of a tile the engineering wrench acts on, by its mode (N30-5), and which side of the
 * tile a click points to (N30-4). Game independent, so it is tested by the plain test runner.
 */
public final class WrenchTargets {

    /**
     * Half the size of a tile's middle area in pixels (the tile is 32): the middle is the centre
     * 16x16 pixels (N30-4). A click there toggles the vertical link.
     */
    public static final int MIDDLE_HALF_SIZE = 8;

    private WrenchTargets() {
    }

    /**
     * The side a click at level position (x, y) points to, or {@code null} for the tile's middle
     * (N30-4: pixels 8 to 23 of the tile on both axes, 16x16). Elsewhere the nearer edge wins; on
     * a diagonal, east or west.
     */
    public static Direction sideOf(int x, int y) {
        int dx = Math.floorMod(x, 32) - 16;
        int dy = Math.floorMod(y, 32) - 16;
        if (isMiddle(dx) && isMiddle(dy)) {
            return null;
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dy >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean isMiddle(int offset) {
        return offset >= -MIDDLE_HALF_SIZE && offset < MIDDLE_HALF_SIZE;
    }

    /**
     * The part the side right click acts on (12-8, 13-4, N30-5): in {@link WrenchMode#BASIC} the
     * base layer part (basic pipe, pump or valve) first, else the underground pipe; in
     * {@link WrenchMode#UNDERGROUND} the underground pipe first, else the base layer part.
     *
     * @param basePart    the linkable part on the base layer ({@link PipeGrid.Part#BASIC_PIPE},
     *                    {@link PipeGrid.Part#PUMP} or {@link PipeGrid.Part#VALVE}), or {@code null}
     * @param underground whether an underground pipe is on the tile
     * @return the part, or {@code null} when there is none
     */
    public static PipeGrid.Part sidePart(PipeGrid.Part basePart, boolean underground, WrenchMode mode) {
        if (mode == WrenchMode.UNDERGROUND && underground) {
            return PipeGrid.Part.UNDERGROUND_PIPE;
        }
        if (basePart != null) {
            return basePart;
        }
        return underground ? PipeGrid.Part.UNDERGROUND_PIPE : null;
    }

    /**
     * The pipe the left click recovers (12-8, 10-2, 10-5, N30-5): in {@link WrenchMode#BASIC} the
     * basic pipe first, in {@link WrenchMode#UNDERGROUND} the underground pipe first.
     *
     * @return the layer of that pipe, or {@code null} when the tile has no pipe
     */
    public static PipeLayer recoverLayer(boolean basicPipe, boolean undergroundPipe, WrenchMode mode) {
        List<PipeLayer> order = pipeOrder(basicPipe, undergroundPipe, mode);
        return order.isEmpty() ? null : order.get(0);
    }

    /**
     * The pipes of a tile in the order the tooltip shows them (N30-2, N30-5): the main target first
     * (the basic pipe in {@link WrenchMode#BASIC}, the underground pipe in
     * {@link WrenchMode#UNDERGROUND}), then the other pipe of the tile, if any.
     */
    public static List<PipeLayer> pipeOrder(boolean basicPipe, boolean undergroundPipe, WrenchMode mode) {
        if (!basicPipe && !undergroundPipe) {
            return Collections.emptyList();
        }
        List<PipeLayer> order = new ArrayList<>(2);
        PipeLayer first = mode == WrenchMode.UNDERGROUND ? PipeLayer.UNDERGROUND : PipeLayer.BASE;
        if (has(first, basicPipe, undergroundPipe)) {
            order.add(first);
        }
        if (has(first.other(), basicPipe, undergroundPipe)) {
            order.add(first.other());
        }
        return order;
    }

    private static boolean has(PipeLayer layer, boolean basicPipe, boolean undergroundPipe) {
        return layer == PipeLayer.BASE ? basicPipe : undergroundPipe;
    }

}
