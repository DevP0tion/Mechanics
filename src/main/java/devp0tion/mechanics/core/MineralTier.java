package devp0tion.mechanics.core;

import java.util.OptionalInt;

/**
 * The 11 vanilla bars used for mineral walls and pipes (8-2, 9-11), in tier order.
 * The order is the pickaxe mining-power order (8-4) with nightsteel and spiderite appended
 * after ancientfossil (N4-3), so {@link #ordinal()} is the tier order.
 *
 * <p>The game objects are {@code objects.MineralWallObject} (walls); pipes use the same tiers
 * ({@link PipeTierRules}).
 */
public enum MineralTier {

    //            bar item             capacity    pickaxe mining   wall mining
    //                                 multiplier  power (toolDps)  tier (toolTier)
    COPPER(       "copperbar",          1,         65,              0),
    IRON(         "ironbar",            2,         80,              0),
    GOLD(         "goldbar",            3,         95,              0),
    DEMONIC(      "demonicbar",         5,        125,              2),
    IVY(          "ivybar",             8,        155,              4),
    TUNGSTEN(     "tungstenbar",       12,        185,              6),
    GLACIAL(      "glacialbar",        17,        200,              7),
    MYCELIUM(     "myceliumbar",       23,        230,              9),
    ANCIENTFOSSIL("ancientfossilbar",  30,        245,             10),
    // No nightsteel/spiderite pickaxe exists; the tier order replaces the mining power (N4-3).
    // Their wall mining tier is the same as ancientfossil's (N5-2).
    NIGHTSTEEL(   "nightsteelbar",     38, Missing.PICKAXE,        10),
    SPIDERITE(    "spideritebar",      47, Missing.PICKAXE,        10);

    // Sources:
    //   capacity multiplier: N4-2
    //   pickaxe mining power: 8-4 (vanilla CustomPickaxeToolItem toolDps, domain rule S5)
    //   wall mining tier: the toolTier of that mineral's vanilla pickaxe (user decision), N5-2 for
    //   nightsteel/spiderite

    private final String barStringID;
    private final int capacityMultiplier;
    private final int pickaxeMiningPower;
    private final int wallToolTier;

    MineralTier(String barStringID, int capacityMultiplier, int pickaxeMiningPower, int wallToolTier) {
        this.barStringID = barStringID;
        this.capacityMultiplier = capacityMultiplier;
        this.pickaxeMiningPower = pickaxeMiningPower;
        this.wallToolTier = wallToolTier;
    }

    /** Vanilla bar item stringID used as the wall/pipe material (8-2, 9-11). */
    public String getBarStringID() {
        return barStringID;
    }

    /** Multiplier applied to the base tank capacity (N4-2). */
    public int getCapacityMultiplier() {
        return capacityMultiplier;
    }

    /**
     * Mining power (toolDps) of this mineral's vanilla pickaxe (8-4).
     * Empty for nightsteel and spiderite, which have no pickaxe; their place in the
     * tier order replaces it (N4-3).
     */
    public OptionalInt getPickaxeMiningPower() {
        return pickaxeMiningPower == Missing.PICKAXE ? OptionalInt.empty() : OptionalInt.of(pickaxeMiningPower);
    }

    /** Tool tier needed to mine this mineral's wall: the toolTier of its pickaxe (N5-2 for nightsteel/spiderite). */
    public int getWallToolTier() {
        return wallToolTier;
    }

    /** True if this tier is at least as high as {@code other} in the tier order. */
    public boolean isAtLeast(MineralTier other) {
        return compareTo(other) >= 0;
    }

    /** The lower of two tiers. */
    public static MineralTier lowest(MineralTier a, MineralTier b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** The highest tier in the tier order (spiderite, N4-3). */
    public static MineralTier highest() {
        MineralTier[] tiers = values();
        return tiers[tiers.length - 1];
    }

    /** Finds the tier whose bar item has the given stringID, or {@code null}. */
    public static MineralTier fromBarStringID(String barStringID) {
        for (MineralTier tier : values()) {
            if (tier.barStringID.equals(barStringID)) {
                return tier;
            }
        }
        return null;
    }

    // Marker values for the constant table above. Kept in a nested class because enum constant
    // arguments cannot reference the enum's own static fields (illegal forward reference).
    private static final class Missing {
        static final int PICKAXE = -1;
    }

}
