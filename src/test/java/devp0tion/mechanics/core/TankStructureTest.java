package devp0tion.mechanics.core;

import devp0tion.mechanics.core.TankValidation.InteriorCondition;
import devp0tion.mechanics.core.TankValidation.Reason;

/**
 * Tank recognition rules (3-1, 4-1, 4-3, 4-4, 4-5, 5-5, 5-7, 5-11, 5-13, 7-1, N15-4) and capacity
 * (8-1, N4-1, N4-2, N4-4, N13-5). See {@link Grid} for the map legend.
 */
final class TankStructureTest {

    private TankStructureTest() {
    }

    /** Validates the whole grid as one tank candidate. */
    private static TankValidation validateWhole(String... rows) {
        return TankStructure.validate(0, 0, rows[0].length(), rows.length, Grid.of(rows));
    }

    private static void expectValid(TankValidation result) {
        Check.isTrue(result.isValid(), "expected valid but was " + result);
    }

    // ---------- Size ----------

    public static void testMinimumThreeByThreeIsValid() {
        TankValidation result = validateWhole(
                "#C#",
                "#G#",
                "###");
        expectValid(result);
        Check.equal(1, result.getBounds().getInteriorCellCount());
        Check.equal(40, result.getCapacity(), "copper 1 cell = 40 (N4-4)");
    }

    public static void testSevenBySevenMaximumIsValid() {
        TankValidation result = validateWhole(
                "aaaCaaa",
                "a.....a",
                "a.....a",
                "a.....a",
                "a.....a",
                "a.....a",
                "aaaaaaa");
        expectValid(result);
        Check.equal(25, result.getBounds().getInteriorCellCount());
        Check.equal(MineralTier.SPIDERITE, result.getLowestTier());
        Check.equal(47000, result.getCapacity(), "spiderite 25 cells = 47000 (N4-4)");
    }

    public static void testEightWideIsTooLarge() {
        TankValidation result = validateWhole(
                "###C####",
                "#GGGGGG#",
                "########");
        Check.equal(Reason.TOO_LARGE, result.getReason());
        Check.equal(0, result.getCapacity());
    }

    public static void testEightTallIsTooLarge() {
        TankValidation result = validateWhole(
                "#C#",
                "#G#",
                "#G#",
                "#G#",
                "#G#",
                "#G#",
                "#G#",
                "###");
        Check.equal(Reason.TOO_LARGE, result.getReason());
    }

    public static void testLimitIsPerAxis() {
        // Interior 5x1: each axis is checked separately (4-2, 7-1).
        TankValidation result = validateWhole(
                "###C###",
                "#GGGGG#",
                "#######");
        expectValid(result);
        Check.equal(5 * 40, result.getCapacity());
    }

    public static void testNoInteriorIsTooSmall() {
        Check.equal(Reason.TOO_SMALL, TankStructure.validate(0, 0, 2, 3, Grid.of("#C", "##", "##")).getReason());
        Check.equal(Reason.TOO_SMALL, TankStructure.validate(0, 0, 3, 2, Grid.of("#C#", "###")).getReason());
        Check.equal(Reason.TOO_SMALL, TankStructure.validate(0, 0, 1, 1, Grid.of("C")).getReason());
        Check.equal(Reason.TOO_SMALL, TankStructure.validate(0, 0, 0, 0, Grid.of("C")).getReason());
    }

    // ---------- Controller ----------

    public static void testTwoControllersAreInvalid() {
        TankValidation result = validateWhole(
                "#C#",
                "#G#",
                "#C#");
        Check.equal(Reason.MULTIPLE_CONTROLLERS, result.getReason());
    }

    public static void testNoControllerIsInvalid() {
        TankValidation result = validateWhole(
                "###",
                "#G#",
                "###");
        Check.equal(Reason.NO_CONTROLLER, result.getReason());
    }

    public static void testControllerOnEachCornerIsValid() {
        String[][] maps = {
                {"C##", "#G#", "###"},
                {"##C", "#G#", "###"},
                {"###", "#G#", "C##"},
                {"###", "#G#", "##C"}
        };
        for (String[] map : maps) {
            TankValidation result = validateWhole(map);
            expectValid(result);
            Check.equal(map[result.getControllerY()].charAt(result.getControllerX()), 'C', "controller position");
        }
    }

