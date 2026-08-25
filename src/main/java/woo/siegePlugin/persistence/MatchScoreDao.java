package woo.siegePlugin.persistence;

import woo.siegePlugin.team.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Persists match totals and the ledger that explains them. Every total change
 * and its ledger entry are written in one transaction.
 */
public final class MatchScoreDao {

    private final SiegeDatabase database;

    public MatchScoreDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Recovers an existing match or starts it at zero. */
    public CompletableFuture<MatchScores> loadOrCreate(String matchId) {
        Objects.requireNonNull(matchId, "matchId");
        return database.submitTransaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO matches (match_id, red_score, blue_score, created_at, updated_at)
                    VALUES (?, 0, 0, ?, ?)
                    ON CONFLICT(match_id) DO NOTHING
                    """)) {
                long now = System.currentTimeMillis();
                insert.setString(1, matchId);
                insert.setLong(2, now);
                insert.setLong(3, now);
                insert.executeUpdate();
            }
            return readScores(connection, matchId);
        });
    }

    /**
     * Adds points to one team and records why. Returns the totals as persisted,
     * so callers never have to guess at the post-write state.
     */
    public CompletableFuture<MatchScores> award(String matchId, Team team, long points, ScoreReason reason) {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(reason, "reason");
        return database.submitTransaction(connection -> {
            String column = team == Team.RED ? "red_score" : "blue_score";
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE matches SET " + column + " = " + column + " + ?, updated_at = ? WHERE match_id = ?"
            )) {
                update.setLong(1, points);
                update.setLong(2, System.currentTimeMillis());
                update.setString(3, matchId);
                if (update.executeUpdate() == 0) {
                    throw new SQLException("No match row to award points to: " + matchId);
                }
            }
            appendLedgerEntry(connection, matchId, team, points, reason);
            return readScores(connection, matchId);
        });
    }

    /**
     * Zeroes both totals, ledgering the reversal of each so the audit trail
     * still sums to the stored score.
     */
    public CompletableFuture<MatchScores> reset(String matchId) {
        Objects.requireNonNull(matchId, "matchId");
        return database.submitTransaction(connection -> {
            MatchScores before = readScores(connection, matchId);

            for (Team team : Team.values()) {
                long reversal = -before.scoreFor(team);
                if (reversal != 0L) {
                    appendLedgerEntry(connection, matchId, team, reversal, ScoreReason.ADMIN_RESET);
                }
            }

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE matches SET red_score = 0, blue_score = 0, updated_at = ? WHERE match_id = ?"
            )) {
                update.setLong(1, System.currentTimeMillis());
                update.setString(2, matchId);
                if (update.executeUpdate() == 0) {
                    throw new SQLException("No match row to reset: " + matchId);
                }
            }
            return readScores(connection, matchId);
        });
    }

    /** Number of ledger rows recorded for a match. */
    public CompletableFuture<Integer> countLedgerEntries(String matchId) {
        Objects.requireNonNull(matchId, "matchId");
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM score_ledger WHERE match_id = ?"
            )) {
                statement.setString(1, matchId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            }
        });
    }

    private static void appendLedgerEntry(
            Connection connection,
            String matchId,
            Team team,
            long points,
            ScoreReason reason
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO score_ledger (match_id, team, points, reason, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, matchId);
            insert.setString(2, team.name());
            insert.setLong(3, points);
            insert.setString(4, reason.storedValue());
            insert.setLong(5, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }

    private static MatchScores readScores(Connection connection, String matchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT red_score, blue_score FROM matches WHERE match_id = ?"
        )) {
            statement.setString(1, matchId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No match row found: " + matchId);
                }
                return new MatchScores(matchId, result.getLong("red_score"), result.getLong("blue_score"));
            }
        }
    }
}
