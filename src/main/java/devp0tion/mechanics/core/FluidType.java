package devp0tion.mechanics.core;

/**
 * The fluids the mod handles: every vanilla liquid tile (12-1), with water split into
 * seawater and freshwater (12-2, 12-5), and the mod's own crude oil (N17-5).
 *
 * <p>Seawater and freshwater are the same vanilla tile ({@code watertile}); the game tells them
 * apart by position, not by tile type (domain rule S10, {@code LiquidManager.isSaltWater}).
 *
 * <p>Crude oil has no vanilla liquid tile: a pump standing on deep seawater (depth below
 * {@link #DEEP_SEA_HEIGHT}, 11-9) gets crude oil and only crude oil (N17-5). Freshwater and the
 * other liquids are unaffected by depth.
 *
 * <p>Each fluid has a fixed temperature (12-4).
 * TODO(design): temperature values per fluid are undecided (numbers.md table 4); no value exists
 * in code, and the pipe tier conditions that would use them are placeholders ({@link PipeTierRules}).
 *
 * <p>Gases are out of scope (12-3, TODO).
 */
public enum FluidType {

    SEAWATER("watertile", Salinity.SALT, "해수"),
    FRESHWATER("watertile", Salinity.FRESH, "담수"),
    // The fluids below are named after their vanilla liquid tiles, as the game localizes them (N32-4).
    LAVA("lavatile", Salinity.NOT_WATER, null),
    SLIME("liquidslimetile", Salinity.NOT_WATER, null),
    OOZE("liquidoozetile", Salinity.NOT_WATER, null),
    SPIRIT_WATER("spiritwatertile", Salinity.NOT_WATER, null),
    QUICKSAND("quicksandtile", Salinity.NOT_WATER, null),
    /**
     * Crude oil (원유, N17-5): mod-only, no vanilla liquid tile. Only pumps from the advanced fire
     * pump up move it (12-1, 12-6, {@link PumpTier#canPump}). Named 원유 / Crude Oil (N32-4).
     * TODO(design): its temperature is undecided (numbers.md table 4).
     */
    CRUDE_OIL(null, Salinity.NOT_WATER, "원유");

    /**
     * The deep sea starts below this liquid height (vanilla {@code LiquidManager.getHeight}: below 0
     * is depth, -10 deepest). Buckets cannot scoop below it (vanilla {@code BucketItem}: "deepsea"
     * when the height is below -3), and pumps there get crude oil from seawater (11-9, N17-5).
     */
    public static final int DEEP_SEA_HEIGHT = -3;

    /** Which water position a fluid comes from. Only meaningful for {@code watertile}. */
    public enum Salinity {
        SALT,
        FRESH,
        NOT_WATER
    }

    private final String liquidTileStringID;
    private final Salinity salinity;
    private final String koreanName;

    FluidType(String liquidTileStringID, Salinity salinity, String koreanName) {
        this.liquidTileStringID = liquidTileStringID;
        this.salinity = salinity;
        this.koreanName = koreanName;
    }

    /**
     * The vanilla liquid tile this fluid comes from (domain rule D7), or {@code null} for crude oil,
     * which has none (N17-5).
     */
    public String getLiquidTileStringID() {
        return liquidTileStringID;
    }

    /** Whether a vanilla liquid tile exists for this fluid (every fluid but crude oil). */
    public boolean hasLiquidTile() {
        return liquidTileStringID != null;
    }

    public Salinity getSalinity() {
        return salinity;
    }

    /** True for seawater and freshwater (the manual pump's "water", 12-5). */
    public boolean isWater() {
        return salinity != Salinity.NOT_WATER;
    }

    /**
     * The fixed Korean display name: 해수 and 담수 (12-5), 원유 (N32-4); {@code null} for the fluids
     * named after their vanilla liquid tile, whose names follow the game's localization (N32-4).
     */
    public String getKoreanName() {
        return koreanName;
    }

    /** Whether a liquid height counts as the deep sea (below {@link #DEEP_SEA_HEIGHT}). */
    public static boolean isDeepSeaHeight(int liquidHeight) {
        return liquidHeight < DEEP_SEA_HEIGHT;
    }

    /**
     * Maps a vanilla liquid tile to a fluid, ignoring depth.
     *
     * @param liquidTileStringID the tile's stringID
     * @param saltWater          whether the position is salt water (only used for {@code watertile})
     * @return the fluid, or {@code null} if the tile is not one of the liquid tiles
     */
    public static FluidType fromLiquidTile(String liquidTileStringID, boolean saltWater) {
        if ("watertile".equals(liquidTileStringID)) {
            return saltWater ? SEAWATER : FRESHWATER;
        }
        for (FluidType type : values()) {
            if (!type.isWater() && type.hasLiquidTile() && type.liquidTileStringID.equals(liquidTileStringID)) {
                return type;
            }
        }
        return null;
    }

    /**
     * The fluid a pump gets from a liquid tile (11-1 ①): as {@link #fromLiquidTile(String, boolean)},
     * except that deep seawater (height below {@link #DEEP_SEA_HEIGHT}) gives only crude oil (N17-5).
     * Deep freshwater and the other liquids are unchanged.
     *
     * @param liquidHeight the tile's liquid height ({@code LiquidManager.getHeight})
     */
    public static FluidType fromPumpedTile(String liquidTileStringID, boolean saltWater, int liquidHeight) {
        FluidType fluid = fromLiquidTile(liquidTileStringID, saltWater);
        if (fluid == SEAWATER && isDeepSeaHeight(liquidHeight)) {
            return CRUDE_OIL;
        }
        return fluid;
    }

}
