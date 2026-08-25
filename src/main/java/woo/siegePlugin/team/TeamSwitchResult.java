package woo.siegePlugin.team;

import java.time.Duration;

public record TeamSwitchResult(Status status, Duration cooldownRemaining, boolean teleported) {

    public enum Status {
        SWITCHED,
        NO_CURRENT_TEAM,
        ALREADY_ON_TEAM,
        COOLDOWN_ACTIVE,
        COMBAT_TAGGED,
        CAPTURE_SESSION_ACTIVE,
        WOULD_UNBALANCE_TEAMS
    }

    public static TeamSwitchResult of(Status status) {
        return new TeamSwitchResult(status, Duration.ZERO, false);
    }

    public static TeamSwitchResult cooldown(Duration remaining) {
        return new TeamSwitchResult(Status.COOLDOWN_ACTIVE, remaining, false);
    }

    public static TeamSwitchResult switched(boolean teleported) {
        return new TeamSwitchResult(Status.SWITCHED, Duration.ZERO, teleported);
    }
}
