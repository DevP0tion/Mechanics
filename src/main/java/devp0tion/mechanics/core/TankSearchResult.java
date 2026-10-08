package devp0tion.mechanics.core;

import java.util.Collections;
import java.util.List;

/**
 * Result of {@link TankStructure#findTank}: the tank of a controller under the ownership rules
 * (N13-3).
 */
public final class TankSearchResult {

    public enum Status {
        /**
         * The controller has a tank: the tank it keeps, still valid (first come, first served,
         * N13-3), or else the one valid rectangle for it.
         */
        FOUND,
        /** The controller keeps no valid tank and no rectangle is valid for it. */
        NOT_FOUND,
        /** The given tile does not hold a controller. */
        NOT_A_CONTROLLER,
        /**
         * The controller keeps no valid tank and more than one rectangle is valid for it: it sits in
         * a wall shared by two new tanks. A controller may not sit in a shared wall (N8-1), so no
         * tank is recognized ({@link #getTank()} is {@code null}).
         *
         * <p>Placing a controller that would end up like this is rejected beforehand
         * ({@link TankStructure#canPlaceController}). A controller that already keeps a valid tank
         * never ends up here: it keeps its tank (N13-3). One change (a wall, valve, glass block or
         * floor tile) can still complete two tanks around a controller that keeps no valid tank, so
         * neither came first: no tank is recognized for it. Two tanks of two controllers sharing a
         * wall with a valve are both recognized (N33-1, N29-8 replaced).
         */
        CONTROLLER_IN_SHARED_WALL
    }

    private final Status status;
    private final List<TankValidation> candidates;
    private final boolean unloadedCandidates;

    TankSearchResult(Status status, List<TankValidation> candidates) {
        this(status, candidates, false);
    }

    TankSearchResult(Status status, List<TankValidation> candidates, boolean unloadedCandidates) {
        this.status = status;
        this.candidates = Collections.unmodifiableList(candidates);
        this.unloadedCandidates = unloadedCandidates;
    }

    /**
     * Whether rectangles touching cells that are not loaded were left out
     * ({@link TankStructure#findLoadedTank}, N29-2): they may be recognized once those cells load.
     */
    public boolean hasUnloadedCandidates() {
        return unloadedCandidates;
    }

    public Status getStatus() {
        return status;
    }

    /** The tank when {@link Status#FOUND}, otherwise {@code null}. */
    public TankValidation getTank() {
        return status == Status.FOUND ? candidates.get(0) : null;
    }

    /**
     * The tank when FOUND, every rectangle valid for the controller when CONTROLLER_IN_SHARED_WALL,
     * none otherwise.
     */
    public List<TankValidation> getCandidates() {
        return candidates;
    }

    @Override
    public String toString() {
        return "TankSearchResult[" + status + ", " + candidates + "]";
    }

}