    public static void testControllerPositionIsReported() {
        TankValidation result = TankStructure.validate(2, 1, 4, 3, Grid.of(
                "......",
                "..####",
                "..#GGV",
                "..##C#"));
        expectValid(result);
        Check.equal(4, result.getControllerX());
        Check.equal(3, result.getControllerY());
    }

    public static void testControllerInsideTheInteriorIsNotABorderController() {
        TankValidation result = validateWhole(
                "#####",
                "#GGG#",
                "#GCG#",
                "#GGG#",
                "#####");
        Check.equal(Reason.NO_CONTROLLER, result.getReason());
    }

    // ---------- Valves ----------

    public static void testValveOnEachCornerIsInvalid() {
        String[][] maps = {
                {"V#C", "#G#", "###"},
                {"#CV", "#G#", "###"},
                {"#C#", "#G#", "V##"},
                {"#C#", "#G#", "##V"}
        };
        for (String[] map : maps) {
            Check.equal(Reason.VALVE_ON_CORNER, validateWhole(map).getReason(), String.join("/", map));
        }
    }

    public static void testZeroValvesAreAllowed() {
        TankValidation result = validateWhole(
                "#C#",
                "#G#",
                "###");
        expectValid(result);
        Check.equal(0, result.getValveCount());
    }

    public static void testManyValvesAreAllowed() {
        TankValidation result = validateWhole(
                "#VCVV#",
                "V....V",
                "V....V",
                "#VVVV#");
        expectValid(result);
        Check.equal(11, result.getValveCount());
    }

    // ---------- Border cells ----------

    public static void testBorderMustBeWallControllerOrValve() {
        String[][] maps = {
                {"#C#", "#G#", "#X#"},
                {"#C#", "GG#", "###"},
                {"#C#", "#G.", "###"},
                {"#C#", "#Gn", "###"}
        };
        for (String[] map : maps) {
            Check.equal(Reason.INVALID_BORDER_CELL, validateWhole(map).getReason(), String.join("/", map));
        }
    }

    public static void testInvalidBorderIsReportedBeforeControllerCount() {
        TankValidation result = validateWhole(
                "#CC",
                "#G#",
                "#X#");
        Check.equal(Reason.INVALID_BORDER_CELL, result.getReason());
    }

    // ---------- Interior ----------

    public static void testAllGlassInterior() {
        TankValidation result = validateWhole(
                "##C##",
                "#GGG#",
                "#GGG#",
                "#####");
        expectValid(result);
        Check.equal(InteriorCondition.ALL_GLASS, result.getInteriorCondition());
    }

    public static void testAllEmptyInterior() {
        TankValidation result = validateWhole(
                "##C##",
                "#...#",
                "#...#",
                "#####");
        expectValid(result);
        Check.equal(InteriorCondition.ALL_EMPTY, result.getInteriorCondition());
    }

    public static void testMixedInteriorWithoutTankFloorIsInvalid() {
        TankValidation result = validateWhole(
                "##C##",
                "#G.G#",
                "#GGG#",
                "#####");
        Check.equal(Reason.MIXED_INTERIOR, result.getReason());
    }

    public static void testTankFloorAllowsGlassAndEmptyMixed() {
        TankValidation result = validateWhole(
                "##C##",
                "#g,g#",
                "#,,g#",
                "#####");
        expectValid(result);
        Check.equal(InteriorCondition.TANK_FLOOR, result.getInteriorCondition());
        Check.equal(6 * 40, result.getCapacity());
    }

    public static void testPartialTankFloorWithMixedInteriorIsInvalid() {
        TankValidation result = validateWhole(
                "##C##",
                "#g,g#",
                "#,.g#",
                "#####");
        Check.equal(Reason.MIXED_INTERIOR, result.getReason());
    }

    public static void testAllGlassIsValidWhateverTheFloor() {
        TankValidation result = validateWhole(
                "##C##",
                "#gGg#",
                "#####");
        expectValid(result);
        Check.equal(InteriorCondition.ALL_GLASS, result.getInteriorCondition());
    }

