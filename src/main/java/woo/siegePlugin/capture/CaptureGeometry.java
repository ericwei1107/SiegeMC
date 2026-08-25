package woo.siegePlugin.capture;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Mirrors SiegeWar's {@code SiegeWarDistanceUtil.areLocationsClose}: horizontal
 * distance and vertical difference are each bounded by the radius independently,
 * so the zone is a cylinder of height 2r rather than a sphere.
 */
public final class CaptureGeometry {

    private CaptureGeometry() {
    }

    public static boolean isWithinCaptureZone(Location player, Location banner, int radiusBlocks) {
        World playerWorld = player.getWorld();
        World bannerWorld = banner.getWorld();
        if (playerWorld == null || bannerWorld == null) {
            return false;
        }
        if (!playerWorld.getName().equalsIgnoreCase(bannerWorld.getName())) {
            return false;
        }

        return isWithinCaptureZone(
                player.getX(), player.getY(), player.getZ(),
                banner.getX(), banner.getY(), banner.getZ(),
                radiusBlocks
        );
    }

    static boolean isWithinCaptureZone(
            double playerX, double playerY, double playerZ,
            double bannerX, double bannerY, double bannerZ,
            int radiusBlocks
    ) {
        double deltaX = playerX - bannerX;
        double deltaZ = playerZ - bannerZ;
        if (Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) > radiusBlocks) {
            return false;
        }

        return Math.abs(playerY - bannerY) <= radiusBlocks;
    }
}
