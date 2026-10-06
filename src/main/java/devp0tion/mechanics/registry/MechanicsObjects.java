package devp0tion.mechanics.registry;

import devp0tion.mechanics.core.MineralTier;
import devp0tion.mechanics.objects.EngineeringWorkbenchObject;
import devp0tion.mechanics.objects.MineralWallObject;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Object registration.
 */
public final class MechanicsObjects {

    /** Engineering workbench (공학 작업대) stringID; also its item's stringID. */
    public static final String ENGINEERING_WORKBENCH = "engineeringworkbench";

    /** Object IDs of the mineral walls (5-12, 8-2), in tier order. */
    public static final Map<MineralTier, Integer> MINERAL_WALL_IDS = new EnumMap<>(MineralTier.class);

    private MechanicsObjects() {
    }

    public static void load() {
        EngineeringWorkbenchObject.register(ENGINEERING_WORKBENCH);
        for (MineralTier tier : MineralTier.values()) {
            MINERAL_WALL_IDS.put(tier, MineralWallObject.register(tier, mapColor(tier)));
        }
    }

    /**
     * Minimap color of each mineral wall: the base shade of its texture palette
     * (tools/textures/draw_mineral_walls.py). Art choice, not a design value.
     */
    private static Color mapColor(MineralTier tier) {
        switch (tier) {
            case COPPER:
                return new Color(196, 98, 52);
            case IRON:
                return new Color(126, 132, 142);
            case GOLD:
                return new Color(214, 164, 44);
            case DEMONIC:
                return new Color(128, 44, 128);
            case IVY:
                return new Color(72, 140, 52);
            case TUNGSTEN:
                return new Color(76, 86, 104);
            case GLACIAL:
                return new Color(112, 182, 226);
            case MYCELIUM:
                return new Color(150, 98, 64);
            case ANCIENTFOSSIL:
                return new Color(178, 150, 104);
            case NIGHTSTEEL:
                return new Color(70, 56, 130);
            case SPIDERITE:
                return new Color(132, 196, 44);
            default:
                throw new IllegalArgumentException("No map color for " + tier);
        }
    }

}