    public static void testOtherObjectInInteriorIsInvalid() {
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, validateWhole("##C##", "#GXG#", "#####").getReason());
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, validateWhole("##C##", "#...#", "#.X.#", "#####").getReason());
        // Only glass may stand on the tank floor (5-11).
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, validateWhole("##C##", "#g,x#", "#####").getReason());
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, validateWhole("##C##", "#G#G#", "#####").getReason());
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, validateWhole("##C##", "#GVG#", "#####").getReason());
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, validateWhole("##C##", "#GnG#", "#####").getReason());
    }

    // ---------- Interior layers and liquid floors (N15-4) ----------

    public static void testLiquidFloorIsNotEmpty() {
        TankValidation result = validateWhole(
                "##C##",
                "#.~.#",
                "#####");
        Check.equal(Reason.LIQUID_FLOOR, result.getReason());
    }

    public static void testGlassOnALiquidFloorIsInvalid() {
        Grid grid = Grid.of(
                "##C##",
                "#GGG#",
                "#####").set(2, 1, TankCell.of(CellKind.GLASS).withLiquidFloor(true));
        Check.equal(Reason.LIQUID_FLOOR, TankStructure.validate(0, 0, 5, 3, grid).getReason());
    }

    public static void testObjectOnAnotherLayerIsNotEmpty() {
        TankValidation result = validateWhole(
                "##C##",
                "#.*.#",
                "#####");
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, result.getReason(), "a carpet is not empty (N15-4)");
    }

    public static void testObjectOnAnotherLayerOverGlassIsInvalid() {
        Grid grid = Grid.of(
                "##C##",
                "#GGG#",
                "#####").set(1, 1, TankCell.of(CellKind.GLASS).withOtherLayerObject(true));
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, TankStructure.validate(0, 0, 5, 3, grid).getReason());
    }

    public static void testTankFloorNeedsTheOtherLayersEmpty() {
        Grid grid = Grid.of(
                "##C##",
                "#g,g#",
                "#####").set(2, 1, TankCell.of(CellKind.EMPTY).withTankFloor(true).withOtherLayerObject(true));
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, TankStructure.validate(0, 0, 5, 3, grid).getReason());
    }

    public static void testInvalidObjectIsReportedBeforeLiquidFloor() {
        TankValidation result = validateWhole(
                "##C##",
                "#~*.#",
                "#####");
        Check.equal(Reason.INVALID_INTERIOR_OBJECT, result.getReason());
    }

    public static void testBorderOnlyLooksAtTheBaseLayer() {
        // The interior rules (5-5, 5-11) look at every layer (N15-4); the border is the base layer.
        Grid grid = Grid.of(
                "##C##",
                "#...#",
                "#####")
                .set(0, 1, TankCell.mineralWall(MineralTier.COPPER).withOtherLayerObject(true))
                .set(4, 1, TankCell.mineralWall(MineralTier.COPPER).withLiquidFloor(true));
        expectValid(TankStructure.validate(0, 0, 5, 3, grid));
    }

    // ---------- Capacity ----------

    public static void testCapacityUsesLowestWallTier() {
        TankValidation result = validateWhole(
                "aaCaa",
                "a...a",
                "a...#",
                "aaaaa");
        expectValid(result);
        Check.equal(MineralTier.COPPER, result.getLowestTier());
        Check.equal(6 * 40 * 1, result.getCapacity());
    }

    public static void testCapacityLowestOfMixedTiers() {
        // iron (1), gold (2), tungsten (5) -> iron, multiplier 2
        TankValidation result = validateWhole(
                "55C55",
                "2GGG5",
                "2GGG1",
                "2GGG5",
                "55555");
        expectValid(result);
        Check.equal(MineralTier.IRON, result.getLowestTier());
        Check.equal(9 * 40 * 2, result.getCapacity());
    }

    public static void testControllerNeverLowersTheMultiplier() {
        // N13-5 ①: the controller counts as the highest tier.
        Check.equal(MineralTier.SPIDERITE, TankStructure.CONTROLLER_TIER);
        Check.equal(MineralTier.highest(), TankStructure.CONTROLLER_TIER);
        for (MineralTier tier : MineralTier.values()) {
            char wall = (char) (tier.ordinal() < 10 ? '0' + tier.ordinal() : 'a');
            String row = "" + wall + 'C' + wall;
            TankValidation result = validateWhole(row, "" + wall + 'G' + wall, "" + wall + wall + wall);
            expectValid(result);
            Check.equal(tier, result.getLowestTier(), tier.name());
            Check.equal(40 * tier.getCapacityMultiplier(), result.getCapacity(), tier.name());
        }
    }

    public static void testValveTierLowersTheMultiplier() {
        // N13-5 ②③: spiderite walls with a copper valve: the copper valve sets the multiplier.
        TankValidation result = validateWhole(
                "aaCaa",
                "a...V",
                "aaaaa");
        expectValid(result);
        Check.equal(MineralTier.COPPER, result.getLowestTier());
        Check.equal(3 * 40 * 1, result.getCapacity());
    }

    public static void testHigherValveTierDoesNotRaiseTheMultiplier() {
        Grid grid = Grid.of(
                "11C11",
                "1...1",
                "11111").set(4, 1, TankCell.valve(MineralTier.SPIDERITE));
        TankValidation result = TankStructure.validate(0, 0, 5, 3, grid);
        expectValid(result);
        Check.equal(MineralTier.IRON, result.getLowestTier());
        Check.equal(3 * 40 * 2, result.getCapacity());
    }

    public static void testOneMultiplierFromWallsAndValves() {
        // Tungsten walls, gold and glacial valves: gold (multiplier 3) for the whole tank (N13-5 ③).
        Grid grid = Grid.of(
                "55C55",
                "5GGG5",
                "55555")
                .set(0, 1, TankCell.valve(MineralTier.GOLD))
                .set(4, 1, TankCell.valve(MineralTier.GLACIAL));
        TankValidation result = TankStructure.validate(0, 0, 5, 3, grid);
        expectValid(result);
        Check.equal(MineralTier.GOLD, result.getLowestTier());
        Check.equal(3 * 40 * 3, result.getCapacity());
        Check.equal(2, result.getValveCount());
    }

    public static void testCapacityFormula() {
        Check.equal(40, TankStructure.capacity(1, MineralTier.COPPER));
        Check.equal(47000, TankStructure.capacity(25, MineralTier.SPIDERITE));
        Check.equal(4 * 40 * 30, TankStructure.capacity(4, MineralTier.ANCIENTFOSSIL));
        Check.equal(40, TankStructure.BASE_CAPACITY_PER_CELL);
        Check.equal(4 * FluidUnits.BUCKET, TankStructure.BASE_CAPACITY_PER_CELL, "four buckets per cell");
    }

    public static void testEveryTierCapacityForFullTank() {
        for (MineralTier tier : MineralTier.values()) {
            Check.equal(25 * 40 * tier.getCapacityMultiplier(), TankStructure.capacity(25, tier), tier.name());
        }
    }

    // ---------- TankCell ----------

    public static void testTankCellFactories() {
        Check.throwsException(IllegalArgumentException.class, () -> TankCell.of(CellKind.MINERAL_WALL));
        Check.throwsException(IllegalArgumentException.class, () -> TankCell.of(CellKind.VALVE));
        Check.throwsException(NullPointerException.class, () -> TankCell.mineralWall(null));
        Check.throwsException(NullPointerException.class, () -> TankCell.valve(null));
        TankCell valve = TankCell.valve(MineralTier.IVY);
        Check.equal(CellKind.VALVE, valve.getKind());
        Check.equal(MineralTier.IVY, valve.getMineral(), "a valve carries its tier (N13-5)");
        Check.isNull(valve.getKeptTank(), "and no owner or kept tank (N33-1)");
        TankBounds kept = new TankBounds(0, 0, 3, 3);
        Check.equal(kept, TankCell.controller(kept).getKeptTank());
        Check.isNull(TankCell.controller().getKeptTank(), "keeps nothing");
        Check.isNull(TankCell.controller().getMineral(), "the controller has no tier of its own");
        TankCell floor = TankCell.of(CellKind.EMPTY).withLiquidFloor(true).withOtherLayerObject(true);
        Check.isTrue(floor.isLiquidFloor() && floor.hasOtherLayerObject(), "interior flags");
        Check.equal(TankCell.of(CellKind.EMPTY), floor.withLiquidFloor(false).withOtherLayerObject(false));
        TankCell wall = TankCell.mineralWall(MineralTier.GOLD);
        Check.equal(CellKind.MINERAL_WALL, wall.getKind());
        Check.equal(MineralTier.GOLD, wall.getMineral());
        Check.isFalse(wall.isTankFloor(), "default floor");
        Check.isTrue(TankCell.of(CellKind.GLASS).withTankFloor(true).isTankFloor(), "tank floor");
        Check.isNull(TankCell.of(CellKind.GLASS).getMineral(), "glass has no mineral");
    }

}
