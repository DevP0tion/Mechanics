package devp0tion.mechanics.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * {@link PumpStatusText}: the pump window's and hover tooltip's lines (N36-44, N36-56, N36-57, N36-67).
 * The locale files have every key ({@code ModLocaleTest}).
 */
final class PumpStatusTextTest {

    private PumpStatusTextTest() {
    }

    /** The key itself in brackets, so the tests see which template was used. */
    private static final Function<String, String> KEYS = key -> {
        if (key.equals(PumpStatusText.OUTPUT)) {
            return "<direction>|<form>";
        }
        if (key.equals(PumpStatusText.PUMPING)) {
            return "[" + key + "]<fluid>";
        }
        return "[" + key + "]";
    };

    public static void testEveryStateHasAKey() {
        List<String> keys = PumpStatusText.keys();
        for (PumpResult.Status status : PumpResult.Status.values()) {
            for (PumpResult.Detail detail : PumpResult.Detail.values()) {
                for (PumpForm form : PumpForm.values()) {
                    String key = PumpStatusText.key(status, detail, form);
                    Check.isTrue(key != null && keys.contains(key), status + " " + detail + " " + form + ": " + key);
                }
            }
        }
    }

    public static void testKeysAreDistinct() {
        List<String> keys = PumpStatusText.keys();
        Check.equal(keys.size(), new HashSet<>(keys).size(), "no key twice: " + keys);
    }

    public static void testEveryStateKeyIsReached() {
        // Every state key is the text of some state the engine reports (no key left unused).
        Set<String> reached = new HashSet<>();
        for (PumpResult.Status status : PumpResult.Status.values()) {
            for (PumpResult.Detail detail : PumpResult.Detail.values()) {
                for (PumpForm form : PumpForm.values()) {
                    reached.add(PumpStatusText.key(status, detail, form));
                }
            }
        }
        for (String key : PumpStatusText.keys()) {
            boolean lineOne = key.equals(PumpStatusText.OUTPUT) || key.equals(PumpStatusText.FORM_VALVE)
                    || key.equals(PumpStatusText.FORM_GROUND);
            for (Direction d : Direction.values()) {
                lineOne |= key.equals(PumpStatusText.directionKey(d));
            }
            Check.isTrue(lineOne || reached.contains(key), "reached: " + key);
        }
    }

    public static void testStatesTheEngineReports() {
        PumpForm any = PumpForm.GROUND;
        PumpResult.Detail none = PumpResult.Detail.NONE;
        Check.equal(PumpStatusText.WAITING, PumpStatusText.key(PumpResult.Status.WAITING, none, any), "N36-67");
        Check.equal(PumpStatusText.PUMPING, PumpStatusText.key(PumpResult.Status.PUMPED, none, any));
        Check.equal(PumpStatusText.WIRE_OFF, PumpStatusText.key(PumpResult.Status.DISABLED, none, any), "11-3");
        Check.equal(PumpStatusText.FLUID_NOT_ALLOWED, PumpStatusText.key(PumpResult.Status.FLUID_NOT_ALLOWED, none, any));
        Check.equal(PumpStatusText.NO_FUEL, PumpStatusText.key(PumpResult.Status.NO_FUEL, none, any));
    }

    public static void testNoSourceSaysWhy() {
        PumpResult.Status s = PumpResult.Status.NO_SOURCE;
        // The form says where the missing source is (N36-6, N36-15, N36-16).
        Check.equal(PumpStatusText.NO_SOURCE_GROUND, PumpStatusText.key(s, PumpResult.Detail.SOURCE_MISSING, PumpForm.GROUND));
        Check.equal(PumpStatusText.NO_SOURCE_VALVE, PumpStatusText.key(s, PumpResult.Detail.SOURCE_MISSING, PumpForm.VALVE));
        for (PumpForm form : PumpForm.values()) {
            Check.equal(PumpStatusText.SOURCE_OFF, PumpStatusText.key(s, PumpResult.Detail.SOURCE_OFF, form), "N27-4");
            Check.equal(PumpStatusText.SOURCE_EMPTY, PumpStatusText.key(s, PumpResult.Detail.SOURCE_EMPTY, form));
            Check.equal(PumpStatusText.SOURCE_OTHER_FLUID, PumpStatusText.key(s, PumpResult.Detail.SOURCE_OTHER_FLUID, form),
                    "inactive (N20-3, N36-53)");
        }
    }

    public static void testNowhereToPushSaysWhy() {
        // N36-56: "넣을 곳 없음(이유)".
        PumpResult.Status s = PumpResult.Status.NO_DESTINATION;
        for (PumpForm form : PumpForm.values()) {
            Check.equal(PumpStatusText.NO_DESTINATION, PumpStatusText.key(s, PumpResult.Detail.DESTINATION_MISSING, form));
            Check.equal(PumpStatusText.DESTINATION_FULL, PumpStatusText.key(s, PumpResult.Detail.DESTINATION_FULL, form), "N7-4");
            Check.equal(PumpStatusText.DESTINATION_OTHER_FLUID,
                    PumpStatusText.key(s, PumpResult.Detail.DESTINATION_OTHER_FLUID, form), "N36-46");
            Check.equal(PumpStatusText.DESTINATION_OFF, PumpStatusText.key(s, PumpResult.Detail.DESTINATION_OFF, form), "N27-4");
        }
    }

    public static void testADetailOfAnotherStatusIsItsPlainestCase() {
        // The engine never pairs them; the text still says something true of the status.
        Check.equal(PumpStatusText.NO_SOURCE_VALVE,
                PumpStatusText.key(PumpResult.Status.NO_SOURCE, PumpResult.Detail.DESTINATION_FULL, PumpForm.VALVE));
        Check.equal(PumpStatusText.NO_SOURCE_GROUND,
                PumpStatusText.key(PumpResult.Status.NO_SOURCE, PumpResult.Detail.NONE, PumpForm.GROUND));
        Check.equal(PumpStatusText.NO_DESTINATION,
                PumpStatusText.key(PumpResult.Status.NO_DESTINATION, PumpResult.Detail.SOURCE_EMPTY, PumpForm.GROUND));
        Check.equal(PumpStatusText.NO_FUEL,
                PumpStatusText.key(PumpResult.Status.NO_FUEL, PumpResult.Detail.SOURCE_EMPTY, PumpForm.GROUND));
    }

    public static void testOutputLineNamesTheDirectionAndTheForm() {
        // N36-44: line 1, the output direction (N36-10) and the form (N36-6).
        Check.equal("[mechanicspumpdireast]|[mechanicspumpformvalve]",
                PumpStatusText.outputLine(Direction.EAST, PumpForm.VALVE, KEYS));
        Check.equal("[mechanicspumpdirnorth]|[mechanicspumpformground]",
                PumpStatusText.outputLine(Direction.NORTH, PumpForm.GROUND, KEYS));
        Set<String> directions = new HashSet<>();
        for (Direction d : Direction.values()) {
            directions.add(PumpStatusText.directionKey(d));
        }
        Check.equal(4, directions.size(), "one name per direction");
    }

    public static void testStateLineNamesTheMovedFluid() {
        Check.equal("[mechanicspumppumping]해수",
                PumpStatusText.stateLine(PumpResult.Status.PUMPED, PumpResult.Detail.NONE, PumpForm.GROUND, "해수", KEYS));
        Check.equal("[mechanicspumpnosourcevalve]", PumpStatusText.stateLine(PumpResult.Status.NO_SOURCE,
                PumpResult.Detail.SOURCE_MISSING, PumpForm.VALVE, null, KEYS));
    }

}
