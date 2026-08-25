package woo.siegePlugin.capture;

import woo.siegePlugin.team.Team;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record CaptureSession(UUID playerId, Team side, Instant startedAt, Instant endsAt) {

    public static CaptureSession starting(UUID playerId, Team side, Instant now, Duration duration) {
        return new CaptureSession(playerId, side, now, now.plus(duration));
    }

    public boolean isComplete(Instant now) {
        return !now.isBefore(endsAt);
    }

    public Duration remaining(Instant now) {
        Duration remaining = Duration.between(now, endsAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** Fraction of the session already elapsed, clamped to {@code [0, 1]}. */
    public float progress(Instant now) {
        long totalMillis = Duration.between(startedAt, endsAt).toMillis();
        if (totalMillis <= 0L) {
            return 1.0f;
        }

        long elapsedMillis = Duration.between(startedAt, now).toMillis();
        return (float) Math.clamp(elapsedMillis / (double) totalMillis, 0.0d, 1.0d);
    }
}
