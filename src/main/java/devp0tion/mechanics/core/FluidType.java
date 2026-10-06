package devp0tion.mechanics.core;

/**
 * The fluids the mod handles: every vanilla liquid tile (12-1), with water split into
 * seawater and freshwater (12-2, 12-5).
 *
 * <p>Seawater and freshwater are the same vanilla tile ({@code watertile}); the game tells them
 * apart by position, not by tile type (domain rule S10, {@code LiquidManager.isSaltWater}).
 *
 * <p>Each fluid has a fixed temperature (12-4).
 * TODO(design): temperature values per fluid are undecided (numbers.md table 4).
 *
 * <p>Gases are out of scope (12-3, TODO).
 */
public enum FluidType {

    SEAWATER("watertile", Salinity.SALT, "해수"),
    FRESHWATER("watertile", Salinity.FRESH, "담수"),
    // TODO(design): display names of the fluids below are undecided (numbers.md table 4).
    LAVA("lavatile", Salinity.NOT_WATER, null),
    SLIME("liquidslimetile", Salinity.NOT_WATER, null),
    OOZE("liquidoozetile", Salinity.NOT_WATER, null),
    SPIRIT_WATER("spiritwatertile", Salinity.NOT_WATER, null),
    QUICKSAND("quicksandtile", Salinity.NOT_WATER, null);

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

    /** The vanilla liquid tile this fluid comes from (domain rule D7). */
    public String getLiquidTileStringID() {
        return liquidTileStringID;
    }

    public Salinity getSalinity() {
        return salinity;
    }

    /** True for seawater and freshwater (the manual pump's "water", 12-5). */
    public boolean isWater() {
        return salinity != Salinity.NOT_WATER;
    }

    /**
     * The decided Korean display name, or {@code null} while it is undecided.
     * Only 해수 and 담수 are decided (12-5).
     */
    public String getKoreanName() {
        return koreanName;
    }

    /**
     * Maps a vanilla liquid tile to a fluid.
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
            if (!type.isWater() && type.liquidTileStringID.equals(liquidTileStringID)) {
                return type;
            }
        }
        return null;
    }

}
