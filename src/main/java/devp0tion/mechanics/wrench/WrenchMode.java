package devp0tion.mechanics.wrench;

import java.util.Locale;

/**
 * The engineering wrench's mode (N30-5), stored on the wrench item, so it lasts while other items
 * are held, across saving and reconnecting.
 *
 * <ul>
 *     <li>{@link #BASIC}, the default: the basic pipe (base layer part) comes first.</li>
 *     <li>{@link #UNDERGROUND}: the underground pipe comes first for the side right click, the left
 *     click recovery and the tooltip's main target. The middle right click (vertical link) is the
 *     same in both modes.</li>
 * </ul>
 * Game independent (no engine classes), so it is tested by the plain test runner.
 */
public enum WrenchMode {

    BASIC,
    UNDERGROUND;

    /** The mode the switch key changes to (N30-5): the other one. */
    public WrenchMode next() {
        return this == BASIC ? UNDERGROUND : BASIC;
    }

    /** The name stored in the item data: the lower-case constant name. */
    public String saveName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode of a stored name; {@link #BASIC} for none or an unknown one (N30-5: the default). */
    public static WrenchMode fromSaveName(String name) {
        if (name != null) {
            for (WrenchMode mode : values()) {
                if (mode.saveName().equals(name)) {
                    return mode;
                }
            }
        }
        return BASIC;
    }

    /** The mode of a packet's byte (the ordinal); {@link #BASIC} for an unknown one. */
    public static WrenchMode fromOrdinal(int ordinal) {
        WrenchMode[] modes = values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : BASIC;
    }

}
