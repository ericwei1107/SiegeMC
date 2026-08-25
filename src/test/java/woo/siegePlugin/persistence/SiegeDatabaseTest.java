package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiegeDatabaseTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void aFailedTransactionRollsBackEveryWriteItAlreadyMade() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            await(new MatchScoreDao(database).loadOrCreate("eternal-1"));

            assertThrows(ExecutionException.class, () -> await(database.submitTransaction(connection -> {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO score_ledger (match_id, team, points, reason, created_at)
                        VALUES ('eternal-1', 'RED', 10, 'banner_control', 0)
                        """)) {
                    insert.executeUpdate();
                }
                throw new SQLException("deliberate failure after a successful write");
            })));

            assertEquals(0, await(new MatchScoreDao(database).countLedgerEntries("eternal-1")));
        }
    }

    @Test
    void aCommittedTransactionKeepsItsWrites() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            await(new MatchScoreDao(database).loadOrCreate("eternal-1"));

            await(database.submitTransaction(connection -> {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO score_ledger (match_id, team, points, reason, created_at)
                        VALUES ('eternal-1', 'RED', 10, 'banner_control', 0)
                        """)) {
                    insert.executeUpdate();
                }
                return null;
            }));

            assertEquals(1, await(new MatchScoreDao(database).countLedgerEntries("eternal-1")));
        }
    }

    @Test
    void theLedgerRejectsEntriesForAMatchThatDoesNotExist() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            assertThrows(ExecutionException.class, () -> await(database.submit(connection -> {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO score_ledger (match_id, team, points, reason, created_at)
                        VALUES ('no-such-match', 'RED', 10, 'banner_control', 0)
                        """)) {
                    return insert.executeUpdate();
                }
            })));
        }
    }

    @Test
    void writesSurviveReopeningTheDatabase() throws Exception {
        Path databasePath = temporaryDirectory.resolve("durable.db");
        try (SiegeDatabase database = new SiegeDatabase(databasePath)) {
            await(database.initialized());
            await(new MatchScoreDao(database).loadOrCreate("eternal-1"));
        }

        try (SiegeDatabase reopened = new SiegeDatabase(databasePath)) {
            await(reopened.initialized());
            ResultSetCount count = await(reopened.submit(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM matches WHERE match_id = 'eternal-1'"
                ); ResultSet result = statement.executeQuery()) {
                    result.next();
                    return new ResultSetCount(result.getInt(1));
                }
            }));

            assertEquals(1, count.value());
        }
    }

    private record ResultSetCount(int value) {
    }

    private SiegeDatabase openDatabase() throws Exception {
        SiegeDatabase database = new SiegeDatabase(temporaryDirectory.resolve(UUID.randomUUID() + ".db"));
        await(database.initialized());
        return database;
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }
}
