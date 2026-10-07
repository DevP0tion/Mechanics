package devp0tion.mechanics.wrench;

import devp0tion.mechanics.core.PipeGrid;

import java.util.HashSet;
import java.util.Set;

/** {@link WrenchRefusal}: the reasons a right click would be refused (N30-1). */
final class WrenchRefusalTest {

    private WrenchRefusalTest() {
    }

    public static void testNoReasonWhenTheClickGoesThrough() {
        Check.isNull(WrenchRefusal.of(PipeGrid.Check.OK, false), "side");
        Check.isNull(WrenchRefusal.of(PipeGrid.Check.OK, true), "middle");
        Check.isNull(WrenchRefusal.of(null, false), "not known");
    }

    public static void testEngineResults() {
        Check.equal(WrenchRefusal.DIFFERENT_SOURCE_FLUID, WrenchRefusal.of(PipeGrid.Check.DIFFERENT_SOURCE_FLUID, false), "N16-3");
        Check.equal(WrenchRefusal.NOT_LOADED, WrenchRefusal.of(PipeGrid.Check.NOT_LOADED, false));
        Check.equal(WrenchRefusal.NOT_LOADED, WrenchRefusal.of(PipeGrid.Check.NOT_LOADED, true));
        Check.equal(WrenchRefusal.NOTHING_TO_LINK, WrenchRefusal.of(PipeGrid.Check.NOTHING_THERE, false));
        Check.equal(WrenchRefusal.NO_VERTICAL_LINK, WrenchRefusal.of(PipeGrid.Check.NOTHING_THERE, true),
                "a lone pump has no vertical link (9-9)");
    }

    public static void testEveryReasonHasItsOwnText() {
        Set<String> keys = new HashSet<>();
        for (WrenchRefusal refusal : WrenchRefusal.values()) {
            Check.isTrue(keys.add(refusal.localeKey), "unique key " + refusal.localeKey);
        }
    }

}
