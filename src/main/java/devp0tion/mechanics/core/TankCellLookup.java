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

}
