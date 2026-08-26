package woo.siegePlugin.minecart;

import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Removes abandoned carts from the battlefield.
 *
 * <p>A cart is removed only after it has remained riderless and stationary
 * for the configured threshold. There is deliberately no cap on how many
 * carts may exist.</p>
 */
public final class MinecartSweeper {

    private static final Duration SWEEP_INTERVAL = Duration.ofSeconds(30L);

    private final JavaPlugin plugin;
    private final String worldName;
    private final Duration stationaryCleanupThreshold;
    private final Clock clock;
    private final StationaryMinecartTracker stationaryCarts = new StationaryMinecartTracker();

    private BukkitTask task;

    public MinecartSweeper(JavaPlugin plugin, String worldName, Duration stationaryCleanupThreshold) {
        this(plugin, worldName, stationaryCleanupThreshold, Clock.systemUTC());
    }

    MinecartSweeper(JavaPlugin plugin, String worldName, Duration stationaryCleanupThreshold, Clock clock) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.stationaryCleanupThreshold = stationaryCleanupThreshold;
        this.clock = clock;
    }

    public void start() {
        long periodTicks = SWEEP_INTERVAL.toSeconds() * 20L;
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        stationaryCarts.clear();
    }

    private void sweep() {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return;
        }

        Set<UUID> present = new HashSet<>();
        Instant now = clock.instant();
        int removed = 0;
        for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
            UUID cartId = cart.getUniqueId();
            present.add(cartId);

            if (!cart.getPassengers().isEmpty()) {
                stationaryCarts.forget(cartId);
                continue;
            }

            Instant stationarySince = stationaryCarts.observe(cartId, cart.getLocation(), now);
            if (hasExceededStationaryThreshold(stationarySince, now, stationaryCleanupThreshold)) {
                cart.remove();
                stationaryCarts.forget(cartId);
                removed++;
                present.remove(cartId);
            }
        }

        // Forget carts that despawned, were destroyed, or entered unloaded
        // chunks between sweeps. A reloaded cart starts a fresh stationary age.
        stationaryCarts.retainAll(present);

        if (removed > 0) {
            plugin.getLogger().info("Swept " + removed + " abandoned minecart(s) from " + worldName + ".");
        }
    }

    static boolean hasExceededStationaryThreshold(Instant stationarySince, Instant now, Duration threshold) {
        Duration stationaryAge = Duration.between(stationarySince, now);
        return !stationaryAge.isNegative() && stationaryAge.compareTo(threshold) > 0;
    }
}
