package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import woo.siegePlugin.round.QueueSource;
import woo.siegePlugin.round.RosterPresence;
import woo.siegePlugin.round.RotationCandidate;
import woo.siegePlugin.round.RoundPhase;
import woo.siegePlugin.round.RoundRole;
import woo.siegePlugin.round.RotationState;
import woo.siegePlugin.team.Team;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationStateDaoTest {

    @TempDir Path temporaryDirectory;

    @Test
    void lifecycleAndQueueSurviveReopenAndRosterPublicationClearsQueue() throws Exception {
        Path path = temporaryDirectory.resolve("rotation.db");
        UUID fighter = UUID.randomUUID();
        Instant deadline = Instant.ofEpochMilli(123_456L);
        try (SiegeDatabase database = new SiegeDatabase(path)) {
            RotationStateDao dao = new RotationStateDao(database);
            await(new MatchScoreDao(database).loadOrCreate(
                    MatchDefinition.rotating("rotation-3", "kazan", "active-kazan", 10_000L)
            ));
            await(dao.save(new RotationState(
                    RoundPhase.INTERMISSION, 3L, 0L, "rotation-3", "kazan", "active-kazan",
                    "murmansk", "al_quds", "active-al-quds", deadline,
                    List.of(RotationCandidate.pending("al_quds"), RotationCandidate.pending("kazan"))
            )));
            await(dao.queue(fighter, RoundRole.PLAYER, QueueSource.PREVIOUS_MATCH));
        }

        try (SiegeDatabase database = new SiegeDatabase(path)) {
            RotationStateDao dao = new RotationStateDao(database);
            RotationState state = await(dao.load()).orElseThrow();
            assertEquals(RoundPhase.INTERMISSION, state.phase());
            assertEquals(deadline, state.intermissionDeadline());
            assertEquals(1L, state.revision(), "a successful save advances the revision");
            assertEquals(List.of("al_quds", "kazan"), state.candidates().stream()
                    .map(RotationCandidate::mapId).toList());
            assertEquals(fighter, await(dao.queuedPlayers()).getFirst().playerId());
            await(dao.replaceRoster("rotation-3", List.of(new RotationStateDao.RosterEntry(
                    fighter, "Fighter", Team.RED, RoundRole.PLAYER, RosterPresence.BATTLEFIELD
            ))));
            assertTrue(await(dao.queuedPlayers()).isEmpty());
        }
    }

    @Test
    void aWriteCarryingAStaleRevisionIsRejectedInsteadOfOverwriting() throws Exception {
        try (SiegeDatabase database = new SiegeDatabase(temporaryDirectory.resolve("cas.db"))) {
            RotationStateDao dao = new RotationStateDao(database);
            RotationState first = await(dao.save(state(RoundPhase.INTERMISSION, 0L)));
            assertEquals(1L, first.revision());

            // A second decision lands, moving the stored revision on.
            RotationState second = await(dao.save(first.withPhase(RoundPhase.ACTIVATING)));
            assertEquals(2L, second.revision());

            // A slow completion still holding the first revision must lose.
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> dao.save(first.withPhase(RoundPhase.RECOVERY)).get(5, TimeUnit.SECONDS)
            );
            assertInstanceOf(RotationStateDao.StaleRevisionException.class, rootCause(failure));

            assertEquals(RoundPhase.ACTIVATING, await(dao.load()).orElseThrow().phase());
        }
    }

    @Test
    void candidateOutcomesAndReasonsSurviveARestart() throws Exception {
        Path path = temporaryDirectory.resolve("candidates.db");
        RotationState state = state(RoundPhase.INTERMISSION, 0L).withCandidates(List.of(
                RotationCandidate.pending("al_quds").failed("copy failed:\n  disk full"),
                RotationCandidate.pending("murmansk").prepared(),
                RotationCandidate.pending("kazan")
        ));

        RotationState saved;
        try (SiegeDatabase database = new SiegeDatabase(path)) {
            saved = await(new RotationStateDao(database).save(state));
        }
        try (SiegeDatabase database = new SiegeDatabase(path)) {
            List<RotationCandidate> restored =
                    await(new RotationStateDao(database).load()).orElseThrow().candidates();
            assertEquals(saved.candidates(), restored);
            assertEquals(RotationCandidate.CandidateStatus.FAILED, restored.getFirst().status());
            assertEquals("copy failed: disk full", restored.getFirst().failureReason(),
                    "failure reasons are collapsed to one readable line");
            assertNull(restored.get(1).failureReason());
        }
    }

    @Test
    void plannedAssignmentsReplayInOrderWithTheirPresence() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID spectator = UUID.randomUUID();
        try (SiegeDatabase database = new SiegeDatabase(temporaryDirectory.resolve("roster.db"))) {
            RotationStateDao dao = new RotationStateDao(database);
            await(new MatchScoreDao(database).loadOrCreate(
                    MatchDefinition.rotating("rotation-5", "kazan", "active-kazan", 10_000L)
            ));
            await(dao.replaceRoster("rotation-5", List.of(
                    new RotationStateDao.RosterEntry(first, "First", Team.RED, RoundRole.PLAYER, RosterPresence.PLANNED),
                    new RotationStateDao.RosterEntry(second, "Second", Team.BLUE, RoundRole.PLAYER, RosterPresence.PLANNED),
                    new RotationStateDao.RosterEntry(spectator, "Watcher", null, RoundRole.SPECTATOR, RosterPresence.PLANNED)
            )));

            List<RotationStateDao.RosterEntry> planned = await(dao.rosterAssignments("rotation-5"));
            assertEquals(List.of(first, second, spectator),
                    planned.stream().map(RotationStateDao.RosterEntry::playerId).toList());
            assertEquals(Team.RED, planned.getFirst().team());
            assertNull(planned.getLast().team());

            await(dao.updatePresence("rotation-5", first, RosterPresence.BATTLEFIELD));
            await(dao.updatePresence("rotation-5", second, RosterPresence.LOBBY));
            List<RotationStateDao.RosterEntry> after = await(dao.rosterAssignments("rotation-5"));
            assertEquals(RosterPresence.BATTLEFIELD, after.getFirst().presence());
            assertEquals(RosterPresence.LOBBY, after.get(1).presence());
        }
    }

    @Test
    void abortingAMatchTransfersItsWholeRosterIntoTheQueue() throws Exception {
        UUID fighter = UUID.randomUUID();
        UUID spectator = UUID.randomUUID();
        try (SiegeDatabase database = new SiegeDatabase(temporaryDirectory.resolve("transfer.db"))) {
            RotationStateDao dao = new RotationStateDao(database);
            await(new MatchScoreDao(database).loadOrCreate(
                    MatchDefinition.rotating("rotation-6", "kazan", "active-kazan", 10_000L)
            ));
            await(dao.replaceRoster("rotation-6", List.of(
                    new RotationStateDao.RosterEntry(fighter, "Fighter", Team.RED, RoundRole.PLAYER, RosterPresence.BATTLEFIELD),
                    new RotationStateDao.RosterEntry(spectator, "Watcher", null, RoundRole.SPECTATOR, RosterPresence.BATTLEFIELD)
            )));

            assertEquals(2, await(dao.transferRosterToQueue("rotation-6")).size());

            assertEquals(
                    List.of(fighter, spectator),
                    await(dao.queuedPlayers()).stream()
                            .map(woo.siegePlugin.round.QueuedPlayer::playerId)
                            .sorted(java.util.Comparator.comparing(
                                    id -> id.equals(fighter) ? 0 : 1
                            ))
                            .toList()
            );
            assertEquals(RoundRole.SPECTATOR, await(dao.queuedPlayers()).stream()
                    .filter(value -> value.playerId().equals(spectator))
                    .findFirst().orElseThrow().role());
        }
    }

    private static RotationState state(RoundPhase phase, long revision) {
        return new RotationState(
                phase, 1L, revision, null, null, null, null, null, null, null, List.of()
        );
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
}
