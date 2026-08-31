package woo.siegePlugin.round;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import woo.siegePlugin.persistence.MatchDefinition;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.MatchStatsDao;
import woo.siegePlugin.persistence.MatchStatus;
import woo.siegePlugin.persistence.RotationStateDao;
import woo.siegePlugin.persistence.ScoreReason;
import woo.siegePlugin.persistence.SiegeDatabase;
import woo.siegePlugin.stats.PlayerMatchStats;
import woo.siegePlugin.team.Team;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restart behaviour for every durable phase.
 *
 * <p>Each test writes the durable state a crash would have left behind, then
 * starts a fresh coordinator against the same database — which is exactly what
 * a server restart does.</p>
 */
class RotationRestartTest {

    @TempDir Path directory;

    @Test
    void anActiveMatchResumesWithItsScoreStatsAndRoster() throws Exception {
        Path db = database();
        UUID fighter = UUID.nameUUIDFromBytes("Fighter".getBytes());
        seedActiveRound(db, fighter, "siege-active-1-kazan");

        try (RotationTestHarness rig = rig(db)) {
            rig.audience.online.add(fighter);
            rig.coordinator().start();
            rig.settle();

            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
            assertEquals(1, rig.published.size(), "services rebind to the recovered map");
            assertEquals("kazan", rig.published.getFirst().map().id());
            assertEquals(RosterPresence.BATTLEFIELD,
                    rig.roster.presenceOf(fighter).orElseThrow().presence());
            assertTrue(rig.stats.isBoundTo("rotation-7"));
            assertEquals(3L, rig.stats.snapshot().iterator().next().kills(),
                    "the last statistics checkpoint is restored");
        }
    }

    @Test
    void anActiveMatchWhoseWorldIsGoneIsAbortedAndItsRosterCarriedIntoIntermission() throws Exception {
        Path db = database();
        UUID fighter = UUID.nameUUIDFromBytes("Fighter".getBytes());
        seedActiveRound(db, fighter, "siege-active-missing");

        try (RotationTestHarness rig = rig(db)) {
            rig.worlds.loadFails.add("kazan");
            rig.audience.online.add(fighter);
            // Hold the next map so the intermission it falls into is observable.
            rig.worlds.holdAllLoads = true;
            rig.coordinator().start();
            rig.settle();

            assertEquals(MatchStatus.ABORTED, await(rig.scoreDao.load("rotation-7")).status());
            assertEquals(RoundPhase.INTERMISSION, rig.rounds.phase(),
                    "an unrecoverable round becomes intermission, not a dead end");
            assertTrue(rig.broadcastContains("abandoned with no winner"));
            assertFalse(rig.broadcastContains("Match Complete"), "no winner may be announced");
            assertEquals(List.of(fighter), await(rig.stateDao.queuedPlayers()).stream()
                    .map(QueuedPlayer::playerId).toList(),
                    "the aborted roster must not lose its participants");
        }
    }

    @Test
    void aMatchTheCompletionTransactionAlreadyClosedRunsItsCeremonyExactlyOnce() throws Exception {
        Path db = database();
        UUID fighter = UUID.nameUUIDFromBytes("Fighter".getBytes());
        seedActiveRound(db, fighter, "siege-active-1-kazan");
        // The award landed, but the server died before the ceremony ran.
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            await(new MatchScoreDao(database).awardWithCutoff(
                    "rotation-7", Team.RED, 100L, ScoreReason.BANNER_CONTROL,
                    List.of(new PlayerMatchStats(fighter, "Fighter", 3L, 12D, 40L))
            ));
        }

