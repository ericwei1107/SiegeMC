package woo.siegePlugin.arena;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Objects;

/**
 * Starts a fresh, ephemeral arena-reset interval on every plugin enable.
 *
 * <p>The reset service remains the sole owner of its warning countdown and
 * restoration. This scheduler only requests that existing path at the
 * configured interval, so manual and automatic resets share the same overlap
 * protection.</p>
 */
public final class ArenaResetScheduler {

    private static final long TICKS_PER_SECOND = 20L;

    private final JavaPlugin plugin;
    private final ArenaResetService resetService;
    private final Duration interval;

    private BukkitTask intervalTask;

    public ArenaResetScheduler(JavaPlugin plugin, ArenaResetService resetService, Duration interval) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resetService = Objects.requireNonNull(resetService, "resetService");
        this.interval = Objects.requireNonNull(interval, "interval");
    }

    /** Begins the next interval only when one is not already armed. */
    public void start() {
        if (intervalTask != null) {
            return;
        }
        armNextInterval();
    }

    /** Cancels the pending interval; {@link ArenaResetService#stop()} cancels an active reset. */
    public void stop() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
    }

    private void armNextInterval() {
        intervalTask = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                this::beginAutomaticReset,
                toTicks(interval)
        );
        plugin.getLogger().info("Next automatic arena reset is scheduled in " + format(interval) + ".");
    }

    private void beginAutomaticReset() {
        intervalTask = null;
        resetService.scheduleReset(message -> plugin.getLogger().info("Automatic arena reset: " + message));
        // The next timer begins from this interval boundary. If a manual reset
        // or snapshot capture owns maintenance now, ArenaResetService declines
        // the request and this still leaves exactly one future automatic timer.
        armNextInterval();
    }

    static long toTicks(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        try {
            long wholeSecondTicks = Math.multiplyExact(duration.getSeconds(), TICKS_PER_SECOND);
            long partialSecondTicks = (duration.getNano() + 49_999_999L) / 50_000_000L;
            return Math.addExact(wholeSecondTicks, partialSecondTicks);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("interval is too large to schedule", exception);
        }
    }

    static String format(Duration duration) {
        long seconds = duration.getSeconds();
        if (duration.getNano() == 0 && seconds >= 3_600L && seconds % 3_600L == 0L) {
            long hours = seconds / 3_600L;
            return hours + " hour" + (hours == 1L ? "" : "s");
        }
        if (duration.getNano() == 0 && seconds >= 60L && seconds % 60L == 0L) {
            long minutes = seconds / 60L;
            return minutes + " minute" + (minutes == 1L ? "" : "s");
        }
        if (duration.getNano() == 0 && seconds >= 1L) {
            return seconds + " second" + (seconds == 1L ? "" : "s");
        }
        long milliseconds = duration.toMillis();
        return milliseconds + " millisecond" + (milliseconds == 1L ? "" : "s");
    }
}
