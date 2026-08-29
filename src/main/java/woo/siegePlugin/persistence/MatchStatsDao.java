package woo.siegePlugin.persistence;

import woo.siegePlugin.stats.PlayerMatchStats;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable five-second checkpoints for recoverable match-local statistics. */
public final class MatchStatsDao {

    private final SiegeDatabase database;

    public MatchStatsDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Void> saveSnapshot(String matchId, Collection<PlayerMatchStats> stats) {
        List<PlayerMatchStats> snapshot = List.copyOf(stats);
        return database.submitTransaction(connection -> {
            replaceSnapshot(connection, matchId, snapshot);
            return null;
        });
    }

    /**
     * Writes one match's statistics as a complete replacement.
     *
     * <p>Deleting first matters: a checkpoint is the authoritative picture of
     * the tracker, so a player row left over from an earlier binding of the
     * same match ID must not survive into the ceremony.</p>
     */
    static void replaceSnapshot(
            java.sql.Connection connection,
            String matchId,
            Collection<PlayerMatchStats> stats
    ) throws java.sql.SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM match_player_stats WHERE match_id = ?"
        )) {
            delete.setString(1, matchId);
            delete.executeUpdate();
        }
        if (stats.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO match_player_stats (
                    match_id, player_uuid, player_name, kills, damage, banner_seconds, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            long now = System.currentTimeMillis();
            for (PlayerMatchStats value : stats) {
                insert.setString(1, matchId);
                insert.setString(2, value.playerId().toString());
                insert.setString(3, value.playerName());
                insert.setLong(4, value.kills());
                insert.setDouble(5, value.damage());
                insert.setLong(6, value.bannerSeconds());
                insert.setLong(7, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public CompletableFuture<List<PlayerMatchStats>> load(String matchId) {
        return database.submit(connection -> {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT player_uuid, player_name, kills, damage, banner_seconds
                    FROM match_player_stats WHERE match_id = ?
                    """)) {
                query.setString(1, matchId);
                try (ResultSet result = query.executeQuery()) {
                    java.util.ArrayList<PlayerMatchStats> values = new java.util.ArrayList<>();
                    while (result.next()) {
                        values.add(new PlayerMatchStats(
                                UUID.fromString(result.getString("player_uuid")),
                                result.getString("player_name"),
                                result.getLong("kills"),
                                result.getDouble("damage"),
                                result.getLong("banner_seconds")
                        ));
                    }
                    return List.copyOf(values);
                }
            }
        });
    }
}
