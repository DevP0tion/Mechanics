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
         * More than one valid rectangle has the controller in its border (for example two tanks
         * that share the wall holding the controller). The rules do not say which one wins.
         * TODO(design): decide how a controller shared by several valid rectangles is resolved.
         */
        AMBIGUOUS
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

    /** Every valid rectangle found (one when FOUND, several when AMBIGUOUS, none otherwise). */
    public List<TankValidation> getCandidates() {
        return candidates;
    }

    @Override
    public String toString() {
        return "TankSearchResult[" + status + ", " + candidates + "]";
    }

}
