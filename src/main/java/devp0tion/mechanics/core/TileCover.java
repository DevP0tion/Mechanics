package devp0tion.mechanics.core;

import java.util.Arrays;

/**
 * Whether collision rectangles cover a whole tile (N34-5): a glass block outside a recognized tank
 * joins its wall drawing only toward neighbours that block the whole tile. The engine has no flag
 * for that ({@code GameObject.isSolid} is true for any collision, and furniture keeps a full
 * constructor collision but overrides it with a smaller one), so the game side passes the
 * neighbour's actual collision rectangles here.
 */
public final class TileCover {

    /** A tile's size in px. */
    public static final int TILE_SIZE = 32;

    private TileCover() {
    }

    /**
     * @param rects collision rectangles relative to the tile's top-left corner, flattened as
     *              {@code x, y, width, height} per rectangle; parts outside the tile are ignored
     * @return whether their union covers the whole tile {@code [0, 32) x [0, 32)}
     */
    public static boolean coversTile(int[] rects) {
        if (rects == null || rects.length < 4 || rects.length % 4 != 0) {
            return false;
        }
        int count = rects.length / 4;
        int[] xs = new int[count * 2 + 2];
        int[] ys = new int[count * 2 + 2];
        int n = 0;
        xs[n] = 0;
        ys[n++] = 0;
        xs[n] = TILE_SIZE;
        ys[n++] = TILE_SIZE;
        for (int i = 0; i < count; i++) {
            int x = rects[i * 4];
            int y = rects[i * 4 + 1];
            xs[n] = clamp(x);
            ys[n++] = clamp(y);
            xs[n] = clamp(x + Math.max(0, rects[i * 4 + 2]));
            ys[n++] = clamp(y + Math.max(0, rects[i * 4 + 3]));
        }
        xs = unique(xs, n);
        ys = unique(ys, n);
        // Every elementary cell between consecutive edges must lie inside some rectangle.
        for (int xi = 0; xi + 1 < xs.length; xi++) {
            for (int yi = 0; yi + 1 < ys.length; yi++) {
                if (!covered(rects, count, xs[xi], ys[yi], xs[xi + 1], ys[yi + 1])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean covered(int[] rects, int count, int x0, int y0, int x1, int y1) {
        for (int i = 0; i < count; i++) {
            int rx = rects[i * 4];
            int ry = rects[i * 4 + 1];
            int rw = rects[i * 4 + 2];
            int rh = rects[i * 4 + 3];
            if (rw > 0 && rh > 0 && rx <= x0 && ry <= y0 && rx + rw >= x1 && ry + rh >= y1) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(TILE_SIZE, value));
    }

    private static int[] unique(int[] values, int length) {
        int[] sorted = Arrays.copyOf(values, length);
        Arrays.sort(sorted);
        int out = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[i - 1]) {
                sorted[out++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, out);
    }

}
