package devp0tion.mechanics.wrench;

/**
 * Other texts of the locale files read by the game-independent tests: the log-fuelled pumps' hint
 * (N31-12), the Korean name of ooze (N32-6), the wrench's reason toward a pump's side that cannot be
 * linked (N36-61), the pump texts N36-8 removed and the pump item's form texts (N36-42, N36-54).
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

    public static void testPumpSideReason() {
        Check.equal("펌프의 연결될 수 없는 쪽이라 바꿀 수 없습니다.",
                LangFile.load("kr").get("ui", "mechanicswrenchpumpside"), "N36-61");
        Check.equal("Cannot change: that side of the pump cannot be connected.",
                LangFile.load("en").get("ui", "mechanicswrenchpumpside"), "N36-61");
    }

    public static void testPumpFormTexts() {
        // N36-42, N36-54: the form line and how to switch it in the item description, the right
        // click hint in the inventory (N36-65); drafts (N36-48), so only their presence.
        for (String lang : new String[]{"kr", "en"}) {
            LangFile file = LangFile.load(lang);
            for (String key : new String[]{"pumpformvalvetip", "pumpformgroundtip", "pumpformswitchtip"}) {
                Check.isTrue(file.get("itemtooltip", key) != null, lang + " [itemtooltip] " + key);
            }
            Check.isTrue(file.get("controls", "mechanicspumpformtip") != null, lang + " [controls] mechanicspumpformtip");
            // Kept beside the form line (N36-54).
            Check.isTrue(file.get("itemtooltip", "pumpwiretip") != null, lang + " pumpwiretip");
            Check.isTrue(file.get("itemtooltip", "tankinteriortip") != null, lang + " tankinteriortip");
        }
    }

    public static void testRemovedPumpSourceTexts() {
        // N36-8: one source per pump, so no sources rule in the description and no fluid refusal.
        for (String lang : new String[]{"kr", "en"}) {
            Check.equal(null, LangFile.load(lang).get("itemtooltip", "pumpsourcestip"), lang + " pumpsourcestip");
            Check.equal(null, LangFile.load(lang).get("ui", "mechanicswrenchsourcefluid"), lang + " mechanicswrenchsourcefluid");
        }
    }

}
