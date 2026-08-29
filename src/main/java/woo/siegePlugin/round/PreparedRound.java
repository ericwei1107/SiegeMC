package woo.siegePlugin.round;

import woo.siegePlugin.map.ActiveMapWorld;

import java.util.Objects;

public record PreparedRound(long generation, String matchId, ActiveMapWorld activeWorld) {
    public PreparedRound {
        activeWorld = Objects.requireNonNull(activeWorld, "activeWorld");
    }
}
