package devp0tion.mechanics.wrench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The wrench's texts are in both locale files (N30-1, N30-2, N30-3, N30-5). */
final class WrenchLocaleTest {

    private WrenchLocaleTest() {
    }

    private static List<String> uiKeys() {
        List<String> keys = new ArrayList<>(Arrays.asList(WrenchInfoText.FLUID, WrenchInfoText.EMPTY, WrenchInfoText.LINKED,
                WrenchInfoText.BLOCKED, WrenchInfoText.NONE, WrenchInfoText.VERTICAL));
        keys.addAll(Arrays.asList(WrenchInfoText.DIRECTION_KEYS));
        for (WrenchRefusal refusal : WrenchRefusal.values()) {
            keys.add(refusal.localeKey);
        }
        return keys;
    }

    public static void testEveryTextIsInBothLanguages() {
        for (String language : new String[]{"kr", "en"}) {
            LangFile file = LangFile.load(language);
            for (String key : uiKeys()) {
                file.ui(key);
            }
            for (String[] key : new String[][]{{"misc", "mechanicswrenchbasicmode"}, {"misc", "mechanicswrenchundergroundmode"},
                    {"controls", "mechanicswrenchmode"}, {"controls", "mechanicswrenchmodetip"},
                    {"itemtooltip", "mechanicswrenchtip"}}) {
                Check.isTrue(file.get(key[0], key[1]) != null, language + " [" + key[0] + "] " + key[1]);
            }
        }
    }

    public static void testNames() {
        Check.equal("공학 렌치", LangFile.load("kr").get("item", "mechanicswrench"), "N30-3");
        Check.equal("Engineering Wrench", LangFile.load("en").get("item", "mechanicswrench"), "N30-3");
        Check.equal("비어 있음", LangFile.load("kr").ui(WrenchInfoText.EMPTY), "N30-2");
        Check.equal("Empty", LangFile.load("en").ui(WrenchInfoText.EMPTY), "N30-2");
        Check.equal("지하 모드", LangFile.load("kr").get("misc", "mechanicswrenchundergroundmode"), "N30-5");
        Check.equal("기본 모드", LangFile.load("kr").get("misc", "mechanicswrenchbasicmode"), "N30-5");
    }

    public static void testTheTipShowsTheModeKeyAndTheTooltip() {
        for (String language : new String[]{"kr", "en"}) {
            String tip = LangFile.load(language).get("itemtooltip", "mechanicswrenchtip");
            Check.isTrue(tip.contains("[input=mechanicswrenchmode]"), language + ": the bound key (N30-5)");
        }
    }

}
