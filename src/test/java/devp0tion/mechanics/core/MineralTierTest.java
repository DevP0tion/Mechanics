package devp0tion.mechanics.core;

import java.util.Arrays;
import java.util.OptionalInt;

/** MineralTier data (8-2, 8-4, 8-5, N4-2, N4-3, N5-2). */
final class MineralTierTest {

    private MineralTierTest() {
    }

    public static void testOrder() {
        Check.equal(Arrays.asList(
                        MineralTier.COPPER, MineralTier.IRON, MineralTier.GOLD, MineralTier.DEMONIC,
                        MineralTier.IVY, MineralTier.TUNGSTEN, MineralTier.GLACIAL, MineralTier.MYCELIUM,
                        MineralTier.ANCIENTFOSSIL, MineralTier.NIGHTSTEEL, MineralTier.SPIDERITE),
                Arrays.asList(MineralTier.values()));
    }

    public static void testBarStringIDs() {
        String[] expected = {"copperbar", "ironbar", "goldbar", "demonicbar", "ivybar", "tungstenbar",
                "glacialbar", "myceliumbar", "ancientfossilbar", "nightsteelbar", "spideritebar"};
        for (int i = 0; i < expected.length; i++) {
            MineralTier tier = MineralTier.values()[i];
            Check.equal(expected[i], tier.getBarStringID(), tier.name());
            Check.equal(tier, MineralTier.fromBarStringID(expected[i]), "fromBarStringID " + expected[i]);
        }
        Check.isNull(MineralTier.fromBarStringID("stone"), "unknown bar");
    }

    public static void testCapacityMultipliers() {
        int[] expected = {1, 2, 3, 5, 8, 12, 17, 23, 30, 38, 47};
        for (int i = 0; i < expected.length; i++) {
            MineralTier tier = MineralTier.values()[i];
            Check.equal(expected[i], tier.getCapacityMultiplier(), tier.name());
        }
    }

    public static void testPickaxeMiningPower() {
        int[] expected = {65, 80, 95, 125, 155, 185, 200, 230, 245};
        for (int i = 0; i < expected.length; i++) {
            MineralTier tier = MineralTier.values()[i];
            Check.equal(OptionalInt.of(expected[i]), tier.getPickaxeMiningPower(), tier.name());
        }
        Check.equal(OptionalInt.empty(), MineralTier.NIGHTSTEEL.getPickaxeMiningPower(), "nightsteel");
        Check.equal(OptionalInt.empty(), MineralTier.SPIDERITE.getPickaxeMiningPower(), "spiderite");
    }

    public static void testCopperIronGoldAreDistinct() {
        // 8-5: copper, iron and gold walls must have different values.
        int copper = MineralTier.COPPER.getPickaxeMiningPower().getAsInt();
        int iron = MineralTier.IRON.getPickaxeMiningPower().getAsInt();
        int gold = MineralTier.GOLD.getPickaxeMiningPower().getAsInt();
        Check.isTrue(copper < iron && iron < gold, "copper < iron < gold");
    }

    public static void testWallToolTier() {
        int[] expected = {0, 0, 0, 2, 4, 6, 7, 9, 10, 10, 10};
        for (int i = 0; i < expected.length; i++) {
            MineralTier tier = MineralTier.values()[i];
            Check.equal(expected[i], tier.getWallToolTier(), tier.name());
        }
        // N5-2: nightsteel and spiderite use ancientfossil's tier.
        Check.equal(MineralTier.ANCIENTFOSSIL.getWallToolTier(), MineralTier.NIGHTSTEEL.getWallToolTier());
        Check.equal(MineralTier.ANCIENTFOSSIL.getWallToolTier(), MineralTier.SPIDERITE.getWallToolTier());
    }

    public static void testMultipliersIncreaseWithTier() {
        MineralTier[] tiers = MineralTier.values();
        for (int i = 1; i < tiers.length; i++) {
            Check.isTrue(tiers[i].getCapacityMultiplier() > tiers[i - 1].getCapacityMultiplier(),
                    tiers[i] + " multiplier > " + tiers[i - 1]);
        }
    }

    public static void testLowestAndIsAtLeast() {
        Check.equal(MineralTier.COPPER, MineralTier.lowest(MineralTier.SPIDERITE, MineralTier.COPPER));
        Check.equal(MineralTier.IRON, MineralTier.lowest(MineralTier.IRON, MineralTier.GOLD));
        Check.equal(MineralTier.GOLD, MineralTier.lowest(MineralTier.GOLD, MineralTier.GOLD));
        Check.isTrue(MineralTier.NIGHTSTEEL.isAtLeast(MineralTier.ANCIENTFOSSIL), "nightsteel >= ancientfossil");
        Check.isTrue(MineralTier.SPIDERITE.isAtLeast(MineralTier.NIGHTSTEEL), "spiderite >= nightsteel");
        Check.isTrue(MineralTier.IRON.isAtLeast(MineralTier.IRON), "iron >= iron");
        Check.isFalse(MineralTier.COPPER.isAtLeast(MineralTier.IRON), "copper < iron");
    }

}
