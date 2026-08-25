package woo.siegePlugin.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * The inclusive block box that resets restore. Corners are normalized on
 * construction, so the two admin positions can be set in any order.
 */
public record ArenaRegion(
        String worldName,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
) {

    /** Vanilla structure blocks cap at 48; 16 keeps each tile chunk-aligned. */
    public static final int TILE_SIZE = 16;

    public ArenaRegion {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Arena region corners must be normalized");
        }
    }

    public static ArenaRegion between(
            String worldName,
            int firstX, int firstY, int firstZ,
            int secondX, int secondY, int secondZ
    ) {
        return new ArenaRegion(
                worldName,
                Math.min(firstX, secondX), Math.min(firstY, secondY), Math.min(firstZ, secondZ),
                Math.max(firstX, secondX), Math.max(firstY, secondY), Math.max(firstZ, secondZ)
        );
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long blockCount() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /**
     * Splits the region into {@value #TILE_SIZE}-cubed tiles. Tiles at the far
     * edges are clipped so the grid covers the region exactly, never more.
     */
    public List<ArenaTile> tiles() {
        List<ArenaTile> tiles = new ArrayList<>();
        for (int x = minX; x <= maxX; x += TILE_SIZE) {
            for (int y = minY; y <= maxY; y += TILE_SIZE) {
                for (int z = minZ; z <= maxZ; z += TILE_SIZE) {
                    tiles.add(new ArenaTile(
                            x, y, z,
                            Math.min(TILE_SIZE, maxX - x + 1),
                            Math.min(TILE_SIZE, maxY - y + 1),
                            Math.min(TILE_SIZE, maxZ - z + 1)
                    ));
                }
            }
        }
        return tiles;
    }

    public int tileCount() {
        return tilesAlong(sizeX()) * tilesAlong(sizeY()) * tilesAlong(sizeZ());
    }

    private static int tilesAlong(int extent) {
        return (extent + TILE_SIZE - 1) / TILE_SIZE;
    }
}
