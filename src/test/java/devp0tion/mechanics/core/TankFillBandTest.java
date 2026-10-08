package devp0tion.mechanics.core;

/** {@link TankFillBand}: the fill level band's height on the north wall (N34-4). */
final class TankFillBandTest {

    private TankFillBandTest() {
    }

    public static void testEmptyAndFull() {
        Check.equal(0, TankFillBand.height(0, 100), "empty: no band");
        Check.equal(32, TankFillBand.height(100, 100), "full: the whole 32 px face, rim included");
        Check.equal(TankFillBand.MAX_HEIGHT, TankFillBand.height(47000, 47000));
    }

    public static void testHalf() {
        Check.equal(16, TankFillBand.height(50, 100));
        Check.equal(8, TankFillBand.height(25, 100));
    }

    public static void testRoundsHalfUp() {
        // 1/64 * 32 = 0.5 -> 1; 3/64 * 32 = 1.5 -> 2; 1/65 * 32 = 0.49 -> 0
        Check.equal(1, TankFillBand.height(1, 64));
        Check.equal(2, TankFillBand.height(3, 64));
        Check.equal(0, TankFillBand.height(1, 65));
        // 31.5 px rounds to the full face
        Check.equal(32, TankFillBand.height(63, 64));
    }

    public static void testCapacityZeroGuard() {
        Check.equal(0, TankFillBand.height(10, 0), "capacity 0: no band, no division");
        Check.equal(0, TankFillBand.height(10, -5));
    }

    public static void testOutOfRangeAmounts() {
        Check.equal(0, TankFillBand.height(-3, 100), "a negative amount is empty");
        Check.equal(32, TankFillBand.height(150, 100), "above the capacity counts as full");
    }

    public static void testNoOverflowAtLargeValues() {
        Check.equal(32, TankFillBand.height(Integer.MAX_VALUE, Integer.MAX_VALUE));
        Check.equal(16, TankFillBand.height(Integer.MAX_VALUE / 2 + 1, Integer.MAX_VALUE));
    }

}
