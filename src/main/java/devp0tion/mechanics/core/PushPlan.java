package devp0tion.mechanics.core;

/** One push of a pump: its destinations and the logic that moves fluid to them (N7-1, N7-2). */
interface PushPlan {

    /** What a push of up to {@code amount} would use now (moved plus lost), without changing anything. */
    long simulate(int amount);

    /** Pushes {@code amount}. */
    PumpResult run(int amount);

}
