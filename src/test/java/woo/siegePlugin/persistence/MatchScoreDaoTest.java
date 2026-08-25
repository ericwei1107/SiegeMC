package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import woo.siegePlugin.team.Team;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchScoreDaoTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String MATCH = "eternal-1";

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadOrCreateStartsANewMatchAtZero() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScores scores = await(new MatchScoreDao(database).loadOrCreate(MATCH));

            assertEquals(new MatchScores(MATCH, 0L, 0L), scores);
        }
    }

    @Test
    void loadOrCreateRecoversAnExistingMatchInsteadOfResettingIt() throws Exception {
        Path databasePath = temporaryDirectory.resolve("recover.db");
        try (SiegeDatabase database = new SiegeDatabase(databasePath)) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));
            await(dao.award(MATCH, Team.RED, 30L, ScoreReason.BANNER_CONTROL));
        }

        try (SiegeDatabase reopened = new SiegeDatabase(databasePath)) {
            MatchScores recovered = await(new MatchScoreDao(reopened).loadOrCreate(MATCH));

            assertEquals(30L, recovered.redScore());
            assertEquals(0L, recovered.blueScore());
        }
    }

    @Test
    void awardsAccumulatePerTeamAndReturnPersistedTotals() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));

            await(dao.award(MATCH, Team.RED, 10L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH, Team.BLUE, 150L, ScoreReason.ENEMY_DEATH_BONUS));
            MatchScores scores = await(dao.award(MATCH, Team.RED, 20L, ScoreReason.BANNER_CONTROL));

            assertEquals(30L, scores.redScore());
            assertEquals(150L, scores.blueScore());
        }
    }

    @Test
    void everyAwardIsLedgered() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));

            await(dao.award(MATCH, Team.RED, 10L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH, Team.RED, 10L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH, Team.BLUE, 150L, ScoreReason.ENEMY_DEATH_BONUS));

            assertEquals(3, await(dao.countLedgerEntries(MATCH)));
        }
    }

    @Test
    void resetZeroesBothTeamsAndLedgersTheReversal() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));
            await(dao.award(MATCH, Team.RED, 40L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH, Team.BLUE, 150L, ScoreReason.ENEMY_DEATH_BONUS));

            MatchScores reset = await(dao.reset(MATCH));

            assertEquals(new MatchScores(MATCH, 0L, 0L), reset);
            // Two awards plus one reversal entry per team that had points.
            assertEquals(4, await(dao.countLedgerEntries(MATCH)));
        }
    }

    @Test
    void resetOnAZeroedMatchAddsNoReversalEntries() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));

            await(dao.reset(MATCH));

            assertEquals(0, await(dao.countLedgerEntries(MATCH)));
        }
    }

    @Test
    void awardingAnUnknownMatchFailsAndWritesNoLedgerEntry() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));

            assertThrows(
                    ExecutionException.class,
                    () -> await(dao.award("missing-match", Team.RED, 10L, ScoreReason.BANNER_CONTROL))
            );
            assertEquals(0, await(dao.countLedgerEntries("missing-match")));
        }
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
