package woo.siegePlugin.persistence;

import woo.siegePlugin.round.QueueSource;
import woo.siegePlugin.round.QueuedPlayer;
import woo.siegePlugin.round.RosterPresence;
import woo.siegePlugin.round.RotationCandidate;
import woo.siegePlugin.round.RoundPhase;
import woo.siegePlugin.round.RoundRole;
import woo.siegePlugin.round.RotationState;
import woo.siegePlugin.team.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable lifecycle, queue, and roster storage for restart-safe rotation. */
public final class RotationStateDao {

    /** Thrown when a lifecycle write loses its compare-and-set race. */
    public static final class StaleRevisionException extends RuntimeException {
        private final long storedRevision;

        StaleRevisionException(long expected, long storedRevision) {
            super("Rotation state moved on: expected revision " + expected + " but found " + storedRevision);
            this.storedRevision = storedRevision;
        }

        public long storedRevision() {
            return storedRevision;
        }
    }

    private final SiegeDatabase database;

    public RotationStateDao(SiegeDatabase database) {
        this.database = database;
    }

    public CompletableFuture<Optional<RotationState>> load() {
        return database.submit(connection -> readState(connection));
    }

    /**
     * Compare-and-set write. The update only lands while the stored revision
     * still matches the one the caller read, so a stale asynchronous completion
     * cannot overwrite a newer phase decision.
     *
     * @return the state as persisted, carrying its new revision
     */
    public CompletableFuture<RotationState> save(RotationState expected) {
        return database.submitTransaction(connection -> {
            RotationState stored = readState(connection).orElse(null);
            if (stored == null) {
                if (expected.revision() != 0L) {
                    throw new StaleRevisionException(expected.revision(), -1L);
                }
            } else if (stored.revision() != expected.revision()) {
                throw new StaleRevisionException(expected.revision(), stored.revision());
            }

            RotationState next = expected.nextRevision();
            try (PreparedStatement upsert = connection.prepareStatement("""
                    INSERT INTO rotation_state (
                        singleton_id, phase, generation, revision, current_match_id, current_map_id,
                        current_runtime_world, previous_map_id, prepared_map_id,
                        prepared_runtime_world, intermission_deadline, candidates, updated_at
                    ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?)
                    ON CONFLICT(singleton_id) DO UPDATE SET
                        phase=excluded.phase, generation=excluded.generation, revision=excluded.revision,
                        current_match_id=excluded.current_match_id, current_map_id=excluded.current_map_id,
                        current_runtime_world=excluded.current_runtime_world,
                        previous_map_id=excluded.previous_map_id, prepared_map_id=excluded.prepared_map_id,
                        prepared_runtime_world=excluded.prepared_runtime_world,
                        intermission_deadline=excluded.intermission_deadline,
                        updated_at=excluded.updated_at
                    WHERE rotation_state.revision = ?
                    """)) {
                upsert.setString(1, next.phase().name());
                upsert.setLong(2, next.generation());
                upsert.setLong(3, next.revision());
                upsert.setString(4, next.currentMatchId());
                upsert.setString(5, next.currentMapId());
                upsert.setString(6, next.currentRuntimeWorld());
                upsert.setString(7, next.previousMapId());
                upsert.setString(8, next.preparedMapId());
                upsert.setString(9, next.preparedRuntimeWorld());
                if (next.intermissionDeadline() == null) {
                    upsert.setObject(10, null);
                } else {
                    upsert.setLong(10, next.intermissionDeadline().toEpochMilli());
                }
                upsert.setLong(11, System.currentTimeMillis());
                upsert.setLong(12, expected.revision());
                if (upsert.executeUpdate() != 1) {
                    throw new StaleRevisionException(expected.revision(), -1L);
                }
            }
            replaceCandidates(connection, next.candidates());
            return next;
        });
    }

