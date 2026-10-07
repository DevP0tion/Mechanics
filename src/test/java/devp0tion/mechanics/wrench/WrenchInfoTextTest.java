package devp0tion.mechanics.wrench;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.LinkFlags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** {@link WrenchInfoText}: the wrench tooltip's text (N30-1, N30-2), with the Korean locale file's words. */
final class WrenchInfoTextTest {

    private static final LangFile KR = LangFile.load("kr");

    private WrenchInfoTextTest() {
    }

    private static List<String> texts(List<WrenchInfoText.Line> lines) {
        List<String> texts = new ArrayList<>();
        for (WrenchInfoText.Line line : lines) {
            texts.add(line.toString());
        }
        return texts;
    }

    private static List<String> build(List<WrenchInfoText.PipeSection> sections, WrenchRefusal refusal) {
        return texts(WrenchInfoText.build(sections, refusal, KR::ui));
    }

    public static void testAPipesNameFluidLinksAndBlockedDirections() {
        int linked = LinkFlags.bit(Direction.NORTH) | LinkFlags.bit(Direction.EAST) | LinkFlags.VERTICAL;
        WrenchInfoText.PipeSection pipe = new WrenchInfoText.PipeSection("구리 파이프", true, "해수", linked,
                LinkFlags.bit(Direction.SOUTH));
        Check.equal(Arrays.asList("구리 파이프", "유체: 해수", "연결된 방향: 북, 동, 위아래", "다른 유체로 막힌 방향: 남"),
                build(Collections.singletonList(pipe), null), "N30-2");
    }

    public static void testAnEmptyPipeShowsEmptyAndNoAmount() {
        WrenchInfoText.PipeSection pipe = new WrenchInfoText.PipeSection("철 파이프", true, null, 0, 0);
        List<String> lines = build(Collections.singletonList(pipe), null);
        Check.equal(Arrays.asList("철 파이프", "유체: 비어 있음", "연결된 방향: 없음"), lines,
                "\"비어 있음\" (N30-2); no blocked line when nothing is blocked");
        for (String line : lines) {
            Check.isFalse(line.matches(".*\\d.*"), "no amount (N30-2): " + line);
        }
    }

    public static void testTheFluidLineWaitsForTheServer() {
        WrenchInfoText.PipeSection pipe = new WrenchInfoText.PipeSection("철 파이프", false, null, LinkFlags.bit(Direction.WEST), 0);
        Check.equal(Arrays.asList("철 파이프", "연결된 방향: 서"), build(Collections.singletonList(pipe), null));
    }

    public static void testTheUndergroundPipeOfTheTileFollowsTheMainTarget() {
        WrenchInfoText.PipeSection main = new WrenchInfoText.PipeSection("철 지하 파이프", true, "담수", 0, 0);
        WrenchInfoText.PipeSection other = new WrenchInfoText.PipeSection("철 파이프", true, null, 0, 0);
        Check.equal(Arrays.asList("철 지하 파이프", "유체: 담수", "연결된 방향: 없음", "철 파이프", "유체: 비어 있음", "연결된 방향: 없음"),
                build(Arrays.asList(main, other), null));
    }

    public static void testTheRefusalReasonComesLastAsAWarning() {
        WrenchInfoText.PipeSection pipe = new WrenchInfoText.PipeSection("철 파이프", true, null, 0, 0);
        List<WrenchInfoText.Line> lines = WrenchInfoText.build(Collections.singletonList(pipe),
                WrenchRefusal.DIFFERENT_SOURCE_FLUID, KR::ui);
        WrenchInfoText.Line last = lines.get(lines.size() - 1);
        Check.isTrue(last.warning, "warning");
        Check.equal(KR.ui("mechanicswrenchsourcefluid"), last.text, "N16-3 reason (N30-1)");
        for (int i = 0; i < lines.size() - 1; i++) {
            Check.isFalse(lines.get(i).warning, "only the reason is a warning");
        }
    }

    public static void testAPumpOrValveAloneShowsOnlyAReason() {
        Check.equal(Collections.emptyList(), build(Collections.<WrenchInfoText.PipeSection>emptyList(), null),
                "nothing when the click goes through (N30-1)");
        Check.equal(Collections.singletonList("! " + KR.ui("mechanicswrenchnovertical")),
                build(Collections.<WrenchInfoText.PipeSection>emptyList(), WrenchRefusal.NO_VERTICAL_LINK));
    }

    public static void testLinkedNeedsBothFacingFlagsAndANeighbour() {
        int none = -1;
        int[] neighbours = {LinkFlags.ALL_OPEN, LinkFlags.ALL_OPEN, LinkFlags.ALL_OPEN, none};
        Check.equal(LinkFlags.bit(Direction.NORTH) | LinkFlags.bit(Direction.EAST) | LinkFlags.bit(Direction.SOUTH),
                WrenchInfoText.linkedFaces(LinkFlags.ALL_OPEN, neighbours, none), "no neighbour west, no vertical partner");
        int ownCutEast = LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.EAST, false);
        Check.equal(LinkFlags.bit(Direction.NORTH) | LinkFlags.bit(Direction.SOUTH),
                WrenchInfoText.linkedFaces(ownCutEast, neighbours, none), "own flag cut");
        int[] southCutTowardUs = {none, none, LinkFlags.withSide(LinkFlags.ALL_OPEN, Direction.NORTH, false), none};
        Check.equal(0, WrenchInfoText.linkedFaces(LinkFlags.ALL_OPEN, southCutTowardUs, none), "neighbour's flag toward it cut");
        int[] noNeighbours = {none, none, none, none};
        Check.equal(LinkFlags.VERTICAL, WrenchInfoText.linkedFaces(LinkFlags.ALL_OPEN, noNeighbours, LinkFlags.ALL_OPEN), "vertical");
        Check.equal(0, WrenchInfoText.linkedFaces(LinkFlags.ALL_OPEN, noNeighbours,
                LinkFlags.withVertical(LinkFlags.ALL_OPEN, false)), "partner's vertical flag cut (N16-4)");
    }

}
