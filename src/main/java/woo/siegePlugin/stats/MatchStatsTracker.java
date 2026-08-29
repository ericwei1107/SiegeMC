package woo.siegePlugin.stats;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Main-thread match-local accumulator; persistence receives immutable snapshots.
 *
 * <p>Every accumulator is bound to one explicit match ID. Recording against a
 * different match is dropped rather than silently attributed, which is what
 * stops a late damage or banner tick from the previous round leaking into the
 * new one's MVP ceremony.</p>
 */
public final class MatchStatsTracker {

    private final Map<UUID, MutableStats> values = new LinkedHashMap<>();
    private String matchId;

    /** Rebinds to a new match, discarding everything the previous one accumulated. */
    public void bind(String matchId) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        values.clear();
    }

    /** Detaches from any match; every later record is dropped until rebound. */
    public void unbind() {
        this.matchId = null;
        values.clear();
    }

    public String matchId() {
        return matchId;
    }

    public boolean isBoundTo(String candidate) {
        return matchId != null && matchId.equals(candidate);
    }

    public void recordKill(String matchId, UUID playerId, String playerName) {
        stats(matchId, playerId, playerName).ifPresent(current -> current.kills++);
    }

    public void rollbackKill(String matchId, UUID playerId) {
        if (!isBoundTo(matchId)) {
            return;
        }
        MutableStats current = values.get(playerId);
        if (current != null && current.kills > 0L) {
            current.kills--;
        }
    }

    public void recordDamage(String matchId, UUID playerId, String playerName, double damage) {
        if (damage > 0D) {
            stats(matchId, playerId, playerName).ifPresent(current -> current.damage += damage);
        }
    }

    public void recordBannerSecond(String matchId, UUID playerId, String playerName) {
        stats(matchId, playerId, playerName).ifPresent(current -> current.bannerSeconds++);
    }

    public Collection<PlayerMatchStats> snapshot() {
        return values.entrySet().stream().map(entry -> entry.getValue().snapshot(entry.getKey())).toList();
    }

    /** Restores the last durable checkpoint before a recovered match becomes scoreable. */
    public void restore(String matchId, Collection<PlayerMatchStats> checkpoint) {
        bind(matchId);
        for (PlayerMatchStats value : checkpoint) {
            MutableStats restored = new MutableStats();
            restored.playerName = value.playerName();
            restored.kills = value.kills();
            restored.damage = value.damage();
            restored.bannerSeconds = value.bannerSeconds();
            values.put(value.playerId(), restored);
        }
    }

    private java.util.Optional<MutableStats> stats(String matchId, UUID playerId, String playerName) {
        if (!isBoundTo(matchId)) {
            return java.util.Optional.empty();
        }
        MutableStats current = values.computeIfAbsent(playerId, ignored -> new MutableStats());
        current.playerName = playerName;
        return java.util.Optional.of(current);
    }

    private static final class MutableStats {
        private String playerName;
        private long kills;
        private double damage;
        private long bannerSeconds;

        private PlayerMatchStats snapshot(UUID playerId) {
            return new PlayerMatchStats(playerId, playerName, kills, damage, bannerSeconds);
        }
    }
}
