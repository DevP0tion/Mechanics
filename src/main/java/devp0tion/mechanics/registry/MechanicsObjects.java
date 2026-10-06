package devp0tion.mechanics.registry;

import devp0tion.mechanics.objects.EngineeringWorkbenchObject;

/**
 * Object registration.
 */
public final class MechanicsObjects {

    /** Engineering workbench (공학 작업대) stringID; also its item's stringID. */
    public static final String ENGINEERING_WORKBENCH = "engineeringworkbench";

    private MechanicsObjects() {
    }

    public static void load() {
        EngineeringWorkbenchObject.register(ENGINEERING_WORKBENCH);
    }

}
