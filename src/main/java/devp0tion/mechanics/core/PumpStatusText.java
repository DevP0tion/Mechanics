package devp0tion.mechanics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A pump's state as text (N36-44): shown in the window of the log-fueled pumps and in the tooltip
 * while the cursor is over any pump, whatever the player holds, with the same words (N36-57); the
 * manual pump has no window, only the tooltip. Game independent: the words come from a lookup of the
 * {@code [ui]} locale keys, as for the wrench's tooltip ({@code WrenchInfoText}).
 *
 * <ol>
 *     <li>The output line: the output direction (N36-1, N36-10) and the form (N36-6).</li>
 *     <li>The state line: what the last cycle did ({@link PumpResult.Status}) and, when it stopped for
 *     want of a source or a destination, why ({@link PumpResult.Detail}, N36-56: "넣을 곳 없음(이유)").
 *     A ground pump and a valve pump say different things when they have no source.</li>
 * </ol>
 * The state is not saved: a new pump, a loaded one and a manual pump not clicked yet read "대기 중"
 * until their first cycle (N36-67). The wording is settled (N36-48, N36-71).
 */
public final class PumpStatusText {

    // Locale keys ([ui] section).
    /** The output line: {@code <direction>} and {@code <form>} are replaced. */
    public static final String OUTPUT = "mechanicspumpoutput";
    public static final String FORM_VALVE = "mechanicspumpformvalve";
    public static final String FORM_GROUND = "mechanicspumpformground";
    /** Per {@link Direction} ordinal. */
    private static final String[] DIRECTION_KEYS = {
            "mechanicspumpdirnorth", "mechanicspumpdireast", "mechanicspumpdirsouth", "mechanicspumpdirwest"};

    /** No cycle yet (N36-67). */
    public static final String WAITING = "mechanicspumpwaiting";
    /** A cycle moved fluid: {@code <fluid>} is replaced by its name. */
    public static final String PUMPING = "mechanicspumppumping";
    /** Switched off by a wire signal (11-3, N11-3). */
    public static final String WIRE_OFF = "mechanicspumpwireoff";
    /** A ground pump without liquid under it (N36-15, N36-21). */
    public static final String NO_SOURCE_GROUND = "mechanicspumpnosourceground";
    /** A valve pump without a linked tank valve behind it (N36-16, N36-58). */
    public static final String NO_SOURCE_VALVE = "mechanicspumpnosourcevalve";
    /** The tank valve behind a valve pump is switched off by a wire signal (N27-4). */
    public static final String SOURCE_OFF = "mechanicspumpsourceoff";
    public static final String SOURCE_EMPTY = "mechanicspumpsourceempty";
    /** The source holds another fluid than the baseline: inactive (N20-3, N36-53). */
    public static final String SOURCE_OTHER_FLUID = "mechanicspumpsourceotherfluid";
    /** The pump's tier cannot move the fluid (N20-4, 12-1). */
    public static final String FLUID_NOT_ALLOWED = "mechanicspumpfluidnotallowed";
    /** Nowhere to push: no tank linked in front (N36-1, N36-4, N36-50). */
    public static final String NO_DESTINATION = "mechanicspumpnodestination";
    /** Nowhere to push: every destination is full (N7-4). */
    public static final String DESTINATION_FULL = "mechanicspumpdestinationfull";
    /** Nowhere to push: another fluid in front (N36-46, N36-56). */
    public static final String DESTINATION_OTHER_FLUID = "mechanicspumpdestinationotherfluid";
    /** Nowhere to push: the tank valve in front is switched off by a wire signal (N27-4, N36-56). */
    public static final String DESTINATION_OFF = "mechanicspumpdestinationoff";
    /** A log-fueled pump with no lit log and no log to light (11-7). */
    public static final String NO_FUEL = "mechanicspumpnofuel";

    private PumpStatusText() {
    }

    /** Every {@code [ui]} key of the pump texts (both locale files have them). */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        keys.add(OUTPUT);
        keys.add(FORM_VALVE);
        keys.add(FORM_GROUND);
        Collections.addAll(keys, DIRECTION_KEYS);
        Collections.addAll(keys, WAITING, PUMPING, WIRE_OFF, NO_SOURCE_GROUND, NO_SOURCE_VALVE, SOURCE_OFF,
                SOURCE_EMPTY, SOURCE_OTHER_FLUID, FLUID_NOT_ALLOWED, NO_DESTINATION, DESTINATION_FULL,
                DESTINATION_OTHER_FLUID, DESTINATION_OFF, NO_FUEL);
        return keys;
    }

    /** The key of a direction's name. */
    public static String directionKey(Direction direction) {
        return DIRECTION_KEYS[direction.ordinal()];
    }

    /** The key of a form's name. */
    public static String formKey(PumpForm form) {
        return form == PumpForm.VALVE ? FORM_VALVE : FORM_GROUND;
    }

    /**
     * The key of the state line. Every combination has one: a detail that does not belong to the
     * status (the engine never pairs them) is read as the status's plainest case, no source for
     * {@link PumpResult.Status#NO_SOURCE} and no tank in front for
     * {@link PumpResult.Status#NO_DESTINATION}; the other statuses have no detail.
     */
    public static String key(PumpResult.Status status, PumpResult.Detail detail, PumpForm form) {
        switch (status) {
            case PUMPED:
                return PUMPING;
            case DISABLED:
                return WIRE_OFF;
            case NO_SOURCE:
                switch (detail) {
                    case SOURCE_OFF:
                        return SOURCE_OFF;
                    case SOURCE_EMPTY:
                        return SOURCE_EMPTY;
                    case SOURCE_OTHER_FLUID:
                        return SOURCE_OTHER_FLUID;
                    default:
                        // SOURCE_MISSING: the form says where the source is missing (N36-6).
                        return form == PumpForm.VALVE ? NO_SOURCE_VALVE : NO_SOURCE_GROUND;
                }
            case FLUID_NOT_ALLOWED:
                return FLUID_NOT_ALLOWED;
            case NO_DESTINATION:
                switch (detail) {
                    case DESTINATION_FULL:
                        return DESTINATION_FULL;
                    case DESTINATION_OTHER_FLUID:
                        return DESTINATION_OTHER_FLUID;
                    case DESTINATION_OFF:
                        return DESTINATION_OFF;
                    default:
                        // DESTINATION_MISSING.
                        return NO_DESTINATION;
                }
            case NO_FUEL:
                return NO_FUEL;
            case WAITING:
            default:
                return WAITING;
        }
    }

    /**
     * The output line: the output direction and the form (N36-44).
     *
     * @param words the localized template of a {@code [ui]} key
     */
    public static String outputLine(Direction direction, PumpForm form, Function<String, String> words) {
        return words.apply(OUTPUT)
                .replace("<direction>", words.apply(directionKey(direction)))
                .replace("<form>", words.apply(formKey(form)));
    }

    /**
     * The state line (N36-44, N36-56).
     *
     * @param fluidName the name of the fluid the last cycle moved, for {@link PumpResult.Status#PUMPED}
     * @param words     the localized template of a {@code [ui]} key
     */
    public static String stateLine(PumpResult.Status status, PumpResult.Detail detail, PumpForm form, String fluidName,
                                   Function<String, String> words) {
        return words.apply(key(status, detail, form)).replace("<fluid>", fluidName == null ? "" : fluidName);
    }

}
