package woo.siegePlugin.capture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Builds the real vanilla beacon immediately below the managed capture banner.
 * The banner remains at the capture block, so the beam visually originates at
 * it without trying to place two blocks in the same location.
 */
final class CaptureBeacon {

    void ensurePresent(Location bannerLocation) {
        World world = bannerLocation.getWorld();
        if (world == null || !isSupported(bannerLocation.getBlockY(), world.getMinHeight())) {
            return;
        }
        int beaconY = bannerLocation.getBlockY() - 1;
        int baseY = bannerLocation.getBlockY() - 2;
        int centerX = bannerLocation.getBlockX();
        int centerZ = bannerLocation.getBlockZ();

        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                Block base = world.getBlockAt(x, baseY, z);
                if (base.getType() != Material.IRON_BLOCK) {
                    base.setType(Material.IRON_BLOCK);
                }
            }
        }
        Block beacon = world.getBlockAt(centerX, beaconY, centerZ);
        if (beacon.getType() != Material.BEACON) {
            beacon.setType(Material.BEACON);
        }
    }

    static boolean isSupported(int bannerY, int worldMinHeight) {
        return bannerY - 2 >= worldMinHeight;
    }
}
