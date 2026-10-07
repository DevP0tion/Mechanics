package devp0tion.mechanics.core;

/** {@link TankStatusText}: the controller window and hover tooltip text (5-4, 6-2, 6-9). */
final class TankStatusTextTest {

    private TankStatusTextTest() {
    }

    public static void testFluidNameThenCurrentSlashMax() {
        Check.equal("해수 40/80", TankStatusText.format("해수", 40, 80, "비어 있음"), "name, space, current/max (6-9)");
        Check.equal("담수 47000/47000", TankStatusText.format("담수", 47000, 47000, "비어 있음"));
    }

    public static void testNoBrackets() {
        String text = TankStatusText.format("담수", 1, 2, "비어 있음");
        Check.isFalse(text.contains("[") || text.contains("]") || text.contains("(") || text.contains(")"),
                "no brackets (6-2): " + text);
    }

    public static void testEmptyTankShowsOnlyEmpty() {
        Check.equal("비어 있음", TankStatusText.format(null, 0, 120, "비어 있음"), "no amounts (N31-7)");
        Check.equal("Empty", TankStatusText.format(null, 0, 120, "Empty"));
    }

}
