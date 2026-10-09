package devp0tion.mechanics.core;

import java.util.Arrays;

/** {@link PumpSprites}: the sheet cell of a pump's rotation and form (N36-38, N36-39, N36-40, N36-41). */
final class PumpSpritesTest {

    private PumpSpritesTest() {
    }

    private static String section(int rotation, PumpForm form) {
        return Arrays.toString(PumpSprites.section(rotation, form));
    }

    public static void testColumnIsTheRotationRowIsTheForm() {
        // Columns: rotation 0 north, 1 east, 2 south, 3 west; row 0 valve, row 1 ground; 32x64 cells.
        Check.equal("[0, 32, 0, 64]", section(0, PumpForm.VALVE), "north valve");
        Check.equal("[32, 64, 0, 64]", section(1, PumpForm.VALVE), "east valve");
        Check.equal("[64, 96, 0, 64]", section(2, PumpForm.VALVE), "south valve");
        Check.equal("[96, 128, 0, 64]", section(3, PumpForm.VALVE), "west valve");
        Check.equal("[0, 32, 64, 128]", section(0, PumpForm.GROUND), "north ground");
        Check.equal("[32, 64, 64, 128]", section(1, PumpForm.GROUND), "east ground");
        Check.equal("[64, 96, 64, 128]", section(2, PumpForm.GROUND), "south ground");
        Check.equal("[96, 128, 64, 128]", section(3, PumpForm.GROUND), "west ground");
    }

    public static void testEveryCellIsInsideTheSheetAndDistinct() {
        boolean[] seen = new boolean[8];
        for (int rotation = 0; rotation < 4; rotation++) {
            for (PumpForm form : PumpForm.values()) {
                int[] s = PumpSprites.section(rotation, form);
                Check.equal(PumpSprites.CELL_WIDTH, s[1] - s[0], "width");
                Check.equal(PumpSprites.CELL_HEIGHT, s[3] - s[2], "height");
                Check.isTrue(s[0] >= 0 && s[1] <= 128 && s[2] >= 0 && s[3] <= 128, "inside the 128x128 sheet");
                int index = PumpSprites.row(form) * 4 + PumpSprites.column(rotation);
                Check.isFalse(seen[index], "one cell per rotation and form");
                seen[index] = true;
            }
        }
    }

    public static void testRotationMatchesTheDirectionOrder() {
        // The pump's output side is Direction.values()[rotation & 3] (N36-10): the column is its ordinal.
        for (Direction d : Direction.values()) {
            Check.equal(d.ordinal(), PumpSprites.column(d.ordinal()), d.name());
        }
        Check.equal(Direction.NORTH.ordinal(), 0);
        Check.equal(Direction.EAST.ordinal(), 1);
        Check.equal(Direction.SOUTH.ordinal(), 2);
        Check.equal(Direction.WEST.ordinal(), 3);
    }

    public static void testRotationByteIsMasked() {
        // The engine's rotation is a byte; only its low two bits are a direction.
        Check.equal(1, PumpSprites.column(5), "5");
        Check.equal(3, PumpSprites.column(-1), "-1 (byte 0xff)");
        Check.equal(section(2, PumpForm.VALVE), section(6, PumpForm.VALVE), "6");
    }

    public static void testWithoutAFormTheGroundRow() {
        // No form data is the ground form (N36-32): a pump without an entity draws the ground row.
        Check.equal(1, PumpSprites.row(null), "null form");
        Check.equal(section(0, PumpForm.GROUND), section(0, null), "null form section");
    }

}
