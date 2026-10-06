package devp0tion.mechanics.core;

/** {@link TankStatusText}: the controller window and hover tooltip text (5-4, 6-2, 6-9). */
final class TankStatusTextTest {

    private TankStatusTextTest() {
    }

    public static void testFluidNameThenCurrentSlashMax() {
        Check.equal("해수 40/80", TankStatusText.format("해수", 40, 80), "name, space, current/max (6-9)");
        Check.equal("담수 47000/47000", TankStatusText.format("담수", 47000, 47000));
    }

    public static void testNoBrackets() {
        String text = TankStatusText.format("담수", 1, 2);
        Check.isFalse(text.contains("[") || text.contains("]") || text.contains("(") || text.contains(")"),
                "no brackets (6-2): " + text);
    }

    public static void testEmptyTankShowsOnlyTheAmounts() {
        Check.equal("0/120", TankStatusText.format(null, 0, 120));
    }

}
