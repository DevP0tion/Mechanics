package devp0tion.mechanics.wrench;

/**
 * Other texts of the locale files read by the game-independent tests: the log-fuelled pumps' hint
 * (N31-12) and the Korean name of ooze (N32-6).
 */
final class ModLocaleTest {

    private ModLocaleTest() {
    }

    public static void testAddFuelHint() {
        Check.equal("연료 넣기", LangFile.load("kr").get("controls", "mechanicsaddfueltip"), "N31-12");
        Check.equal("Add fuel", LangFile.load("en").get("controls", "mechanicsaddfueltip"), "N31-12");
    }

    public static void testOozeIsNamedInKoreanOnly() {
        Check.equal("우즈", LangFile.load("kr").get("fluid", "ooze"), "N32-6");
        Check.equal(null, LangFile.load("en").get("fluid", "ooze"), "English keeps the vanilla name (N32-4)");
        for (String fluid : new String[]{"lava", "slime", "spiritwater", "quicksand"}) {
            Check.equal(null, LangFile.load("kr").get("fluid", fluid), fluid + " keeps the vanilla name (N32-4)");
        }
    }

}
