package devp0tion.mechanics.core;

/** FluidUnits (N2-1) and FluidType (12-1, 12-2, 12-5, S10). */
final class FluidTest {

    private FluidTest() {
    }

    public static void testBucketIsTenUnits() {
        Check.equal(10, FluidUnits.BUCKET);
    }

    public static void testFluidListAndTiles() {
        Check.equal(7, FluidType.values().length, "fluid count");
        Check.equal("watertile", FluidType.SEAWATER.getLiquidTileStringID());
        Check.equal("watertile", FluidType.FRESHWATER.getLiquidTileStringID());
        Check.equal("lavatile", FluidType.LAVA.getLiquidTileStringID());
        Check.equal("liquidslimetile", FluidType.SLIME.getLiquidTileStringID());
        Check.equal("liquidoozetile", FluidType.OOZE.getLiquidTileStringID());
        Check.equal("spiritwatertile", FluidType.SPIRIT_WATER.getLiquidTileStringID());
        Check.equal("quicksandtile", FluidType.QUICKSAND.getLiquidTileStringID());
    }

    public static void testWaterSplitBySalinity() {
        Check.equal(FluidType.Salinity.SALT, FluidType.SEAWATER.getSalinity());
        Check.equal(FluidType.Salinity.FRESH, FluidType.FRESHWATER.getSalinity());
        for (FluidType type : FluidType.values()) {
            boolean water = type == FluidType.SEAWATER || type == FluidType.FRESHWATER;
            Check.equal(water, type.isWater(), type + " isWater");
        }
    }

    public static void testOnlySeawaterAndFreshwaterHaveDecidedNames() {
        Check.equal("해수", FluidType.SEAWATER.getKoreanName());
        Check.equal("담수", FluidType.FRESHWATER.getKoreanName());
        for (FluidType type : FluidType.values()) {
            if (!type.isWater()) {
                Check.isNull(type.getKoreanName(), type + " name is undecided");
            }
        }
    }

    public static void testFromLiquidTile() {
        Check.equal(FluidType.SEAWATER, FluidType.fromLiquidTile("watertile", true));
        Check.equal(FluidType.FRESHWATER, FluidType.fromLiquidTile("watertile", false));
        Check.equal(FluidType.LAVA, FluidType.fromLiquidTile("lavatile", true));
        Check.equal(FluidType.LAVA, FluidType.fromLiquidTile("lavatile", false));
        Check.equal(FluidType.SLIME, FluidType.fromLiquidTile("liquidslimetile", false));
        Check.equal(FluidType.OOZE, FluidType.fromLiquidTile("liquidoozetile", false));
        Check.equal(FluidType.SPIRIT_WATER, FluidType.fromLiquidTile("spiritwatertile", false));
        Check.equal(FluidType.QUICKSAND, FluidType.fromLiquidTile("quicksandtile", false));
        Check.isNull(FluidType.fromLiquidTile("grasstile", false), "non-liquid tile");
        Check.isNull(FluidType.fromLiquidTile(null, false), "null tile");
    }

}
