package woo.siegePlugin.round;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Paper-backed scheduler port; one repeating task drives the whole lifecycle tick. */
public final class BukkitRoundScheduler implements RoundScheduler {

    private static final long ONE_SECOND_IN_TICKS = 20L;

    private final JavaPlugin plugin;
    private final Clock clock;
    private BukkitTask task;

    public BukkitRoundScheduler(JavaPlugin plugin) {
        this(plugin, Clock.systemUTC());
    }

    public BukkitRoundScheduler(JavaPlugin plugin, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void onServerThread(Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    @Override
    public void startTicking(Runnable tick) {
        stopTicking();
        if (!plugin.isEnabled()) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, tick, ONE_SECOND_IN_TICKS, ONE_SECOND_IN_TICKS
        );
    }

    @Override
    public void stopTicking() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
