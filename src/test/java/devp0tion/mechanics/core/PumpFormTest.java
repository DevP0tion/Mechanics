package devp0tion.mechanics.core;

/** {@link PumpForm}: the pump's form as item data (N36-11). */
final class PumpFormTest {

    private PumpFormTest() {
    }

    public static void testGroundWithoutFormData() {
        // N36-32: a pump item without form data (creative item tab, /give) is a ground pump.
        Check.equal(PumpForm.GROUND, PumpForm.fromSaveName(null), "no data (N36-32)");
        Check.equal(PumpForm.GROUND, PumpForm.fromSaveName(""), "empty");
        Check.equal(PumpForm.GROUND, PumpForm.fromSaveName("sideways"), "unknown");
    }

    public static void testSavedNamesRoundTrip() {
        for (PumpForm form : PumpForm.values()) {
            Check.equal(form, PumpForm.fromSaveName(form.saveName()), form.name());
        }
        // The item data values (pumpform=valve on crafted pumps, N36-14).
        Check.equal("valve", PumpForm.VALVE.saveName());
        Check.equal("ground", PumpForm.GROUND.saveName());
    }

}
