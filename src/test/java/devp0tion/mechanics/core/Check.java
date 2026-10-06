package devp0tion.mechanics.core;

import java.util.Objects;

/** Assertion helpers for {@link CoreTestRunner}. */
final class Check {

    private Check() {
    }

    static void equal(Object expected, Object actual) {
        equal(expected, actual, "");
    }

    static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError((message.isEmpty() ? "" : message + ": ")
                    + "expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void isFalse(boolean condition, String message) {
        isTrue(!condition, message);
    }

    static void isNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + ": expected null but was <" + value + ">");
        }
    }

    static void throwsException(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

}
