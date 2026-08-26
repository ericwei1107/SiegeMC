package woo.siegePlugin.cycle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import woo.siegePlugin.display.SidebarService;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Owns the Bukkit scheduler and broadcasts around the pure activity cycle. */
public final class ActivityCycleService implements SiegePhaseStatus {

    private static final long TICK_PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final SidebarService sidebarService;
    private final ActivityCycleSettings settings;
    private final Clock clock;
    private final ActivityCycle cycle;

    private BukkitTask task;
    private PhaseChangeListener listener = (previous, current) -> {
    };

    public ActivityCycleService(JavaPlugin plugin, SidebarService sidebarService, ActivityCycleSettings settings) {
        this(plugin, sidebarService, settings, Clock.systemUTC());
    }

    ActivityCycleService(JavaPlugin plugin, SidebarService sidebarService, ActivityCycleSettings settings, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sidebarService = Objects.requireNonNull(sidebarService, "sidebarService");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cycle = new ActivityCycle(settings, clock.instant());
    }

    public void setPhaseChangeListener(PhaseChangeListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public void start() {
        publishTimeRemaining();
        if (!settings.enabled()) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public SiegePhase currentPhase() {
        return cycle.currentPhase();
    }

    public CycleCommandResult forceBreak(Duration duration) {
        Optional<ActivityCycle.Transition> transition = cycle.forceBreak(clock.instant(), duration);
        if (transition.isEmpty()) {
            return CycleCommandResult.DISABLED;
        }
        applyTransition(transition.orElseThrow(), true);
        return transition.orElseThrow().previous() == SiegePhase.BREAK
                ? CycleCommandResult.BREAK_EXTENDED
                : CycleCommandResult.BREAK_STARTED;
    }

    public CycleCommandResult resume() {
        Optional<ActivityCycle.Transition> transition = cycle.resume(clock.instant());
        if (transition.isEmpty()) {
            return settings.enabled() ? CycleCommandResult.ALREADY_ACTIVE : CycleCommandResult.DISABLED;
        }
        applyTransition(transition.orElseThrow(), true);
        return CycleCommandResult.RESUMED;
    }

    public Duration configuredBreakDuration() {
        return settings.breakDuration();
    }

    private void tick() {
        cycle.advance(clock.instant()).ifPresent(transition -> applyTransition(transition, true));
        publishTimeRemaining();
    }

    private void applyTransition(ActivityCycle.Transition transition, boolean announce) {
        if (transition.previous() != transition.current()) {
            listener.onPhaseChange(transition.previous(), transition.current());
            if (announce) {
                String message = transition.current() == SiegePhase.BREAK
                        ? "Siege banner control is on break for " + formatDuration(cycle.timeRemaining(clock.instant()).orElseThrow()) + "."
                        : "Siege banner control is active again.";
                plugin.getServer().broadcast(Component.text(message, NamedTextColor.GOLD));
            }
        }
        publishTimeRemaining();
    }

    private void publishTimeRemaining() {
        cycle.timeRemaining(clock.instant()).ifPresent(sidebarService::updateCycleTimeRemaining);
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        return seconds / 60L + "m " + seconds % 60L + "s";
    }

    public enum CycleCommandResult {
        BREAK_STARTED,
        BREAK_EXTENDED,
        RESUMED,
        ALREADY_ACTIVE,
        DISABLED
    }

    @FunctionalInterface
    public interface PhaseChangeListener {
        void onPhaseChange(SiegePhase previous, SiegePhase current);
    }
}
