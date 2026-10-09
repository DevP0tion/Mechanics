package devp0tion.mechanics.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal test runner without external dependencies (run by the Gradle task {@code coreTest}).
 * Runs every {@code public static void test*()} method of the listed classes.
 */
public final class CoreTestRunner {

    private static final Class<?>[] TEST_CLASSES = {
            FluidTest.class,
            MineralTierTest.class,
            PumpTierTest.class,
            TankStructureTest.class,
            TankSearchTest.class,
            TankOwnershipTest.class,
            TankSharedWallTest.class,
            TankJudgmentTest.class,
            TankFloorRecordTest.class,
            TankStatusTextTest.class,
            TankFillBandTest.class,
            GlassBlockSpritesTest.class,
            TileCoverTest.class,
            LiquidStorageTest.class,
            PipeLinkTest.class,
            PipeShapeTest.class,
            PipeNetworkTest.class,
            PumpPushTest.class,
            PumpSourceTest.class,
            PumpBaselineTest.class,
            PumpFrontTest.class,
            TileBucketsTest.class,
            NewEngineTest.class
    };

    private CoreTestRunner() {
    }

    public static void main(String[] args) {
        int[] passed = {0};
        List<String> failures = new ArrayList<>();
        run(TEST_CLASSES, "", passed, failures);
        System.out.println();
        System.out.println(passed[0] + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                System.out.println("  " + failure);
            }
            System.exit(1);
        }
    }

    private static void run(Class<?>[] classes, String prefix, int[] passed, List<String> failures) {
        for (Class<?> testClass : classes) {
            Method[] methods = testClass.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (!method.getName().startsWith("test") || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0) {
                    continue;
                }
                String name = prefix + testClass.getSimpleName() + "." + method.getName();
                try {
                    method.invoke(null);
                    passed[0]++;
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
    }

}
