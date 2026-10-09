package devp0tion.mechanics.core;

/**
 * A pump's form (N36-6): where it pulls from. The player chooses it when placing the pump (N36-11);
 * it stays until the pump is picked up (N36-7). A pump never uses both sources (N36-6), and the
 * source of the other form is not linked, like a wall (N36-20).
 */
public enum PumpForm {

    /** Pulls from the tank valve behind it, on the side opposite its output (N36-3). */
    VALVE("valve"),
    /** Pulls from the liquid tile under it (11-1 ①). */
    GROUND("ground");

    private final String saveName;

    PumpForm(String saveName) {
        this.saveName = saveName;
    }

    /** The form's name in item data (N36-11); the pump's object entity saves the enum itself. */
    public String saveName() {
        return saveName;
    }

    /** The form of a saved name: without one (or an unknown one), the ground form (N36-32, N36-35). */
    public static PumpForm fromSaveName(String name) {
        for (PumpForm form : values()) {
            if (form.saveName.equals(name)) {
                return form;
            }
        }
        return GROUND;
    }

}
