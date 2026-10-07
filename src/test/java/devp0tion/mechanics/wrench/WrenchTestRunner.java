package devp0tion.mechanics.wrench;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Test runner of the engineering wrench's game-independent logic (N30), without external
 * dependencies, like the core tests' runner (run by the Gradle task {@code wrenchTest}). Runs every
 * {@code public static void test*()} method of the listed classes.
 */
public final class WrenchTestRunner {

    private static final Class<?>[] TEST_CLASSES = {
            WrenchModeTest.class,
            WrenchTargetsTest.class,
            WrenchRefusalTest.class,
            WrenchInfoTextTest.class,
            WrenchLocaleTest.class,
            devp0tion.mechanics.core.WrenchCheckTest.class
    };

    private WrenchTestRunner() {
    }

    public static void main(String[] args) {
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Class<?> testClass : TEST_CLASSES) {
            Method[] methods = testClass.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (!method.getName().startsWith("test") || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0) {
                    continue;
                }
                String name = testClass.getSimpleName() + "." + method.getName();
                try {
                    method.setAccessible(true);
                    method.invoke(null);
                    passed++;
                    System.out.println("PASS " + name);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    failures.add(name + ": " + cause);
                    System.out.println("FAIL " + name + ": " + cause);
                    if (!(cause instanceof AssertionError)) {
                        cause.printStackTrace(System.out);
                    }
                } catch (IllegalAccessException e) {
                    failures.add(name + ": " + e);
                    System.out.println("FAIL " + name + ": " + e);
                }
            }
        }
        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                System.out.println("  " + failure);
            }
            System.exit(1);
        }
    }

}
