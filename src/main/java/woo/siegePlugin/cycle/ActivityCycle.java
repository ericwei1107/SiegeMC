package woo.siegePlugin.cycle;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Pure state machine for the ephemeral ACTIVE/BREAK cycle. */
public final class ActivityCycle implements SiegePhaseStatus {

    private final ActivityCycleSettings settings;
    private SiegePhase phase = SiegePhase.ACTIVE;
    private Instant deadline;

    public ActivityCycle(ActivityCycleSettings settings, Instant startedAt) {
        this.settings = Objects.requireNonNull(settings, "settings");
        if (settings.enabled()) {
            deadline = Objects.requireNonNull(startedAt, "startedAt").plus(settings.activeDuration());
        }
    }

    @Override
    public SiegePhase currentPhase() {
        return phase;
    }

    public Optional<Duration> timeRemaining(Instant now) {
        if (deadline == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(Objects.requireNonNull(now, "now"), deadline);
        return Optional.of(remaining.isNegative() ? Duration.ZERO : remaining);
    }

    /** Advances one elapsed cycle boundary, if any. */
    public Optional<Transition> advance(Instant now) {
        if (deadline == null || now.isBefore(deadline)) {
            return Optional.empty();
        }
        return Optional.of(transitionTo(phase == SiegePhase.ACTIVE ? SiegePhase.BREAK : SiegePhase.ACTIVE, now));
    }

    public Optional<Transition> forceBreak(Instant now, Duration duration) {
        if (!settings.enabled()) {
            return Optional.empty();
        }
        requirePositive(duration, "duration");
        SiegePhase previous = phase;
        phase = SiegePhase.BREAK;
        deadline = Objects.requireNonNull(now, "now").plus(duration);
        return Optional.of(new Transition(previous, phase, deadline));
    }

    public Optional<Transition> resume(Instant now) {
        if (!settings.enabled() || phase != SiegePhase.BREAK) {
            return Optional.empty();
        }
        return Optional.of(transitionTo(SiegePhase.ACTIVE, now));
    }

    private Transition transitionTo(SiegePhase next, Instant now) {
        SiegePhase previous = phase;
        phase = next;
        deadline = Objects.requireNonNull(now, "now").plus(
                next == SiegePhase.ACTIVE ? settings.activeDuration() : settings.breakDuration()
        );
        return new Transition(previous, next, deadline);
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public record Transition(SiegePhase previous, SiegePhase current, Instant deadline) {
    }
}
