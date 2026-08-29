package woo.siegePlugin.round;

import java.util.UUID;

public record QueuedPlayer(UUID playerId, RoundRole role, QueueSource source) {
}
