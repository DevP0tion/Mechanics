package devp0tion.mechanics.core;

/**
 * Per-tier pipe transport conditions (9-2). Pipe tiers follow {@link MineralTier} (9-11), and a
 * network uses the conditions of its lowest tier (9-10).
 *
 * <p>Only the transport amount is used so far: it is the amount one pipe cell holds as data (N7-3).
 *
 * <p>TODO(design): every pipe tier value is undecided (numbers.md table 2): transport amount,
 * maximum temperature, transportable fluid kinds and state (liquid/gas). Until then the game uses
 * {@link #UNDECIDED}; tests inject their own values.
 */
public interface PipeTierRules {

    /** Fluid units one pipe cell of this tier holds (N7-3). */
    int getTransportAmount(MineralTier tier);

    /** Placeholder for the undecided values: every lookup fails. */
    PipeTierRules UNDECIDED = tier -> {
        throw new UnsupportedOperationException(
                "TODO(design): pipe transport amount of " + tier + " is undecided (numbers.md table 2)");
    };

}