    private static void replaceCandidates(Connection connection, List<RotationCandidate> candidates)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM rotation_candidates")) {
            delete.executeUpdate();
        }
        if (candidates.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO rotation_candidates (position, map_id, status, failure_reason)
                VALUES (?, ?, ?, ?)
                """)) {
            for (int position = 0; position < candidates.size(); position++) {
                RotationCandidate candidate = candidates.get(position);
                insert.setInt(1, position);
                insert.setString(2, candidate.mapId());
                insert.setString(3, candidate.status().name());
                insert.setString(4, candidate.failureReason());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Optional<RotationState> readState(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM rotation_state WHERE singleton_id = 1"
        ); ResultSet result = query.executeQuery()) {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(new RotationState(
                    RoundPhase.valueOf(result.getString("phase")),
                    result.getLong("generation"),
                    result.getLong("revision"),
                    result.getString("current_match_id"),
                    result.getString("current_map_id"),
                    result.getString("current_runtime_world"),
                    result.getString("previous_map_id"),
                    result.getString("prepared_map_id"),
                    result.getString("prepared_runtime_world"),
                    nullableInstant(result, "intermission_deadline"),
                    readCandidates(connection)
            ));
        }
    }

    private static List<RotationCandidate> readCandidates(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT map_id, status, failure_reason FROM rotation_candidates ORDER BY position
                """); ResultSet result = query.executeQuery()) {
            List<RotationCandidate> candidates = new ArrayList<>();
            while (result.next()) {
                candidates.add(new RotationCandidate(
                        result.getString("map_id"),
                        RotationCandidate.CandidateStatus.valueOf(result.getString("status")),
                        result.getString("failure_reason")
                ));
            }
            return List.copyOf(candidates);
        }
    }

    public CompletableFuture<Void> queue(UUID playerId, RoundRole role, QueueSource source) {
        return database.submit(connection -> {
            try (PreparedStatement upsert = connection.prepareStatement("""
                    INSERT INTO intermission_queue (player_uuid, role, source, queued_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET role=excluded.role, source=excluded.source
                    """)) {
                upsert.setString(1, playerId.toString());
                upsert.setString(2, role.name());
                upsert.setString(3, source.name());
                upsert.setLong(4, System.currentTimeMillis());
                upsert.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> dequeue(UUID playerId) {
        return database.submit(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM intermission_queue WHERE player_uuid = ?"
            )) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<QueuedPlayer>> queuedPlayers() {
        return database.submit(connection -> {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT player_uuid, role, source
                    FROM intermission_queue
                    ORDER BY queued_at, player_uuid
                    """); ResultSet result = query.executeQuery()) {
                List<QueuedPlayer> queued = new ArrayList<>();
                while (result.next()) {
                    queued.add(new QueuedPlayer(
                            UUID.fromString(result.getString("player_uuid")),
                            RoundRole.valueOf(result.getString("role")),
                            QueueSource.valueOf(result.getString("source"))
                    ));
                }
                return List.copyOf(queued);
            }
        });
    }

    public CompletableFuture<Void> replaceRoster(String matchId, List<RosterEntry> roster) {
        return database.submitTransaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM match_roster WHERE match_id = ?"
            )) {
                delete.setString(1, matchId);
                delete.executeUpdate();
            }
            insertRoster(connection, matchId, roster);
            try (PreparedStatement clear = connection.prepareStatement(
                    "DELETE FROM intermission_queue WHERE player_uuid = ?"
            )) {
                for (RosterEntry value : roster) {
                    clear.setString(1, value.playerId().toString());
                    clear.addBatch();
                }
                clear.executeBatch();
            }
            return null;
        });
    }

    /**
     * Moves an aborted match's roster into the intermission queue in one
     * transaction, so an unrecoverable round cannot silently lose participants.
     */
    public CompletableFuture<List<QueuedPlayer>> transferRosterToQueue(String matchId) {
        return database.submitTransaction(connection -> {
            List<RosterEntry> roster = readRoster(connection, matchId);
            try (PreparedStatement upsert = connection.prepareStatement("""
                    INSERT INTO intermission_queue (player_uuid, role, source, queued_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET role=excluded.role, source=excluded.source
                    """)) {
                long now = System.currentTimeMillis();
                for (RosterEntry entry : roster) {
                    upsert.setString(1, entry.playerId().toString());
                    upsert.setString(2, entry.role().name());
                    upsert.setString(3, QueueSource.PREVIOUS_MATCH.name());
                    upsert.setLong(4, now);
                    upsert.addBatch();
                }
                upsert.executeBatch();
            }
            return roster.stream()
                    .map(entry -> new QueuedPlayer(entry.playerId(), entry.role(), QueueSource.PREVIOUS_MATCH))
                    .toList();
        });
    }

    public CompletableFuture<Void> upsertRoster(String matchId, RosterEntry value) {
        return database.submitTransaction(connection -> {
            insertRoster(connection, matchId, List.of(value));
            try (PreparedStatement dequeue = connection.prepareStatement(
                    "DELETE FROM intermission_queue WHERE player_uuid = ?"
            )) {
                dequeue.setString(1, value.playerId().toString());
                dequeue.executeUpdate();
            }
            return null;
        });
    }

    /** Records that a rostered player moved between the battlefield and the lobby. */
    public CompletableFuture<Void> updatePresence(String matchId, UUID playerId, RosterPresence presence) {
        return database.submit(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE match_roster SET presence = ? WHERE match_id = ? AND player_uuid = ?"
            )) {
                update.setString(1, presence.name());
                update.setString(2, matchId);
                update.setString(3, playerId.toString());
                update.executeUpdate();
            }
            return null;
        });
    }

    /**
     * The planned team assignments recorded before Towny was touched. An
     * interrupted activation replays exactly these rather than re-shuffling,
     * so a restart cannot move a player who was already launched.
     */
    public CompletableFuture<List<RosterEntry>> rosterAssignments(String matchId) {
        return database.submit(connection -> readRoster(connection, matchId));
    }

    private static void insertRoster(Connection connection, String matchId, List<RosterEntry> roster)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO match_roster (match_id, player_uuid, player_name, team, role, presence)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(match_id, player_uuid) DO UPDATE SET
                    player_name=excluded.player_name, team=excluded.team,
                    role=excluded.role, presence=excluded.presence
                """)) {
            for (RosterEntry value : roster) {
                insert.setString(1, matchId);
                insert.setString(2, value.playerId().toString());
                insert.setString(3, value.playerName());
                insert.setString(4, value.team() == null ? null : value.team().name());
                insert.setString(5, value.role().name());
                insert.setString(6, value.presence().name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<RosterEntry> readRoster(Connection connection, String matchId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT player_uuid, player_name, team, role, presence
                FROM match_roster
                WHERE match_id = ?
                ORDER BY rowid
                """)) {
            query.setString(1, matchId);
            try (ResultSet result = query.executeQuery()) {
                List<RosterEntry> roster = new ArrayList<>();
                while (result.next()) {
                    String team = result.getString("team");
                    roster.add(new RosterEntry(
                            UUID.fromString(result.getString("player_uuid")),
                            result.getString("player_name"),
                            team == null ? null : Team.valueOf(team),
                            RoundRole.valueOf(result.getString("role")),
                            RosterPresence.valueOf(result.getString("presence"))
                    ));
                }
                return List.copyOf(roster);
            }
        }
    }

    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    public record RosterEntry(
            UUID playerId,
            String playerName,
            Team team,
            RoundRole role,
            RosterPresence presence
    ) {
        public RosterEntry withPresence(RosterPresence next) {
            return new RosterEntry(playerId, playerName, team, role, next);
        }
    }
}
