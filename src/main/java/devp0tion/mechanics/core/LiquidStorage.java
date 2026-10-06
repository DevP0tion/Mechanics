package devp0tion.mechanics.core;

/**
 * The top-level fluid holder that pumps, tanks and pipes all extend (N7-1).
 *
 * <ul>
 *     <li>Holds one fluid type at a time (12-7). Empty storage has no type, so any fluid may enter
 *     it; once the last unit leaves, the type is cleared again.</li>
 *     <li>Never holds more than its capacity through {@link #insert}. A capacity lowered below the
 *     stored amount keeps the fluid (nothing is destroyed); the storage then takes nothing until it
 *     is back under capacity.</li>
 * </ul>
 * Amounts are in fluid units ({@link FluidUnits}).
 */
public class LiquidStorage implements FluidSource {

    private FluidType fluid;
    private int amount;
    private int capacity;

    public LiquidStorage(int capacity) {
        this.capacity = requireNonNegative(capacity, "capacity");
    }

    /** The stored fluid, or {@code null} when empty. */
    public final FluidType getFluid() {
        return fluid;
    }

    public final int getAmount() {
        return amount;
    }

    public final int getCapacity() {
        return capacity;
    }

    public final boolean isEmpty() {
        return amount == 0;
    }

    /** True when the amount has reached (or exceeds) the capacity. */
    public final boolean isFull() {
        return amount >= capacity;
    }

    /** Whether the one-fluid rule allows {@code type}: nothing stored, or the same fluid (12-7). */
    public final boolean canHold(FluidType type) {
        return type != null && (fluid == null || fluid == type);
    }

    /** How much of {@code type} {@link #insert} would take now. */
    public int getSpaceFor(FluidType type) {
        if (!acceptsInput() || !canHold(type)) {
            return 0;
        }
        return Math.max(0, capacity - amount);
    }

    /**
     * Adds up to {@code maxAmount} of {@code type}, respecting the fluid type and the capacity.
     *
     * @return the amount added
     */
    public final int insert(FluidType type, int maxAmount) {
        requireNonNegative(maxAmount, "maxAmount");
        int inserted = Math.min(maxAmount, getSpaceFor(type));
        if (inserted > 0) {
            fluid = type;
            amount += inserted;
            onContentsChanged();
        }
        return inserted;
    }

    @Override
    public FluidType getSourceFluid() {
        return providesOutput() ? fluid : null;
    }

    @Override
    public int getAvailable(FluidType type) {
        if (!providesOutput() || type == null || type != fluid) {
            return 0;
        }
        return amount;
    }

    @Override
    public final int extract(FluidType type, int maxAmount) {
        requireNonNegative(maxAmount, "maxAmount");
        int extracted = Math.min(maxAmount, getAvailable(type));
        if (extracted > 0) {
            amount -= extracted;
            if (amount == 0) {
                fluid = null;
            }
            onContentsChanged();
        }
        return extracted;
    }

    /**
     * Replaces the contents, for loading saved data. The amount may exceed the capacity (it is
     * kept, see the class comment).
     */
    public final void setContents(FluidType fluid, int amount) {
        requireNonNegative(amount, "amount");
        if ((fluid == null) != (amount == 0)) {
            throw new IllegalArgumentException("A fluid needs a positive amount and vice versa: " + fluid + " " + amount);
        }
        this.fluid = fluid;
        this.amount = amount;
        onContentsChanged();
    }

    /** Changes the capacity. The stored fluid is kept even if it no longer fits. */
    protected final void setCapacity(int capacity) {
        this.capacity = requireNonNegative(capacity, "capacity");
    }

    /** Whether this storage takes fluid at all right now (for example, an inactive tank does not). */
    protected boolean acceptsInput() {
        return true;
    }

    /** Whether fluid can be taken out right now. */
    protected boolean providesOutput() {
        return true;
    }

    /** Called after the fluid or the amount changed. */
    protected void onContentsChanged() {
    }

    static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
        return value;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + (fluid == null ? "empty" : fluid + " " + amount)
                + "/" + capacity + "]";
    }

}
