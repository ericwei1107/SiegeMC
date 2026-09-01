package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.stats.PlayerMatchStats;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchScoreDaoTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String MATCH_ID = "eternal-1";
    private static final MatchDefinition MATCH = MatchDefinition.eternalForWorld("siegeworld");

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadOrCreateStartsANewMatchAtZero() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchRecord scores = await(new MatchScoreDao(database).loadOrCreate(MATCH));

            assertEquals(MATCH_ID, scores.matchId());
            assertEquals(MatchStatus.ACTIVE, scores.status());
            assertEquals("siegeworld:primary", scores.capturePointId());
            assertEquals(0L, scores.redScore());
            assertEquals(0L, scores.blueScore());
        }
    }

    @Test
    void loadOrCreateRecoversAnExistingMatchInsteadOfResettingIt() throws Exception {
        Path databasePath = temporaryDirectory.resolve("recover.db");
        try (SiegeDatabase database = new SiegeDatabase(databasePath)) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));
            await(dao.award(MATCH_ID, Team.RED, 30L, ScoreReason.BANNER_CONTROL));
        }

        try (SiegeDatabase reopened = new SiegeDatabase(databasePath)) {
            MatchRecord recovered = await(new MatchScoreDao(reopened).loadOrCreate(MATCH));

            assertEquals(30L, recovered.redScore());
            assertEquals(0L, recovered.blueScore());
        }
    }

    @Test
    void awardsAccumulatePerTeamAndReturnPersistedTotals() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));

            await(dao.award(MATCH_ID, Team.RED, 10L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH_ID, Team.BLUE, 150L, ScoreReason.ENEMY_DEATH_BONUS));
            MatchRecord scores = await(dao.award(MATCH_ID, Team.RED, 20L, ScoreReason.BANNER_CONTROL));

            assertEquals(30L, scores.redScore());
            assertEquals(150L, scores.blueScore());
        }
    }

    @Test
    void everyAwardIsLedgered() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));

            await(dao.award(MATCH_ID, Team.RED, 10L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH_ID, Team.RED, 10L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH_ID, Team.BLUE, 150L, ScoreReason.ENEMY_DEATH_BONUS));

            assertEquals(3, await(dao.countLedgerEntries(MATCH_ID)));
        }
    }

    @Test
    void resetZeroesBothTeamsAndLedgersTheReversal() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchRecord original = await(dao.loadOrCreate(MATCH));
            await(dao.award(MATCH_ID, Team.RED, 40L, ScoreReason.BANNER_CONTROL));
            await(dao.award(MATCH_ID, Team.BLUE, 150L, ScoreReason.ENEMY_DEATH_BONUS));

            MatchRecord reset = await(dao.reset(MATCH_ID));

            assertEquals(0L, reset.redScore());
            assertEquals(0L, reset.blueScore());
            assertEquals(MatchStatus.ACTIVE, reset.status());
            assertEquals("siegeworld:primary", reset.capturePointId());
            assertEquals(original.startedAt(), reset.startedAt());
            // Two awards plus one reversal entry per team that had points.
            assertEquals(4, await(dao.countLedgerEntries(MATCH_ID)));
        }
    }

    @Test
    void resetOnAZeroedMatchAddsNoReversalEntries() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));

            await(dao.reset(MATCH_ID));

            assertEquals(0, await(dao.countLedgerEntries(MATCH_ID)));
        }
    }

    @Test
    void testScoreSetupSetsAnExactActiveTeamScoreAndLedgersItsAdjustment() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            await(dao.loadOrCreate(MATCH));
            await(dao.award(MATCH_ID, Team.RED, 40L, ScoreReason.BANNER_CONTROL));

            MatchRecord prepared = await(dao.setActiveTeamScore(
                    MATCH_ID, Team.RED, 9_990L, ScoreReason.ADMIN_TEST_SET
            ));

            assertEquals(9_990L, prepared.redScore());
            assertEquals(MatchStatus.ACTIVE, prepared.status());
            assertEquals(2, await(dao.countLedgerEntries(MATCH_ID)));
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

    @Test
    void migratesALegacyMatchWithoutChangingItsScoresOrLedger() throws Exception {
        Path databasePath = temporaryDirectory.resolve("legacy.db");
        createLegacyDatabase(databasePath);

        try (SiegeDatabase database = new SiegeDatabase(databasePath)) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchRecord migrated = await(dao.loadOrCreate(MATCH));

            assertEquals(MatchStatus.ACTIVE, migrated.status());
            assertEquals(Instant.ofEpochMilli(1_234L), migrated.startedAt());
            assertEquals("siegeworld:primary", migrated.capturePointId());
            assertEquals(40L, migrated.redScore());
            assertEquals(60L, migrated.blueScore());
            assertEquals(1, await(dao.countLedgerEntries(MATCH_ID)));
        }
    }

    @Test
    void firstCutoffCrossingPreservesOvershootAndRejectsEveryLaterAward() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchDefinition match = MatchDefinition.rotating("rotation-cutoff", "kazan", "active", 100L);
            await(dao.loadOrCreate(match));
            activateRotationFor(database, match.matchId());
            await(dao.awardWithCutoff(match.matchId(), Team.RED, 95L, ScoreReason.BANNER_CONTROL));

            AwardOutcome winner = await(dao.awardWithCutoff(
                    match.matchId(), Team.RED, 10L, ScoreReason.BANNER_CONTROL
            ));
            AwardOutcome rejected = await(dao.awardWithCutoff(
                    match.matchId(), Team.BLUE, 150L, ScoreReason.ENEMY_DEATH_BONUS
            ));

            assertTrue(winner.accepted());
            assertTrue(winner.completedNow());
            assertEquals(105L, winner.match().redScore());
            assertEquals(Team.RED, winner.match().winner());
            assertEquals(MatchStatus.COMPLETED, winner.match().status());
            assertFalse(rejected.accepted());
            assertEquals(0L, rejected.match().blueScore());
            assertEquals(2, await(dao.countLedgerEntries(match.matchId())));
        }
    }

    @Test
    void concurrentCrossingAwardsProduceExactlyOneCompletion() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchDefinition match = MatchDefinition.rotating("rotation-race", "kazan", "active", 100L);
            await(dao.loadOrCreate(match));
            activateRotationFor(database, match.matchId());

            CompletableFuture<AwardOutcome> red = dao.awardWithCutoff(
                    match.matchId(), Team.RED, 100L, ScoreReason.BANNER_CONTROL
            );
            CompletableFuture<AwardOutcome> blue = dao.awardWithCutoff(
                    match.matchId(), Team.BLUE, 100L, ScoreReason.BANNER_CONTROL
            );
            List<AwardOutcome> outcomes = List.of(await(red), await(blue));

            assertEquals(1L, outcomes.stream().filter(AwardOutcome::completedNow).count());
            assertEquals(1L, outcomes.stream().filter(AwardOutcome::accepted).count());
            assertEquals(1, await(dao.countLedgerEntries(match.matchId())));
        }
    }

    @Test
    void winningTransactionStoresTheFinalMvpSnapshot() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchDefinition match = MatchDefinition.rotating("rotation-stats", "kazan", "active", 100L);
            await(dao.loadOrCreate(match));
            activateRotationFor(database, match.matchId());
            UUID playerId = UUID.randomUUID();

            AwardOutcome outcome = await(dao.awardWithCutoff(
                    match.matchId(), Team.RED, 100L, ScoreReason.ENEMY_DEATH_BONUS,
                    List.of(new PlayerMatchStats(playerId, "Winner", 4L, 27.5D, 19L))
            ));

            assertTrue(outcome.completedNow());
            PlayerMatchStats stored = await(new MatchStatsDao(database).load(match.matchId())).getFirst();
            assertEquals(playerId, stored.playerId());
            assertEquals(4L, stored.kills());
            assertEquals(27.5D, stored.damage());
            assertEquals(19L, stored.bannerSeconds());
        }
    }

    @Test
    void aWinningAwardRollsBackCompletelyWhenRotationStateIsMissing() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchDefinition match = MatchDefinition.rotating("rotation-orphan", "kazan", "active", 100L);
            await(dao.loadOrCreate(match));
            // Deliberately no rotation_state row: nothing owns this match.

            assertThrows(ExecutionException.class, () -> dao.awardWithCutoff(
                    match.matchId(), Team.RED, 100L, ScoreReason.BANNER_CONTROL,
                    List.of(new PlayerMatchStats(UUID.randomUUID(), "Nobody", 3L, 10D, 5L))
            ).get(5, TimeUnit.SECONDS));

            MatchRecord after = await(dao.load(match.matchId()));
            assertEquals(0L, after.redScore(), "the score must not survive the failed transaction");
            assertEquals(MatchStatus.ACTIVE, after.status());
            assertNull(after.winner());
            assertNull(after.endedAt());
            assertEquals(0, await(dao.countLedgerEntries(match.matchId())));
            assertTrue(await(new MatchStatsDao(database).load(match.matchId())).isEmpty());
        }
    }

    @Test
    void aWinningAwardRollsBackWhenRotationStateNamesADifferentMatch() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchDefinition match = MatchDefinition.rotating("rotation-mismatch", "kazan", "active", 100L);
            await(dao.loadOrCreate(match));
            activateRotationFor(database, "some-other-match");

            assertThrows(ExecutionException.class, () -> dao.awardWithCutoff(
                    match.matchId(), Team.RED, 100L, ScoreReason.BANNER_CONTROL
            ).get(5, TimeUnit.SECONDS));

            MatchRecord after = await(dao.load(match.matchId()));
            assertEquals(0L, after.redScore());
            assertEquals(MatchStatus.ACTIVE, after.status());
            assertEquals(0, await(dao.countLedgerEntries(match.matchId())));
        }
    }

    @Test
    void aNonCrossingAwardDoesNotNeedRotationState() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            MatchScoreDao dao = new MatchScoreDao(database);
            MatchDefinition match = MatchDefinition.rotating("rotation-partial", "kazan", "active", 100L);
            await(dao.loadOrCreate(match));

            AwardOutcome outcome = await(dao.awardWithCutoff(
                    match.matchId(), Team.RED, 40L, ScoreReason.BANNER_CONTROL
            ));

            assertTrue(outcome.accepted());
            assertFalse(outcome.completedNow());
            assertEquals(40L, outcome.match().redScore());
        }
    }

    /**
     * The winning transaction only commits when durable rotation state is ACTIVE
     * for that match, so every cutoff test must set that state up first.
     */
    private static void activateRotationFor(SiegeDatabase database, String matchId) throws Exception {
        await(new RotationStateDao(database).save(new woo.siegePlugin.round.RotationState(
                woo.siegePlugin.round.RoundPhase.ACTIVE, 1L, 0L, matchId, "kazan", "active",
                null, null, null, null, List.of()
        )));
    }

    private static void createLegacyDatabase(Path databasePath) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE matches (
                        match_id TEXT PRIMARY KEY,
                        red_score INTEGER NOT NULL DEFAULT 0,
                        blue_score INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE score_ledger (
                        entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        match_id TEXT NOT NULL,
                        team TEXT NOT NULL,
                        points INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO matches (match_id, red_score, blue_score, created_at, updated_at)
                    VALUES ('eternal-1', 40, 60, 1234, 5678)
                    """);
            statement.execute("""
                    INSERT INTO score_ledger (match_id, team, points, reason, created_at)
                    VALUES ('eternal-1', 'RED', 40, 'banner_control', 1234)
                    """);
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
