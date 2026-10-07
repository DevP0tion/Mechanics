package devp0tion.mechanics.core;

/**
 * Reads what is on a tile. The game adapter implements this over a level; tests use a grid.
 */
public interface TankCellLookup {

    /**
     * @return the cell at the tile; {@code null} is treated as {@link CellKind#OTHER} on a floor
     * that is not the tank floor
     */
    TankCell getCell(int tileX, int tileY);

    /**
     * Whether the tile is loaded (N20-7). The judgments that use loaded cells only
     * ({@link TankStructure#validateLoaded}, {@link TankJudgment}) leave out the tiles that are not;
     * every other check reads them through {@link #getCell} like any tile. Tiles that can never load
     * (outside the level) count as loaded: they are read as something else.
     */
    default boolean isLoaded(int tileX, int tileY) {
        return true;
    }

}
