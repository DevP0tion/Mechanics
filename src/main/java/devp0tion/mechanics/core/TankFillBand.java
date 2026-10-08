package devp0tion.mechanics.core;

/**
 * The fill level band on a recognized tank's north wall (N34-4): a band of the tank's fluid over
 * the inner side of the north wall's front face, rising with the fill percentage. Its height is
 * {@code round(fill * 32 px)}, the face's full 32 px; the glass ceiling's rim covers the top 16 px
 * of it, so the range includes the rim. Nothing is eased: the band follows the synced amount.
 */
public final class TankFillBand {

    /** The band's full height in px: the north wall's front face (N34-4). */
    public static final int MAX_HEIGHT = 32;

    private TankFillBand() {
    }

    /**
     * The band height in px for {@code amount / capacity}: {@code round(fill * MAX_HEIGHT)}, a
     * half pixel rounding up. 0 for a capacity of 0 or less, or an amount of 0 or less; an amount
     * above the capacity counts as full.
     */
    public static int height(int amount, int capacity) {
        if (capacity <= 0 || amount <= 0) {
            return 0;
        }
        long clamped = Math.min(amount, capacity);
        // floor(clamped * 32 / capacity + 1/2) in integers (no float rounding at large amounts).
        return (int) ((clamped * MAX_HEIGHT * 2 + capacity) / (2L * capacity));
    }

}
