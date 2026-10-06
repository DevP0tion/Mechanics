package devp0tion.mechanics.core;

/** PumpTier rules (11-3, 11-7, 11-8, 12-1, 12-5, 12-6, N1-2, N3-2, N3-3, N6-1 to N6-4). */
final class PumpTierTest {

    private PumpTierTest() {
    }

    public static void testTierNumbers() {
        Check.equal(1, PumpTier.MANUAL.getTier());
        Check.equal(2, PumpTier.FIRE.getTier());
        Check.equal(3, PumpTier.ADVANCED_FIRE.getTier());
        Check.equal(4, PumpTier.ELECTRIC.getTier());
    }

    public static void testManualPumpsWaterOnly() {
        for (FluidType fluid : FluidType.values()) {
            boolean expected = fluid == FluidType.SEAWATER || fluid == FluidType.FRESHWATER;
            Check.equal(expected, PumpTier.MANUAL.canPump(fluid), "MANUAL " + fluid);
        }
    }

    public static void testFirePumpsWaterAndLava() {
        for (FluidType fluid : FluidType.values()) {
            boolean expected = fluid == FluidType.SEAWATER || fluid == FluidType.FRESHWATER || fluid == FluidType.LAVA;
            Check.equal(expected, PumpTier.FIRE.canPump(fluid), "FIRE " + fluid);
        }
    }

    public static void testAdvancedFireAndUpPumpEverything() {
        for (FluidType fluid : FluidType.values()) {
            Check.isTrue(PumpTier.ADVANCED_FIRE.canPump(fluid), "ADVANCED_FIRE " + fluid);
            Check.isTrue(PumpTier.ELECTRIC.canPump(fluid), "ELECTRIC " + fluid);
        }
    }

    public static void testNullFluidIsNeverPumped() {
        for (PumpTier tier : PumpTier.values()) {
            Check.isFalse(tier.canPump(null), tier + " null fluid");
        }
    }

    public static void testWireControlFromTierTwo() {
        Check.isFalse(PumpTier.MANUAL.isWireControllable(), "MANUAL");
        Check.isTrue(PumpTier.FIRE.isWireControllable(), "FIRE");
        Check.isTrue(PumpTier.ADVANCED_FIRE.isWireControllable(), "ADVANCED_FIRE");
        Check.isTrue(PumpTier.ELECTRIC.isWireControllable(), "ELECTRIC");
    }

    public static void testPowerAndFuel() {
        Check.equal(PumpTier.Power.HAND_CLICK, PumpTier.MANUAL.getPower());
        Check.equal(PumpTier.Power.LOG_FUEL, PumpTier.FIRE.getPower());
        Check.equal(PumpTier.Power.LOG_FUEL, PumpTier.ADVANCED_FIRE.getPower());
        Check.equal(PumpTier.Power.ELECTRICITY, PumpTier.ELECTRIC.getPower());
        Check.isFalse(PumpTier.MANUAL.usesLogFuel(), "manual pump uses no fuel (N1-2)");
        Check.isTrue(PumpTier.FIRE.usesLogFuel(), "FIRE");
        Check.isTrue(PumpTier.ADVANCED_FIRE.usesLogFuel(), "ADVANCED_FIRE");
        Check.isFalse(PumpTier.ELECTRIC.usesLogFuel(), "ELECTRIC");
        Check.equal("anylog", PumpTier.LOG_FUEL_INGREDIENT);
    }

    public static void testManualPumpValues() {
        Check.equal(20, PumpTier.MANUAL.getUnitsPerCycle(), "units per click (N3-2)");
        Check.equal(2 * FluidUnits.BUCKET, PumpTier.MANUAL.getUnitsPerCycle(), "two buckets per click");
        Check.equal(20, PumpTier.MANUAL.getCycleTicks(), "click cooldown (N3-3)");
        Check.throwsException(IllegalStateException.class, PumpTier.MANUAL::getLogBurnTicks);
    }

    public static void testFirePumpValues() {
        Check.equal(20, PumpTier.FIRE.getUnitsPerCycle(), "N6-1");
        Check.equal(20, PumpTier.FIRE.getCycleTicks(), "N6-1");
        Check.equal(100, PumpTier.FIRE.getLogBurnTicks(), "one log = 5 s (N6-2)");
        Check.equal(100, PumpTier.FIRE.getUnitsPerLog(), "100 per log (N6-2)");
    }

    public static void testAdvancedFirePumpValues() {
        Check.equal(40, PumpTier.ADVANCED_FIRE.getUnitsPerCycle(), "twice the fire pump (N6-3)");
        Check.equal(20, PumpTier.ADVANCED_FIRE.getCycleTicks(), "N6-3");
        Check.equal(100, PumpTier.ADVANCED_FIRE.getLogBurnTicks(), "same fuel as the fire pump (N6-4)");
        Check.equal(200, PumpTier.ADVANCED_FIRE.getUnitsPerLog(), "200 per log (N6-4)");
    }

    public static void testElectricPumpValuesAreUndecided() {
        Check.isFalse(PumpTier.ELECTRIC.hasDecidedValues(), "ELECTRIC undecided");
        Check.isTrue(PumpTier.MANUAL.hasDecidedValues(), "MANUAL decided");
        Check.throwsException(UnsupportedOperationException.class, PumpTier.ELECTRIC::getUnitsPerCycle);
        Check.throwsException(UnsupportedOperationException.class, PumpTier.ELECTRIC::getCycleTicks);
    }

}
