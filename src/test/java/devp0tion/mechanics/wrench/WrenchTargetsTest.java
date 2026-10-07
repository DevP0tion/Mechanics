package devp0tion.mechanics.wrench;

import devp0tion.mechanics.core.Direction;
import devp0tion.mechanics.core.PipeGrid;
import devp0tion.mechanics.core.PipeLayer;

import java.util.Arrays;
import java.util.Collections;

/** {@link WrenchTargets}: the tile's middle (N30-4) and the part the wrench acts on by its mode (N30-5). */
final class WrenchTargetsTest {

    private WrenchTargetsTest() {
    }

    public static void testTheMiddleIsTheCentre16By16Pixels() {
        for (int tileX : new int[]{0, 5, -1, -7}) {
            int middle = 0;
            for (int px = 0; px < 32; px++) {
                for (int py = 0; py < 32; py++) {
                    boolean inMiddle = px >= 8 && px <= 23 && py >= 8 && py <= 23;
                    Direction side = WrenchTargets.sideOf(tileX * 32 + px, tileX * 32 + py);
                    Check.equal(inMiddle, side == null, "pixel " + px + "," + py + " of tile " + tileX);
                    if (side == null) {
                        middle++;
                    }
                }
            }
            Check.equal(256, middle, "16x16 pixels (N30-4)");
        }
        Check.equal(8, WrenchTargets.MIDDLE_HALF_SIZE);
    }

    public static void testOutsideTheMiddleTheNearerEdgeWins() {
        Check.equal(Direction.NORTH, WrenchTargets.sideOf(16, 0));
        Check.equal(Direction.NORTH, WrenchTargets.sideOf(16, 7), "just above the middle");
        Check.equal(Direction.SOUTH, WrenchTargets.sideOf(16, 31));
        Check.equal(Direction.SOUTH, WrenchTargets.sideOf(16, 24), "just below the middle");
        Check.equal(Direction.WEST, WrenchTargets.sideOf(0, 16));
        Check.equal(Direction.WEST, WrenchTargets.sideOf(7, 16));
        Check.equal(Direction.EAST, WrenchTargets.sideOf(31, 16));
        Check.equal(Direction.EAST, WrenchTargets.sideOf(24, 16));
        Check.equal(Direction.WEST, WrenchTargets.sideOf(-32, -16), "tile -1, west edge");
    }

    public static void testBasicModeActsOnTheBaseLayerPartFirst() {
        Check.equal(PipeGrid.Part.BASIC_PIPE, WrenchTargets.sidePart(PipeGrid.Part.BASIC_PIPE, true, WrenchMode.BASIC));
        Check.equal(PipeGrid.Part.PUMP, WrenchTargets.sidePart(PipeGrid.Part.PUMP, true, WrenchMode.BASIC));
        Check.equal(PipeGrid.Part.VALVE, WrenchTargets.sidePart(PipeGrid.Part.VALVE, false, WrenchMode.BASIC));
        Check.equal(PipeGrid.Part.UNDERGROUND_PIPE, WrenchTargets.sidePart(null, true, WrenchMode.BASIC),
                "else the underground pipe");
        Check.isNull(WrenchTargets.sidePart(null, false, WrenchMode.BASIC), "nothing");
    }

    public static void testUndergroundModeActsOnTheUndergroundPipeFirst() {
        Check.equal(PipeGrid.Part.UNDERGROUND_PIPE, WrenchTargets.sidePart(PipeGrid.Part.BASIC_PIPE, true, WrenchMode.UNDERGROUND),
                "N30-5");
        Check.equal(PipeGrid.Part.UNDERGROUND_PIPE, WrenchTargets.sidePart(PipeGrid.Part.VALVE, true, WrenchMode.UNDERGROUND));
        Check.equal(PipeGrid.Part.PUMP, WrenchTargets.sidePart(PipeGrid.Part.PUMP, false, WrenchMode.UNDERGROUND),
                "else the base layer part");
        Check.isNull(WrenchTargets.sidePart(null, false, WrenchMode.UNDERGROUND), "nothing");
    }

    public static void testLeftClickRecoversByMode() {
        Check.equal(PipeLayer.BASE, WrenchTargets.recoverLayer(true, true, WrenchMode.BASIC), "basic first");
        Check.equal(PipeLayer.UNDERGROUND, WrenchTargets.recoverLayer(false, true, WrenchMode.BASIC));
        Check.equal(PipeLayer.UNDERGROUND, WrenchTargets.recoverLayer(true, true, WrenchMode.UNDERGROUND),
                "underground first (N30-5)");
        Check.equal(PipeLayer.BASE, WrenchTargets.recoverLayer(true, false, WrenchMode.UNDERGROUND));
        Check.isNull(WrenchTargets.recoverLayer(false, false, WrenchMode.UNDERGROUND), "no pipe");
    }

    public static void testTheTooltipsMainTargetFollowsTheMode() {
        Check.equal(Arrays.asList(PipeLayer.BASE, PipeLayer.UNDERGROUND), WrenchTargets.pipeOrder(true, true, WrenchMode.BASIC),
                "the underground pipe in the same tile too (N30-2)");
        Check.equal(Arrays.asList(PipeLayer.UNDERGROUND, PipeLayer.BASE), WrenchTargets.pipeOrder(true, true, WrenchMode.UNDERGROUND),
                "main target the underground pipe (N30-5)");
        Check.equal(Collections.singletonList(PipeLayer.UNDERGROUND), WrenchTargets.pipeOrder(false, true, WrenchMode.BASIC));
        Check.equal(Collections.singletonList(PipeLayer.BASE), WrenchTargets.pipeOrder(true, false, WrenchMode.UNDERGROUND));
        Check.equal(Collections.emptyList(), WrenchTargets.pipeOrder(false, false, WrenchMode.BASIC));
    }

}
