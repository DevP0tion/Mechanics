package devp0tion.mechanics.core;

/** {@link TileCover}: which neighbours block the whole tile, for the glass wall's joins (N34-5). */
final class TileCoverTest {

    private TileCoverTest() {
    }

    public static void testFullTile() {
        Check.isTrue(TileCover.coversTile(new int[]{0, 0, 32, 32}), "walls, rocks, glass: (0, 0, 32, 32)");
    }

    public static void testLargerThanTile() {
        Check.isTrue(TileCover.coversTile(new int[]{-8, -8, 48, 48}), "parts outside the tile are ignored");
    }

    public static void testFurnitureIsNotFull() {
        Check.isFalse(TileCover.coversTile(new int[]{4, 4, 24, 24}), "a table (4, 4, 24, 24)");
        Check.isFalse(TileCover.coversTile(new int[]{0, 0, 32, 4}), "a door strip");
        Check.isFalse(TileCover.coversTile(new int[]{0, 2, 32, 30}), "missing the top 2 px");
    }

    public static void testUnionOfParts() {
        Check.isTrue(TileCover.coversTile(new int[]{0, 0, 16, 32, 16, 0, 16, 32}), "two halves");
        Check.isTrue(TileCover.coversTile(new int[]{0, 0, 32, 20, 0, 10, 32, 22}), "overlapping parts");
        Check.isFalse(TileCover.coversTile(new int[]{0, 0, 16, 32, 17, 0, 15, 32}), "a 1 px gap");
        Check.isFalse(TileCover.coversTile(new int[]{0, 0, 32, 16, 0, 16, 16, 16}), "a missing corner");
    }

    public static void testNoCollision() {
        Check.isFalse(TileCover.coversTile(new int[0]), "no rectangles");
        Check.isFalse(TileCover.coversTile(null), "null");
        Check.isFalse(TileCover.coversTile(new int[]{0, 0, 0, 0}), "an empty rectangle");
        Check.isFalse(TileCover.coversTile(new int[]{0, 0, -32, 32}), "a negative width");
        Check.isFalse(TileCover.coversTile(new int[]{0, 0, 32}), "not whole rectangles");
    }

}
