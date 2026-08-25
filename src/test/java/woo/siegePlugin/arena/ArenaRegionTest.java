package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaRegionTest {

    @Test
    void normalizesCornersGivenInAnyOrder() {
        ArenaRegion ascending = ArenaRegion.between("siegeworld", 0, 60, 0, 31, 75, 31);
        ArenaRegion descending = ArenaRegion.between("siegeworld", 31, 75, 31, 0, 60, 0);

        assertEquals(ascending, descending);
        assertEquals(0, descending.minX());
        assertEquals(31, descending.maxX());
    }

    @Test
    void normalizesMixedAxisOrdering() {
        ArenaRegion region = ArenaRegion.between("siegeworld", 31, 60, 0, 0, 75, 31);

        assertEquals(0, region.minX());
        assertEquals(31, region.maxX());
        assertEquals(60, region.minY());
        assertEquals(75, region.maxY());
    }

    @Test
    void sizesAreInclusiveOfBothCorners() {
        ArenaRegion region = ArenaRegion.between("siegeworld", 0, 0, 0, 15, 15, 15);

        assertEquals(16, region.sizeX());
        assertEquals(16, region.sizeY());
        assertEquals(16, region.sizeZ());
        assertEquals(4096L, region.blockCount());
    }

    @Test
    void anExactlyTiledRegionProducesFullTiles() {
        ArenaRegion region = ArenaRegion.between("siegeworld", 0, 0, 0, 31, 15, 31);
        List<ArenaTile> tiles = region.tiles();

        assertEquals(4, tiles.size());
        assertEquals(4, region.tileCount());
        assertTrue(tiles.stream().allMatch(tile -> tile.sizeX() == 16 && tile.sizeY() == 16 && tile.sizeZ() == 16));
    }

    @Test
    void edgeTilesAreClippedSoTheGridNeverExceedsTheRegion() {
        // 20 blocks across becomes one 16-wide tile plus one 4-wide tile.
        ArenaRegion region = ArenaRegion.between("siegeworld", 0, 0, 0, 19, 0, 0);
        List<ArenaTile> tiles = region.tiles();

        assertEquals(2, tiles.size());
        assertEquals(16, tiles.get(0).sizeX());
        assertEquals(4, tiles.get(1).sizeX());
        assertEquals(1, tiles.get(0).sizeY());
        assertEquals(16, tiles.get(1).originX());
    }

    @Test
    void tilesCoverExactlyTheRegionsBlocks() {
        ArenaRegion region = ArenaRegion.between("siegeworld", -5, 60, 7, 26, 80, 40);

        long covered = region.tiles().stream().mapToLong(ArenaTile::blockCount).sum();

        assertEquals(region.blockCount(), covered);
    }

    @Test
    void tileCountMatchesTheGeneratedTiles() {
        ArenaRegion region = ArenaRegion.between("siegeworld", -5, 60, 7, 26, 80, 40);

        assertEquals(region.tiles().size(), region.tileCount());
    }

    @Test
    void handlesNegativeCoordinatesWithoutLosingBlocks() {
        ArenaRegion region = ArenaRegion.between("siegeworld", -20, -64, -20, -1, -50, -1);

        assertEquals(20, region.sizeX());
        assertEquals(region.blockCount(), region.tiles().stream().mapToLong(ArenaTile::blockCount).sum());
    }

    @Test
    void aSingleBlockRegionIsOneTile() {
        ArenaRegion region = ArenaRegion.between("siegeworld", 5, 5, 5, 5, 5, 5);

        assertEquals(1, region.tiles().size());
        assertEquals(1L, region.blockCount());
    }

    @Test
    void tileFileNamesAreUniquePerOrigin() {
        ArenaRegion region = ArenaRegion.between("siegeworld", 0, 0, 0, 47, 47, 47);

        long distinctNames = region.tiles().stream().map(ArenaTile::fileName).distinct().count();

        assertEquals(region.tiles().size(), distinctNames);
    }

    @Test
    void rejectsUnnormalizedConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArenaRegion("siegeworld", 10, 0, 0, 0, 0, 0)
        );
    }

    @Test
    void rejectsZeroSizedTiles() {
        assertThrows(IllegalArgumentException.class, () -> new ArenaTile(0, 0, 0, 0, 16, 16));
    }
}
