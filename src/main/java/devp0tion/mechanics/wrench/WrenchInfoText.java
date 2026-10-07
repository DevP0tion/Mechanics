package devp0tion.mechanics.wrench;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.LinkFlags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The text of the engineering wrench's tooltip (N30-1, N30-2), shown while the wrench is held and
 * the cursor points at a tile. Game independent: the words come from a lookup of the {@code [ui]}
 * locale keys, so the text building is tested by the plain test runner.
 *
 * <p>Per pipe of the tile, the main target first ({@link WrenchTargets#pipeOrder}, N30-5):
 * <ol>
 *     <li>the pipe's name;</li>
 *     <li>its fluid's name only, no amount; an empty pipe shows "비어 있음" / "Empty" (N30-2);</li>
 *     <li>the directions it is linked toward: both facing link flags open toward a part it links
 *     to (9-4, 13-4, N16-4), the four sides and the vertical link to the other layer;</li>
 *     <li>the directions blocked because the neighbour holds another fluid (N12-2, N13-2), only
 *     when there is one.</li>
 * </ol>
 * Then the reason the right click there would be refused, if it would be (N30-1), as a warning.
 *
 * <p>TODO(design): the layout is provisional: the labels, the order of the lines, the vertical link
 * listed as a direction, "None" for no linked direction and the blocked line left out when nothing
 * is blocked. A side linked by the wrench but blocked by another fluid is listed in both lines.
 */
public final class WrenchInfoText {

    /** One pipe's part of the tooltip. */
    public static final class PipeSection {
        public final String name;
        /** Whether the fluid is known yet (it comes from the server). */
        public final boolean fluidKnown;
        /** The fluid's display name, or {@code null} for an empty pipe. */
        public final String fluidName;
        /** The linked faces ({@link LinkFlags} bits, the vertical one included). */
        public final int linkedFaces;
        /** The faces blocked by another fluid ({@link LinkFlags} bits, the vertical one included). */
        public final int blockedFaces;

        public PipeSection(String name, boolean fluidKnown, String fluidName, int linkedFaces, int blockedFaces) {
            this.name = name;
            this.fluidKnown = fluidKnown;
            this.fluidName = fluidName;
            this.linkedFaces = linkedFaces;
            this.blockedFaces = blockedFaces;
        }
    }

    /** One line of the tooltip. */
    public static final class Line {
        public final String text;
        /** A refusal reason (drawn in a warning colour). */
        public final boolean warning;

        public Line(String text, boolean warning) {
            this.text = text;
            this.warning = warning;
        }

        @Override
        public String toString() {
            return (warning ? "! " : "") + text;
        }
    }

    // Locale keys ([ui] section).
    public static final String FLUID = "mechanicswrenchfluid";
    public static final String EMPTY = "mechanicswrenchempty";
    public static final String LINKED = "mechanicswrenchlinked";
    public static final String BLOCKED = "mechanicswrenchblocked";
    public static final String NONE = "mechanicswrenchnone";
    public static final String VERTICAL = "mechanicswrenchdirvertical";
    static final String[] DIRECTION_KEYS = {
            "mechanicswrenchdirnorth", "mechanicswrenchdireast", "mechanicswrenchdirsouth", "mechanicswrenchdirwest"};

    /** Separator of the listed directions. */
    static final String SEPARATOR = ", ";

    private WrenchInfoText() {
    }

    /**
     * The faces a pipe is linked toward: both facing flags open toward a part it links to.
     *
     * @param ownLinks        the pipe's link flags
     * @param neighbourLinks  per {@link Direction} ordinal, the flags of the part next to it that it
     *                        links to (same layer pipe; for a basic pipe also a valve or pump), or
     *                        -1 when there is none
     * @param verticalPartner the flags of the part on the other layer of its tile it links to (the
     *                        underground pipe for a basic pipe; the basic pipe or valve for an
     *                        underground pipe, never a pump, 9-9), or -1 when there is none
     */
    public static int linkedFaces(int ownLinks, int[] neighbourLinks, int verticalPartner) {
        int faces = 0;
        for (Direction d : Direction.values()) {
            int neighbour = neighbourLinks[d.ordinal()];
            if (LinkFlags.isSideOpen(ownLinks, d) && neighbour >= 0 && LinkFlags.isSideOpen(neighbour, d.opposite())) {
                faces |= LinkFlags.bit(d);
            }
        }
        if (LinkFlags.isVerticalOpen(ownLinks) && verticalPartner >= 0 && LinkFlags.isVerticalOpen(verticalPartner)) {
            faces |= LinkFlags.VERTICAL;
        }
        return faces;
    }

    /**
     * The tooltip's lines.
     *
     * @param sections the tile's pipes, the main target first; may be empty (a pump or valve alone)
     * @param refusal  why the right click there would be refused, or {@code null}
     * @param words    the localized template of a {@code [ui]} key ({@code <fluid>} and
     *                 {@code <directions>} are replaced here)
     */
    public static List<Line> build(List<PipeSection> sections, WrenchRefusal refusal, Function<String, String> words) {
        if (sections.isEmpty() && refusal == null) {
            return Collections.emptyList();
        }
        List<Line> lines = new ArrayList<>();
        for (PipeSection section : sections) {
            lines.add(new Line(section.name, false));
            if (section.fluidKnown) {
                String fluid = section.fluidName != null ? section.fluidName : words.apply(EMPTY);
                lines.add(new Line(words.apply(FLUID).replace("<fluid>", fluid), false));
            }
            lines.add(new Line(words.apply(LINKED).replace("<directions>", directions(section.linkedFaces, words)), false));
            if (LinkFlags.sanitize(section.blockedFaces) != 0) {
                lines.add(new Line(words.apply(BLOCKED).replace("<directions>", directions(section.blockedFaces, words)), false));
            }
        }
        if (refusal != null) {
            lines.add(new Line(words.apply(refusal.localeKey), true));
        }
        return lines;
    }

    /** The faces as a list of direction names, north, east, south, west, then the vertical link; "None" when empty. */
    static String directions(int faces, Function<String, String> words) {
        StringBuilder text = new StringBuilder();
        for (Direction d : Direction.values()) {
            if (LinkFlags.isSideOpen(faces, d)) {
                append(text, words.apply(DIRECTION_KEYS[d.ordinal()]));
            }
        }
        if (LinkFlags.isVerticalOpen(faces)) {
            append(text, words.apply(VERTICAL));
        }
        return text.length() == 0 ? words.apply(NONE) : text.toString();
    }

    private static void append(StringBuilder text, String word) {
        if (text.length() > 0) {
            text.append(SEPARATOR);
        }
        text.append(word);
    }

}
