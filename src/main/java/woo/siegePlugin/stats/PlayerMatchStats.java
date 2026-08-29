package woo.siegePlugin.stats;

import java.util.Objects;
import java.util.UUID;

/** One player's match-local contribution totals. */
public record PlayerMatchStats(UUID playerId, String playerName, long kills, double damage, long bannerSeconds) {
    public PlayerMatchStats {
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName");
        if (playerName.isBlank() || kills < 0L || damage < 0D || bannerSeconds < 0L) {
            throw new IllegalArgumentException("Invalid player match stats");
        }
    }
}
