package woo.siegePlugin.minecart;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Removes abandoned carts from the battlefield.
 *
 * <p>A cart is swept when it is riderless and has not moved between two
 * consecutive sweeps, so the configured interval is also the grace period.
 * There is deliberately no cap on how many carts may exist.</p>
 */
public final class MinecartSweeper {

    private final JavaPlugin plugin;
    private final String worldName;
    private final Duration interval;
    private final Map<UUID, Location> lastSeenPositions = new HashMap<>();

    private BukkitTask task;

    public MinecartSweeper(JavaPlugin plugin, String worldName, Duration interval) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.interval = interval;
    }

    public void start() {
        long periodTicks = interval.toSeconds() * 20L;
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastSeenPositions.clear();
    }

    private void sweep() {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return;
        }

        Set<UUID> present = new HashSet<>();
        int removed = 0;
        for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
            UUID cartId = cart.getUniqueId();
            present.add(cartId);

            if (!cart.getPassengers().isEmpty()) {
                lastSeenPositions.remove(cartId);
                continue;
            }

            Location current = cart.getLocation();
            Location previous = lastSeenPositions.get(cartId);
            if (previous != null && isSameBlock(previous, current)) {
                cart.remove();
                lastSeenPositions.remove(cartId);
                removed++;
            } else {
                lastSeenPositions.put(cartId, current);
            }
        }

        // Forget carts that despawned or were destroyed between sweeps.
        lastSeenPositions.keySet().retainAll(present);

        if (removed > 0) {
            plugin.getLogger().info("Swept " + removed + " abandoned minecart(s) from " + worldName + ".");
        }
    }

    static boolean isSameBlock(Location first, Location second) {
        return first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
