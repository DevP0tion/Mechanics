package devp0tion.mechanics.core;

import java.util.Arrays;
import java.util.Collections;

/**
 * Pump sources: placement with two or more sources is rejected (N11-4); a source that appears
 * later does not replace the original one (N13-3 ②).
 */
final class PumpSourceTest {

    private PumpSourceTest() {
    }

    public static void testPlacementWithTwoOrMoreSourcesIsRejected() {
        Check.isTrue(Pump.canPlace(0), "no source yet");
        Check.isTrue(Pump.canPlace(1), "one source");
        Check.isFalse(Pump.canPlace(2), "liquid tile + valve, or two valves (N11-4)");
        Check.isFalse(Pump.canPlace(3), "three");
    }

    public static void testPumpKeepsItsOriginalSource() {
        Pump pump = new Pump(PumpTier.FIRE);
        LiquidTileSource tile = new LiquidTileSource(FluidType.LAVA);
        TankStorage tank = Fluids.tank(100);
        Check.equal(tile, pump.updateSource(Collections.singletonList(tile)), "the liquid tile under it");
        Check.equal(tile, pump.updateSource(Arrays.<FluidSource>asList(tank, tile)), "a valve attached later");
        Check.equal(tile, pump.getSource());
        Check.equal(tile, pump.updateSource(Arrays.<FluidSource>asList(tank, new LiquidTileSource(FluidType.LAVA))),
                "the same liquid tile, read again");
    }

    public static void testPumpTakesTheOnlySource() {
        Pump pump = new Pump(PumpTier.FIRE);
        TankStorage tank = Fluids.tank(100);
        Check.equal(tank, pump.updateSource(Collections.singletonList(tank)));
        Check.equal(tank, pump.updateSource(Arrays.<FluidSource>asList(tank, tank)), "the same tank twice");
        Check.isNull(pump.updateSource(Collections.<FluidSource>emptyList()), "source gone");
        Check.isNull(pump.getSource(), "no source");
    }

    public static void testOriginalSourceGoneWithTwoLeftGivesNone() {
        // TODO(design) in Pump.updateSource: neither of the remaining sources came first.
        Pump pump = new Pump(PumpTier.FIRE);
        LiquidTileSource tile = new LiquidTileSource(FluidType.SEAWATER);
        pump.setSource(tile);
        Check.isNull(pump.updateSource(Arrays.<FluidSource>asList(Fluids.tank(10), Fluids.tank(20))), "none");
    }

    public static void testLiquidTilesOfOneFluidAreTheSameSource() {
        Check.equal(new LiquidTileSource(FluidType.SEAWATER), new LiquidTileSource(FluidType.SEAWATER));
        Check.isFalse(new LiquidTileSource(FluidType.SEAWATER).equals(new LiquidTileSource(FluidType.FRESHWATER)),
                "seawater and freshwater differ (12-2)");
    }

}
