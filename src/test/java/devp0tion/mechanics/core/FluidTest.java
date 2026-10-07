package devp0tion.mechanics.core;

/** FluidUnits (N2-1) and FluidType (12-1, 12-2, 12-5, S10, N17-5). */
final class FluidTest {

    private FluidTest() {
    }

    public static void testBucketIsTenUnits() {
        Check.equal(10, FluidUnits.BUCKET);
    }

    public static void testFluidListAndTiles() {
        Check.equal(8, FluidType.values().length, "fluid count: 6 liquid tiles, water split, crude oil (N17-5)");
        Check.equal("watertile", FluidType.SEAWATER.getLiquidTileStringID());
        Check.equal("watertile", FluidType.FRESHWATER.getLiquidTileStringID());
        Check.equal("lavatile", FluidType.LAVA.getLiquidTileStringID());
        Check.equal("liquidslimetile", FluidType.SLIME.getLiquidTileStringID());
        Check.equal("liquidoozetile", FluidType.OOZE.getLiquidTileStringID());
        Check.equal("spiritwatertile", FluidType.SPIRIT_WATER.getLiquidTileStringID());
        Check.equal("quicksandtile", FluidType.QUICKSAND.getLiquidTileStringID());
        Check.isNull(FluidType.CRUDE_OIL.getLiquidTileStringID(), "crude oil has no vanilla tile (N17-5)");
        Check.isFalse(FluidType.CRUDE_OIL.hasLiquidTile(), "crude oil has no vanilla tile");
        Check.isFalse(FluidType.CRUDE_OIL.isWater(), "crude oil is not water");
    }

    public static void testWaterSplitBySalinity() {
        Check.equal(FluidType.Salinity.SALT, FluidType.SEAWATER.getSalinity());
        Check.equal(FluidType.Salinity.FRESH, FluidType.FRESHWATER.getSalinity());
        for (FluidType type : FluidType.values()) {
            boolean water = type == FluidType.SEAWATER || type == FluidType.FRESHWATER;
            Check.equal(water, type.isWater(), type + " isWater");
        }
    }

    public static void testFixedNamesOnlyWhereNoVanillaTileNameIsFollowed() {
        Check.equal("해수", FluidType.SEAWATER.getKoreanName());
        Check.equal("담수", FluidType.FRESHWATER.getKoreanName());
        Check.equal("원유", FluidType.CRUDE_OIL.getKoreanName(), "N32-4");
        for (FluidType type : FluidType.values()) {
            if (!type.isWater() && type.hasLiquidTile()) {
                Check.isNull(type.getKoreanName(), type + " follows its vanilla tile's name (N32-4)");
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

    public static void testDeepSeaHeight() {
        Check.equal(-3, FluidType.DEEP_SEA_HEIGHT, "the bucket's deep sea limit (11-9)");
        Check.isTrue(FluidType.isDeepSeaHeight(-4), "below -3");
        Check.isFalse(FluidType.isDeepSeaHeight(-3), "-3 is not deep");
    }

}