        try (RotationTestHarness rig = rig(db)) {
            rig.audience.online.add(fighter);
            // Hold the next map so the intermission it falls into is observable.
            rig.worlds.holdAllLoads = true;
            rig.coordinator().start();
            rig.settle();

            assertEquals(1, rig.broadcasts().stream()
                    .filter(line -> line.contains("Match Complete")).count());
            assertTrue(rig.broadcastContains("Kills MVP: Fighter (3 kills)"));
            assertEquals(RoundPhase.INTERMISSION, rig.rounds.phase());
        }
    }

    @Test
    void restartingAgainAfterTheCeremonyDoesNotAnnounceTheWinnerTwice() throws Exception {
        Path db = database();
        UUID fighter = UUID.nameUUIDFromBytes("Fighter".getBytes());
        seedActiveRound(db, fighter, "siege-active-1-kazan");
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            await(new MatchScoreDao(database).awardWithCutoff(
                    "rotation-7", Team.RED, 100L, ScoreReason.BANNER_CONTROL, List.of()
            ));
        }

        try (RotationTestHarness first = rig(db)) {
            first.worlds.holdAllLoads = true;
            first.coordinator().start();
            first.settle();
            assertEquals(1, first.broadcasts().stream()
                    .filter(line -> line.contains("Match Complete")).count());
        }

        try (RotationTestHarness second = rig(db)) {
            second.worlds.holdAllLoads = true;
            second.coordinator().start();
            second.settle();

            assertFalse(second.broadcastContains("Match Complete"),
                    "an already-announced winner must never be announced again");
            assertEquals(RoundPhase.INTERMISSION, second.rounds.phase());
        }
    }

    @Test
    void anInterruptedActivationReplaysItsPersistedPlanRatherThanReshuffling() throws Exception {
        Path db = database();
        UUID red = UUID.nameUUIDFromBytes("Red".getBytes());
        UUID blue = UUID.nameUUIDFromBytes("Blue".getBytes());
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            await(new MatchScoreDao(database).loadOrCreate(
                    MatchDefinition.rotating("rotation-8", "kazan", "siege-active-8-kazan", 100L)
            ));
            RotationStateDao dao = new RotationStateDao(database);
            await(dao.save(new RotationState(
                    RoundPhase.ACTIVATING, 8L, 0L, "rotation-8", "kazan", "siege-active-8-kazan",
                    "murmansk", "kazan", "siege-active-8-kazan", null, List.of()
            )));
            await(dao.replaceRoster("rotation-8", List.of(
                    new RotationStateDao.RosterEntry(red, "Red", Team.RED, RoundRole.PLAYER, RosterPresence.PLANNED),
                    new RotationStateDao.RosterEntry(blue, "Blue", Team.BLUE, RoundRole.PLAYER, RosterPresence.PLANNED)
            )));
        }

        try (RotationTestHarness rig = rig(db)) {
            rig.audience.online.addAll(List.of(red, blue));
            rig.coordinator().start();
            rig.settle();

            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
            assertEquals(Team.RED, rig.audience.launchedFighters.get(red),
                    "the persisted plan is replayed, not regenerated");
            assertEquals(Team.BLUE, rig.audience.launchedFighters.get(blue));
            assertEquals(1, rig.published.size(), "exactly one match is published");
        }
    }

    @Test
    void anIntermissionRestartResumesItsPreparedCopyWithoutANewCeremony() throws Exception {
        Path db = database();
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            await(new RotationStateDao(database).save(new RotationState(
                    RoundPhase.INTERMISSION, 9L, 0L, null, null, null, "murmansk",
                    "kazan", "siege-active-9-kazan",
                    java.time.Instant.now().plusSeconds(20), List.of()
            )));
        }

        try (RotationTestHarness rig = rig(db)) {
            rig.coordinator().start();
            rig.settle();
            rig.tick();

            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
            assertEquals("siege-active-9-kazan", rig.published.getFirst().world().getName(),
                    "the already-prepared copy is reused rather than copied again");
            assertFalse(rig.broadcastContains("Match Complete"));
        }
    }

    @Test
    void aRecoveryRestartKeepsEveryoneInTheLobbyAndCreatesNoMatch() throws Exception {
        Path db = database();
        UUID player = UUID.nameUUIDFromBytes("Player".getBytes());
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            await(new RotationStateDao(database).save(new RotationState(
                    RoundPhase.RECOVERY, 10L, 0L, null, null, null, "kazan", null, null, null,
                    List.of(RotationCandidate.pending("kazan").failed("template missing"))
            )));
        }

        try (RotationTestHarness rig = rig(db)) {
            rig.audience.online.add(player);
            rig.audience.inLobby.remove(player);
            rig.coordinator().start();
            rig.settle();

            assertEquals(RoundPhase.RECOVERY, rig.rounds.phase());
            assertTrue(rig.audience.isInLobby(player));
            assertTrue(rig.published.isEmpty());
            assertTrue(rig.broadcastContains("needs administrator recovery"));
        }
    }

    @Test
    void aQueuedPlayerSurvivesARestartDuringIntermission() throws Exception {
        Path db = database();
        UUID queued = UUID.nameUUIDFromBytes("Queued".getBytes());
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            RotationStateDao dao = new RotationStateDao(database);
            await(dao.save(new RotationState(
                    RoundPhase.INTERMISSION, 11L, 0L, null, null, null, "murmansk", null, null,
                    java.time.Instant.now().plusSeconds(20), List.of()
            )));
            await(dao.queue(queued, RoundRole.PLAYER, QueueSource.OPT_IN));
        }

        try (RotationTestHarness rig = rig(db)) {
            rig.audience.online.add(queued);
            rig.audience.inLobby.remove(queued);
            rig.worlds.holdAllLoads = true;
            rig.coordinator().start();
            rig.settle();

            rig.advance(Duration.ofSeconds(5));
            rig.tick();
            assertEquals("Lobby transfer in 15s", rig.audience.lastActionBar.get(queued),
                    "the restored queue still receives its countdown");

            rig.worlds.holdAllLoads = false;
            rig.worlds.releaseNextLoad("siege-active-");
            rig.settle();
            rig.advance(Duration.ofSeconds(40));
            rig.tick();

            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
            assertTrue(rig.audience.launchedFighters.containsKey(queued),
                    "a player queued before the restart is launched into the next round");
        }
    }

    @Test
    void aPendingWorldCleanupIsRetriedAfterARestart() throws Exception {
        Path db = database();
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            await(new WorldCleanupDaoAccess(database).enroll("siege-active-orphan", "/tmp/siege-active-orphan"));
            await(new RotationStateDao(database).save(new RotationState(
                    RoundPhase.RECOVERY, 12L, 0L, null, null, null, null, null, null, null, List.of()
            )));
        }

        try (RotationTestHarness rig = rig(db)) {
            rig.coordinator().start();
            rig.settle();

            assertEquals(List.of("siege-active-orphan"), rig.worlds.discarded);
            assertTrue(await(rig.cleanupDao.all()).isEmpty(), "a successful retry clears the queue");
        }
    }

    private Path database() {
        return directory.resolve("restart-" + UUID.randomUUID() + ".db");
    }

    private RotationTestHarness rig(Path db) {
        return new RotationTestHarness(db, List.of("kazan", "murmansk"));
    }

    /** Writes the durable picture of a round that was live when the server died. */
    private static void seedActiveRound(Path db, UUID fighter, String runtimeWorld) throws Exception {
        try (SiegeDatabase database = new SiegeDatabase(db)) {
            await(new MatchScoreDao(database).loadOrCreate(
                    MatchDefinition.rotating("rotation-7", "kazan", runtimeWorld, 100L)
            ));
            RotationStateDao dao = new RotationStateDao(database);
            await(dao.save(new RotationState(
                    RoundPhase.ACTIVE, 7L, 0L, "rotation-7", "kazan", runtimeWorld,
                    "murmansk", null, null, null, List.of()
            )));
            await(dao.replaceRoster("rotation-7", List.of(new RotationStateDao.RosterEntry(
                    fighter, "Fighter", Team.RED, RoundRole.PLAYER, RosterPresence.BATTLEFIELD
            ))));
            await(new MatchStatsDao(database).saveSnapshot("rotation-7", List.of(
                    new PlayerMatchStats(fighter, "Fighter", 3L, 12D, 40L)
            )));
        }
    }

    /** Small shim so this test can enrol cleanup rows before the rig exists. */
    private record WorldCleanupDaoAccess(SiegeDatabase database) {
        CompletableFuture<Void> enroll(String worldName, String folder) {
            return new woo.siegePlugin.persistence.WorldCleanupDao(database).enroll(worldName, folder);
        }
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
}
