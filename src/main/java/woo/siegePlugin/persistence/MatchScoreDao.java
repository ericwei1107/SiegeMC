package woo.siegePlugin.persistence;

import woo.siegePlugin.team.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.Collection;
import woo.siegePlugin.stats.PlayerMatchStats;

/**
 * Persists match totals and the ledger that explains them. Every total change
 * and its ledger entry are written in one transaction.
 */
public final class MatchScoreDao {

    private final SiegeDatabase database;

    public MatchScoreDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Recovers an existing match or starts it with its declared durable identity. */
    public CompletableFuture<MatchRecord> loadOrCreate(MatchDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return database.submitTransaction(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO matches (
                        match_id, status, start_time, capture_point_id, map_id, runtime_world, score_limit,
                        red_score, blue_score, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
                    ON CONFLICT(match_id) DO NOTHING
                    """)) {
                insert.setString(1, definition.matchId());
                insert.setString(2, definition.status().name());
                insert.setLong(3, now);
                insert.setString(4, definition.capturePointId());
                insert.setString(5, definition.mapId());
                insert.setString(6, definition.runtimeWorld());
                insert.setLong(7, definition.scoreLimit());
                insert.setLong(8, now);
                insert.setLong(9, now);
                insert.executeUpdate();
            }
            backfillLegacyMetadata(connection, definition);
            return readMatch(connection, definition.matchId());
        });
    }

    /**
     * Adds points to one team and records why. Returns the totals as persisted,
     * so callers never have to guess at the post-write state.
     */
    public CompletableFuture<MatchRecord> award(String matchId, Team team, long points, ScoreReason reason) {
        return awardWithCutoff(matchId, team, points, reason)
                .thenApply(AwardOutcome::match);
    }

    /**
     * Atomically applies one award and closes the match if that write crosses
     * its durable score limit. Once closed, every later queued award is
     * rejected without a ledger entry.
     */
    public CompletableFuture<AwardOutcome> awardWithCutoff(
            String matchId,
            Team team,
            long points,
            ScoreReason reason
    ) {
        return awardWithCutoff(matchId, team, points, reason, java.util.List.of());
    }

    public CompletableFuture<AwardOutcome> awardWithCutoff(
            String matchId,
            Team team,
            long points,
            ScoreReason reason,
            Collection<PlayerMatchStats> finalStats
    ) {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(reason, "reason");
        return database.submitTransaction(connection -> {
            MatchRecord before = readMatch(connection, matchId);
            if (before.status() != MatchStatus.ACTIVE) {
                return AwardOutcome.rejected(before);
            }
            String column = team == Team.RED ? "red_score" : "blue_score";
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE matches SET " + column + " = " + column + " + ?, updated_at = ? "
                            + "WHERE match_id = ? AND status = 'ACTIVE'"
            )) {
                update.setLong(1, points);
                update.setLong(2, System.currentTimeMillis());
                update.setString(3, matchId);
                if (update.executeUpdate() == 0) {
                    throw new SQLException("No match row to award points to: " + matchId);
                }
            }
            appendLedgerEntry(connection, matchId, team, points, reason);
            MatchRecord afterAward = readMatch(connection, matchId);
            boolean completed = afterAward.scoreFor(team) >= afterAward.scoreLimit();
            if (completed) {
                MatchStatsDao.replaceSnapshot(connection, matchId, finalStats);
                long now = System.currentTimeMillis();
                try (PreparedStatement close = connection.prepareStatement("""
                        UPDATE matches
                        SET status = 'COMPLETED', winner = ?, end_time = ?, updated_at = ?
                        WHERE match_id = ? AND status = 'ACTIVE'
                        """)) {
                    close.setString(1, team.name());
                    close.setLong(2, now);
                    close.setLong(3, now);
                    close.setString(4, matchId);
                    if (close.executeUpdate() != 1) {
                        throw new SQLException("Could not atomically complete match " + matchId);
                    }
                }
                // The whole award — score, ledger, final statistics, winner, and
                // status — is only real if the coordinator's durable state moves
                // to COMPLETING with it. Anything else means this award belongs
                // to a match the coordinator no longer considers active, so the
                // transaction rolls back rather than closing a match nobody
                // will run a ceremony for. Bumping the revision also invalidates
                // any lifecycle write still in flight against the old value.
                try (PreparedStatement completing = connection.prepareStatement("""
                        UPDATE rotation_state
                        SET phase = 'COMPLETING', revision = revision + 1, updated_at = ?
                        WHERE singleton_id = 1 AND current_match_id = ? AND phase = 'ACTIVE'
                        """)) {
                    completing.setLong(1, now);
                    completing.setString(2, matchId);
                    if (completing.executeUpdate() != 1) {
                        throw new SQLException(
                                "Refusing to complete " + matchId
                                        + ": rotation state is not ACTIVE for this match"
                        );
                    }
                }
            }
            return new AwardOutcome(true, completed, readMatch(connection, matchId));
        });
    }


    /** Marks an interrupted active match aborted without fabricating a winner. */
    public CompletableFuture<MatchRecord> abort(String matchId) {
        Objects.requireNonNull(matchId, "matchId");
        return database.submitTransaction(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE matches
                    SET status = 'ABORTED', winner = NULL, end_time = ?, updated_at = ?
                    WHERE match_id = ? AND status = 'ACTIVE'
                    """)) {
                update.setLong(1, now);
                update.setLong(2, now);
                update.setString(3, matchId);
                update.executeUpdate();
            }
            return readMatch(connection, matchId);
        });
    }

    public CompletableFuture<MatchRecord> load(String matchId) {
        Objects.requireNonNull(matchId, "matchId");
        return database.submit(connection -> readMatch(connection, matchId));
    }

    /** Archives the pre-rotation endless match while retaining all history. */
    public CompletableFuture<Void> archiveLegacyMatch() {
        return database.submit(connection -> {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE matches
                    SET status = 'LEGACY', updated_at = ?
                    WHERE match_id = 'eternal-1' AND status = 'ACTIVE'
                    """)) {
                update.setLong(1, System.currentTimeMillis());
                update.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Zeroes both totals, ledgering the reversal of each so the audit trail
     * still sums to the stored score.
     */
    public CompletableFuture<MatchRecord> reset(String matchId) {
        Objects.requireNonNull(matchId, "matchId");
        return database.submitTransaction(connection -> {
            MatchRecord before = readMatch(connection, matchId);

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
            return readMatch(connection, matchId);
        });
    }

    /**
     * Sets one active team's score to an exact value while preserving a ledger
     * entry for the adjustment. This deliberately does not evaluate the
     * winning cutoff: test setup must leave the final scoring event to prove
     * the normal completion and rotation handoff.
     */
    public CompletableFuture<MatchRecord> setActiveTeamScore(
            String matchId, Team team, long score, ScoreReason reason
    ) {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(reason, "reason");
        if (score < 0L) {
            throw new IllegalArgumentException("Score must not be negative");
        }
        return database.submitTransaction(connection -> {
            MatchRecord before = readMatch(connection, matchId);
            if (before.status() != MatchStatus.ACTIVE) {
                throw new SQLException("Cannot set a score for " + matchId + ": match is " + before.status());
            }
            long adjustment = score - before.scoreFor(team);
            if (adjustment != 0L) {
                appendLedgerEntry(connection, matchId, team, adjustment, reason);
            }
            String column = team == Team.RED ? "red_score" : "blue_score";
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE matches SET " + column + " = ?, updated_at = ? WHERE match_id = ? AND status = 'ACTIVE'"
            )) {
                update.setLong(1, score);
                update.setLong(2, System.currentTimeMillis());
                update.setString(3, matchId);
                if (update.executeUpdate() != 1) {
                    throw new SQLException("Could not set the score for " + matchId);
                }
            }
            return readMatch(connection, matchId);
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

    private static void backfillLegacyMetadata(Connection connection, MatchDefinition definition) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE matches
                SET status = CASE WHEN status IS NULL OR TRIM(status) = '' THEN ? ELSE status END,
                    start_time = CASE WHEN start_time = 0 THEN created_at ELSE start_time END,
                    capture_point_id = CASE
                        WHEN capture_point_id IS NULL OR TRIM(capture_point_id) = '' THEN ?
                        ELSE capture_point_id
                    END,
                    map_id = CASE WHEN map_id IS NULL OR TRIM(map_id) = '' THEN ? ELSE map_id END,
                    runtime_world = CASE
                        WHEN runtime_world IS NULL OR TRIM(runtime_world) = '' THEN ?
                        ELSE runtime_world
                    END,
                    score_limit = CASE WHEN score_limit <= 0 THEN ? ELSE score_limit END
                WHERE match_id = ?
                """)) {
            update.setString(1, definition.status().name());
            update.setString(2, definition.capturePointId());
            update.setString(3, definition.mapId());
            update.setString(4, definition.runtimeWorld());
            update.setLong(5, definition.scoreLimit());
            update.setString(6, definition.matchId());
            update.executeUpdate();
        }
    }

    private static MatchRecord readMatch(Connection connection, String matchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT status, start_time, capture_point_id, map_id, runtime_world, score_limit,
                       red_score, blue_score, winner, end_time
                FROM matches WHERE match_id = ?
                """
        )) {
            statement.setString(1, matchId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No match row found: " + matchId);
                }
                return new MatchRecord(
                        matchId,
                        MatchStatus.valueOf(result.getString("status")),
                        java.time.Instant.ofEpochMilli(result.getLong("start_time")),
                        result.getString("capture_point_id"),
                        result.getString("map_id"),
                        result.getString("runtime_world"),
                        result.getLong("score_limit"),
                        result.getLong("red_score"),
                        result.getLong("blue_score"),
                        result.getString("winner") == null ? null : Team.valueOf(result.getString("winner")),
                        result.getObject("end_time") == null
                                ? null
                                : java.time.Instant.ofEpochMilli(result.getLong("end_time"))
                );
            }
        }
    }
}
