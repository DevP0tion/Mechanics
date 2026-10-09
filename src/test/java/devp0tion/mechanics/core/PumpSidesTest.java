package devp0tion.mechanics.core;

/**
 * {@link Pump#accepts(Direction, PumpForm, Direction, PipeGrid.Part)}: the sides a pump links on, as
 * clients draw them from its rotation and form (N36-37), the same answer as the grid's pump.
 */
final class PumpSidesTest {

    private PumpSidesTest() {
    }

    /** The sides (bits by direction ordinal) on which a pump with that front and form accepts {@code part}. */
    private static int sides(Direction front, PumpForm form, PipeGrid.Part part) {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (Pump.accepts(front, form, side, part)) {
                mask |= 1 << side.ordinal();
            }
        }
        return mask;
    }

    private static int bit(Direction d) {
        return 1 << d.ordinal();
    }

    public static void testABasicPipeLinksOnlyInFront() {
        // N36-1, N36-2: the front only, whatever the form (so the collision needs no form).
        for (Direction front : Direction.values()) {
            for (PumpForm form : PumpForm.values()) {
                Check.equal(bit(front), sides(front, form, PipeGrid.Part.BASIC_PIPE), front + " " + form);
            }
        }
    }

    public static void testAValveLinksInFrontAndBehindAValvePump() {
        for (Direction front : Direction.values()) {
            // N36-4 (front), N36-3 (back of a valve pump), N36-5 (never the sides).
            Check.equal(bit(front) | bit(front.opposite()), sides(front, PumpForm.VALVE, PipeGrid.Part.VALVE),
                    front + " valve pump");
            // N36-20: a ground pump's back is a wall for valves.
            Check.equal(bit(front), sides(front, PumpForm.GROUND, PipeGrid.Part.VALVE), front + " ground pump");
        }
    }

    public static void testPumpsAndUndergroundPipesNeverLink() {
        for (Direction front : Direction.values()) {
            for (PumpForm form : PumpForm.values()) {
                Check.equal(0, sides(front, form, PipeGrid.Part.PUMP), front + " " + form + " pump");
                Check.equal(0, sides(front, form, PipeGrid.Part.UNDERGROUND_PIPE), front + " " + form + " underground");
            }
        }
    }

    public static void testTheGridsPumpGivesTheSameAnswer() {
        // The drawing (static, from the rotation and the form) and the grid's pump (instance) agree.
        for (Direction front : Direction.values()) {
            for (PumpForm form : PumpForm.values()) {
                Pump pump = new Pump(PumpTier.MANUAL);
                pump.setDirection(front);
                pump.setForm(form);
                for (Direction side : Direction.values()) {
                    for (PipeGrid.Part part : PipeGrid.Part.values()) {
                        Check.equal(Pump.accepts(front, form, side, part), pump.accepts(side, part),
                                front + " " + form + " " + side + " " + part);
                    }
                }
            }
        }
    }

}
