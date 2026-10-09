package devp0tion.mechanics.wrench;

import devp0tion.mechanics.core.PumpStatusText;

/**
 * Other texts of the locale files read by the game-independent tests: the log-fuelled pumps' hint
 * (N31-12), the Korean name of ooze (N32-6), the wrench's reason toward a pump's side that cannot be
 * linked (N36-61), the pump texts N36-8 removed, the pump item's form texts (N36-42, N36-54) and the
 * pump's state texts (N36-44, N36-57).
 */
final class ModLocaleTest {

    private ModLocaleTest() {
    }

    /**
     * The pump texts use ASCII separators only (N36-71): the em dash, the en dash and the middle dot
     * are not in the English font atlas and are drawn as '?' (review of N36 step C), and vanilla's
     * Korean file never uses them either.
     */
    private static void checkPumpSeparators(String text, String name) {
        for (char c : new char[]{'\u2014', '\u2013', '\u00B7'}) {
            Check.isFalse(text.indexOf(c) >= 0, name + ": no U+" + String.format("%04X", (int) c) + " in " + text);
        }
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
        // click hint in the inventory (N36-65); wording settled (N36-48, N36-71): their presence and
        // their separators.
        for (String lang : new String[]{"kr", "en"}) {
            LangFile file = LangFile.load(lang);
            for (String key : new String[]{"pumpformvalvetip", "pumpformgroundtip", "pumpformswitchtip"}) {
                String text = file.get("itemtooltip", key);
                Check.isTrue(text != null, lang + " [itemtooltip] " + key);
                checkPumpSeparators(text, lang + " [itemtooltip] " + key);
            }
            String hint = file.get("controls", "mechanicspumpformtip");
            Check.isTrue(hint != null, lang + " [controls] mechanicspumpformtip");
            checkPumpSeparators(hint, lang + " [controls] mechanicspumpformtip");
            // Kept beside the form line (N36-54).
            Check.isTrue(file.get("itemtooltip", "pumpwiretip") != null, lang + " pumpwiretip");
            Check.isTrue(file.get("itemtooltip", "tankinteriortip") != null, lang + " tankinteriortip");
        }
    }

    public static void testPumpStatusTexts() {
        // N36-44, N36-56, N36-57, N36-67: every line of the pump window and tooltip; wording settled
        // (N36-48, N36-71): their presence, their separators and their placeholders.
        for (String lang : new String[]{"kr", "en"}) {
            LangFile file = LangFile.load(lang);
            for (String key : PumpStatusText.keys()) {
                String text = file.ui(key);
                Check.isFalse(text.isEmpty(), lang + " [ui] " + key);
                checkPumpSeparators(text, lang + " [ui] " + key);
            }
            String output = file.ui(PumpStatusText.OUTPUT);
            Check.isTrue(output.contains("<direction>") && output.contains("<form>"), lang + " output line: " + output);
            Check.isTrue(file.ui(PumpStatusText.PUMPING).contains("<fluid>"), lang + " pumping line");
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
