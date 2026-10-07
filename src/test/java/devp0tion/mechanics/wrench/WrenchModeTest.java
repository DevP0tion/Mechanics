package devp0tion.mechanics.wrench;

/** {@link WrenchMode}: the wrench's mode stored on the item (N30-5). */
final class WrenchModeTest {

    private WrenchModeTest() {
    }

    public static void testBasicIsTheDefault() {
        Check.equal(WrenchMode.BASIC, WrenchMode.fromSaveName(null), "no stored mode (N30-5)");
        Check.equal(WrenchMode.BASIC, WrenchMode.fromSaveName(""), "empty");
        Check.equal(WrenchMode.BASIC, WrenchMode.fromSaveName("sideways"), "unknown");
        Check.equal(WrenchMode.BASIC, WrenchMode.fromOrdinal(7), "unknown packet byte");
    }

    public static void testSavedNamesRoundTrip() {
        for (WrenchMode mode : WrenchMode.values()) {
            Check.equal(mode, WrenchMode.fromSaveName(mode.saveName()), mode.name());
            Check.equal(mode, WrenchMode.fromOrdinal(mode.ordinal()), mode.name());
        }
        Check.equal("underground", WrenchMode.UNDERGROUND.saveName());
        Check.equal("basic", WrenchMode.BASIC.saveName());
    }

    public static void testTheKeySwitchesBetweenTheTwoModes() {
        Check.equal(WrenchMode.UNDERGROUND, WrenchMode.BASIC.next());
        Check.equal(WrenchMode.BASIC, WrenchMode.UNDERGROUND.next());
    }

}
