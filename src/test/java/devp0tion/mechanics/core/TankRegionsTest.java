package devp0tion.mechanics.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeping a tank's regions loaded together (N15-6), and the interior placement allow-list (N16-2).
 */
final class TankRegionsTest {

    private TankRegionsTest() {
    }

    private static GridPos r(int x, int y) {
        return new GridPos(x, y);
    }

    public static void testRegionsOfATank() {
        Check.equal(Collections.singletonList(r(0, 0)), TankRegions.regionsOf(new TankBounds(2, 3, 7, 7), 16));
        Check.equal(Arrays.asList(r(0, 0), r(1, 0)), TankRegions.regionsOf(new TankBounds(12, 0, 7, 3), 16));
        Check.equal(Arrays.asList(r(0, 0), r(1, 0), r(0, 1), r(1, 1)),
                TankRegions.regionsOf(new TankBounds(14, 14, 3, 3), 16));
        Check.equal(Arrays.asList(r(-1, 0), r(0, 0)), TankRegions.regionsOf(new TankBounds(-1, 0, 3, 3), 16),
                "floor division");
        Check.throwsException(IllegalArgumentException.class,
                () -> TankRegions.regionsOf(new TankBounds(0, 0, 3, 3), 0));
    }

    private static Set<GridPos> set(GridPos... regions) {
        return new HashSet<>(Arrays.asList(regions));
    }

    public static void testATankWithOneKeptRegionKeepsAll() {
        List<Collection<GridPos>> tanks = Collections.<Collection<GridPos>>singletonList(Arrays.asList(r(0, 0), r(1, 0)));
        List<Set<GridPos>> groups = TankRegions.groupsToKeepLoaded(tanks, region -> region.equals(r(1, 0)));
        Check.equal(1, groups.size());
        Check.equal(set(r(0, 0), r(1, 0)), groups.get(0));
    }

    public static void testNothingKeptUnloadsTogether() {
        List<Collection<GridPos>> tanks = Collections.<Collection<GridPos>>singletonList(Arrays.asList(r(0, 0), r(1, 0)));
        Check.equal(0, TankRegions.groupsToKeepLoaded(tanks, region -> false).size());
    }

    public static void testTanksSharingARegionAreKeptTogether() {
        // A spans R0 and R1, B spans R1 and R2, C is elsewhere. A player at R0 keeps A and B.
        List<Collection<GridPos>> tanks = Arrays.<Collection<GridPos>>asList(
                Arrays.asList(r(0, 0), r(1, 0)),
                Arrays.asList(r(1, 0), r(2, 0)),
                Arrays.asList(r(5, 5)));
        List<Set<GridPos>> groups = TankRegions.groupsToKeepLoaded(tanks, region -> region.equals(r(0, 0)));
        Check.equal(1, groups.size());
        Check.equal(set(r(0, 0), r(1, 0), r(2, 0)), groups.get(0));
    }

    public static void testSeparateGroupsStaySeparate() {
        List<Collection<GridPos>> tanks = Arrays.<Collection<GridPos>>asList(
                Arrays.asList(r(0, 0), r(1, 0)),
                Arrays.asList(r(5, 5), r(5, 6)));
        List<Set<GridPos>> groups = TankRegions.groupsToKeepLoaded(tanks, region -> true);
        Check.equal(2, groups.size());
    }

    public static void testInteriorAllowList() {
        Check.isTrue(TankInteriorRule.isAllowed(TankInteriorRule.Placement.GLASS_BLOCK), "glass (N16-2)");
        Check.isTrue(TankInteriorRule.isAllowed(TankInteriorRule.Placement.TANK_FLOOR), "tank floor");
        Check.isTrue(TankInteriorRule.isAllowed(TankInteriorRule.Placement.UNDERGROUND_PIPE), "underground pipe");
        Check.isFalse(TankInteriorRule.isAllowed(TankInteriorRule.Placement.BASIC_PIPE), "basic pipe: underground only");
        Check.isFalse(TankInteriorRule.isAllowed(TankInteriorRule.Placement.OTHER), "anything else");
    }

}
