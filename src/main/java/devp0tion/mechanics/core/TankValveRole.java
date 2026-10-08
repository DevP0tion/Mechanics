package devp0tion.mechanics.core;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What a tank valve is for the tanks around it (N33-1, replacing N15-3, N29-8, N29-10 and the valve
 * part of N13-3).
 *
 * <ul>
 *     <li>Shared wall: the valve's tile is in the border of two recognized (valid) tanks. There it
 *     counts as a plain wall for both ({@link #isPlainWall}): no inlet or outlet, neither a
 *     destination nor a source, and pipes and pumps exchange no fluid through it: the pipes linked to
 *     it, the underground pipe on its tile and the pumps next to it ({@link PipeGrid#setValvePlainWall}).
 *     Both tanks are recognized ({@link TankStructure}), and its tier still counts toward their lowest
 *     tier (N13-5).</li>
 *     <li>Otherwise the valve works as a valve of the one recognized tank with it in its border
 *     ({@link #getTank}), also when a rectangle on the other side is no tank yet (for example it has
 *     no controller).</li>
 *     <li>It follows the judgments: a valve tank A used becomes a plain wall for A too once a tank B
 *     next to A is recognized, and works as a valve again from the judgment that ends the sharing (B
 *     breaks, etc.).</li>
 *     <li>No recognized tank: the valve belongs to the one tank kept with it in its border, inactive
 *     (that tank keeps its fluid and takes and gives nothing, 5-9). TODO(design): two or more tanks
 *     kept with it, none recognized (both walls broken, for example), is not decided; read as no
 *     tank, as when there is none.</li>
 * </ul>
 * The candidates are the controllers the game knows: their kept tanks (N13-3) and whether each is
 * recognized now (its judgment is active). Game independent, so it is tested by the plain test runner.
 *
 * @param <T> the game's controller
 */
public final class TankValveRole<T> {

    private final T tank;
    private final boolean plainWall;

    private TankValveRole(T tank, boolean plainWall) {
        this.tank = tank;
        this.plainWall = plainWall;
    }

    /**
     * The role of the valve at ({@code tileX}, {@code tileY}).
     *
     * @param controllers the controllers whose kept tank may have the tile in its border (others are
     *                    skipped)
     * @param keptTank    a controller's kept tank, or {@code null}
     * @param recognized  whether a controller's kept tank is recognized now (its judgment is active)
     */
    public static <T> TankValveRole<T> of(int tileX, int tileY, Iterable<T> controllers,
                                          Function<T, TankBounds> keptTank, Predicate<T> recognized) {
        T recognizedTank = null;
        int recognizedTanks = 0;
        T keptOnly = null;
        int kept = 0;
        for (T controller : controllers) {
            if (!TankStructure.isValveCell(keptTank.apply(controller), tileX, tileY)) {
                continue;
            }
            kept++;
            keptOnly = controller;
            if (recognized.test(controller)) {
                recognizedTanks++;
                recognizedTank = controller;
            }
        }
        if (recognizedTanks >= 2) {
            // N33-1: a shared wall.
            return new TankValveRole<>(null, true);
        }
        if (recognizedTanks == 1) {
            return new TankValveRole<>(recognizedTank, false);
        }
        return new TankValveRole<>(kept == 1 ? keptOnly : null, false);
    }

    /**
     * The controller of the tank the valve works for, or {@code null}: none, or the valve is a plain
     * wall.
     */
    public T getTank() {
        return tank;
    }

    /** Whether the valve is in a wall shared by two recognized tanks: a plain wall for both (N33-1). */
    public boolean isPlainWall() {
        return plainWall;
    }

    @Override
    public String toString() {
        return plainWall ? "TankValveRole[plain wall]" : "TankValveRole[" + tank + "]";
    }

}
