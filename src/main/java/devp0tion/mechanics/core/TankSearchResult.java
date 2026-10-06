package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.List;

/**
 * Result of {@link TankStructure#findTank}: the valid tank(s) whose border contains a controller.
 */
public final class TankSearchResult {

    public enum Status {
        /** Exactly one valid tank has the controller in its border. */
        FOUND,
        /** No valid tank has the controller in its border. */
        NOT_FOUND,
        /** The given tile does not hold a controller. */
        NOT_A_CONTROLLER,
        /**
         * More than one valid rectangle has the controller in its border: the controller sits in a
         * wall shared by two tanks. Tanks may share walls, but a controller may not sit in a shared
         * wall (N8-1), so no tank is recognized ({@link #getTank()} is {@code null}).
         *
         * <p>Placing a controller that would end up like this is rejected beforehand
         * ({@link TankStructure#canPlaceController}). This status can still occur when another
         * change (a wall, valve, glass block or floor tile) completes a second tank around an
         * existing controller.
         * TODO(design): N8-1 and N11-1 only reject placing the controller or valve itself; whether
         * another placement (a wall, a valve, glass, a floor tile or a second tank) that puts an
         * existing controller or valve onto a shared wall is rejected too, or what the tanks do,
         * is undecided (currently: no tank is recognized for that controller; a valve in two
         * tanks serves neither). The same open question exists for a valve placement that gives an
         * existing pump a second source (N11-4; pumps are not implemented yet).
         */
        CONTROLLER_IN_SHARED_WALL
    }

    private final Status status;
    private final List<TankValidation> candidates;

    TankSearchResult(Status status, List<TankValidation> candidates) {
        this.status = status;
        this.candidates = Collections.unmodifiableList(candidates);
    }

    public Status getStatus() {
        return status;
    }

    /** The tank when {@link Status#FOUND}, otherwise {@code null}. */
    public TankValidation getTank() {
        return status == Status.FOUND ? candidates.get(0) : null;
    }

    /** Every valid rectangle found (one when FOUND, several when CONTROLLER_IN_SHARED_WALL, none otherwise). */
    public List<TankValidation> getCandidates() {
        return candidates;
    }

    @Override
    public String toString() {
        return "TankSearchResult[" + status + ", " + candidates + "]";
    }

}
