package woo.siegePlugin.round;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import woo.siegePlugin.persistence.MatchDefinition;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.MatchStatus;
import woo.siegePlugin.persistence.ScoreReason;
import woo.siegePlugin.team.Team;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationCoordinatorTest {

    @TempDir Path directory;

    // ------------------------------------------------------------- bootstrap

    @Test
    void firstStartArchivesTheEndlessMatchAndOpensIntermissionRatherThanPlayingIt() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            seedLegacyMatch(rig);
            UUID fighter = rig.audience.join("Fighter");

            rig.coordinator().start();
            rig.settle();

            assertEquals(MatchStatus.LEGACY, rig.scoreDao.load("eternal-1").get(5, TimeUnit.SECONDS).status(),
                    "the endless match must be archived, not replayed");
            assertTrue(rig.broadcastContains("Preparing next map"));
            // Nobody was queued, so the first clean map may publish straight
            // away — but it must be a configured map, never the old boot world.
            assertEquals(1, rig.published.size());
            assertNotEquals("legacy", rig.published.getFirst().map().id());
            assertTrue(rig.audience.isInLobby(fighter),
                    "an unqueued player is evacuated, not dragged into the new round");
            assertTrue(rig.audience.launchedFighters.isEmpty());
        }
    }

    @Test
    void firstStartWithNoEnabledMapEntersLobbyRecoveryInsteadOfARound() throws Exception {
        try (RotationTestHarness rig = rig()) {
            UUID fighter = rig.audience.join("Fighter");

            rig.coordinator().start();
            rig.settle();

            assertEquals(RoundPhase.RECOVERY, rig.rounds.phase());
            assertTrue(rig.audience.isInLobby(fighter));
            assertTrue(rig.broadcastContains("needs administrator recovery"));
            assertTrue(rig.published.isEmpty());
        }
    }

    // ------------------------------------------------------------- activation

    @Test
    void bothGatesReadyPublishesOneBalancedRound() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            UUID red = rig.audience.join("A");
            UUID blue = rig.audience.join("B");
            rig.startAndQueue(red, blue);
            rig.tick();

            assertEquals(1, rig.published.size());
            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
            assertEquals(2, rig.audience.launchedFighters.size());
            assertNotEquals(
                    rig.audience.launchedFighters.get(red),
                    rig.audience.launchedFighters.get(blue),
                    "two fighters must be split across the two sides"
            );
            assertEquals(RoundPhase.ACTIVE, rig.durableState().phase());
        }
    }

    @Test
    void onePlayerRefusingToLeaveDoesNotHoldTheRoundInIntermissionForever() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            UUID stuck = rig.audience.join("Stuck");
            UUID willing = rig.audience.join("Willing");
            rig.startHoldingPreparation(stuck, willing);
            // The stuck player leaves the lobby and can never be teleported back.
            rig.audience.inLobby.remove(stuck);
            rig.audience.lobbyTeleportFails.add(stuck);
            rig.releasePreparation();

            rig.tick();
            assertEquals(RoundPhase.INTERMISSION, rig.rounds.phase(), "the gate holds before the deadline");

            rig.advance(Duration.ofSeconds(41));
            rig.tick();

            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase(), "the deadline must release the gate");
            assertTrue(rig.audience.launchedFighters.containsKey(willing));
            assertFalse(rig.audience.launchedFighters.containsKey(stuck),
                    "an unevacuated player is excluded from the launch, not carried into it");
        }
    }

    @Test
    void aFailedTownyLaunchLeavesEveryoneElseInTheRound() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            UUID broken = rig.audience.join("Broken");
            UUID first = rig.audience.join("First");
            UUID second = rig.audience.join("Second");
            rig.audience.launchThrows.add(broken);

            rig.startAndQueue(broken, first, second);
            rig.tick();

            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
            assertEquals(2, rig.audience.launchedFighters.size());
            assertFalse(rig.audience.launchedFighters.containsKey(broken));
            assertTrue(rig.audience.messages.getOrDefault(broken, List.of()).stream()
                    .anyMatch(line -> line.contains("could not be moved into the new siege")));
            assertEquals(2, rig.roster.all().size(), "only launched players are rostered");
        }
    }

    @Test
    void countdownRemindersUseChatAtCheckpointsAndTheActionBarEverySecond() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            UUID waiting = rig.audience.join("Waiting");
            rig.startHoldingPreparation(waiting);
            rig.audience.inLobby.remove(waiting);
            rig.releasePreparation();

            rig.advance(Duration.ofSeconds(10));
            rig.tick();
            assertEquals("Lobby transfer in 30s", rig.audience.lastActionBar.get(waiting));
            assertTrue(rig.broadcastContains("automatic transfer in 30 seconds"));

            rig.advance(Duration.ofSeconds(5));
            rig.tick();
            assertEquals("Lobby transfer in 25s", rig.audience.lastActionBar.get(waiting));
            assertFalse(rig.broadcastContains("automatic transfer in 25 seconds"),
                    "chat carries only the three checkpoints");
        }
    }

    // -------------------------------------------------------------- fallbacks

    @Test
    void aFailedCandidateFallsThroughToTheNextAndRecordsWhy() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk", "al_quds")) {
            UUID waiting = rig.audience.join("Waiting");
            rig.startHoldingPreparation(waiting);
            rig.audience.inLobby.remove(waiting);
            rig.releasePreparation();

            String firstTried = rig.worlds.loaded.getFirst();
            RotationState state = rig.durableState();
            assertEquals(RotationCandidate.CandidateStatus.PREPARED,
                    state.candidates().stream()
                            .filter(candidate -> candidate.mapId().equals(firstTried))
                            .findFirst().orElseThrow().status());
        }
    }

    @Test
    void everyCandidateFailingEndsInRecoveryWithReasonsRecorded() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            rig.worlds.loadFails.addAll(List.of("kazan", "murmansk"));

            rig.coordinator().start();
            rig.settle();

            assertEquals(RoundPhase.RECOVERY, rig.rounds.phase());
            assertTrue(rig.published.isEmpty());
            RotationState state = rig.durableState();
            assertTrue(state.candidates().stream().allMatch(
                    candidate -> candidate.status() == RotationCandidate.CandidateStatus.FAILED
            ));
            assertTrue(state.candidates().stream().allMatch(candidate -> candidate.failureReason() != null));
        }
    }

    @Test
    void aMapFailingLoadedCopyAdmissionIsRejectedAndItsCopyRemoved() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            rig.worlds.runtimeProblems.put("kazan", List.of("red-spawn has no solid footing"));
            rig.worlds.runtimeProblems.put("murmansk", List.of("capture-point is inside a hazard"));

            rig.coordinator().start();
            rig.settle();

            assertEquals(RoundPhase.RECOVERY, rig.rounds.phase());
            assertEquals(2, rig.worlds.unloaded.size(), "each rejected copy must be cleaned up");
            assertTrue(rig.durableState().candidates().stream()
                    .anyMatch(candidate -> candidate.failureReason().contains("solid footing")));
        }
    }

    @Test
    void aStaticallyInvalidMapIsNeverCopied() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            rig.worlds.staticProblems.put("kazan", List.of("template folder 'kazan' is missing"));

            rig.coordinator().start();
            rig.settle();

            assertFalse(rig.worlds.loaded.contains("kazan"), "an inadmissible map must not be copied at all");
            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
        }
    }

    // --------------------------------------------------------- stale attempts

    @Test
    void aLateCopyFromASupersededAttemptOnlyDeletesItselfAndNeverActivates() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            rig.worlds.holdAllLoads = true;
            rig.coordinator().start();
            rig.settle();
            assertEquals(RoundPhase.INTERMISSION, rig.rounds.phase());

            // An operator retries while the first copy is still in flight.
            assertTrue(rig.coordinator().retry(null));
            rig.settle();

            // The abandoned first copy lands after the retry superseded it.
            rig.worlds.releaseNextLoad("siege-stale-");
            rig.settle();
            rig.tick();
            assertEquals(0, rig.published.size(),
                    "a stale completion must not publish a round");

            // The retry's own copy then lands and publishes normally.
            rig.worlds.releaseNextLoad("siege-retry-");
            rig.settle();
            rig.tick();
            int publishedAfterRetry = rig.published.size();
            assertEquals(1, publishedAfterRetry);

            assertEquals(publishedAfterRetry, rig.published.size(),
                    "a stale completion must not publish another round");
            assertTrue(
                    rig.worlds.unloaded.stream().anyMatch(name -> name.startsWith("siege-stale-"))
                            || rig.worlds.discarded.stream().anyMatch(name -> name.startsWith("siege-stale-")),
                    "the abandoned copy must be cleaned up: "
                            + rig.worlds.unloaded + " / " + rig.worlds.discarded
            );
        }
    }

    @Test
    void preparationExceedingItsTimeoutIsAbandonedInsteadOfHangingTheServer() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            rig.worlds.holdAllLoads = true;

            rig.coordinator().start();
            rig.settle();
            assertEquals(RoundPhase.INTERMISSION, rig.rounds.phase());

            rig.advance(Duration.ofSeconds(301));
            rig.tick();

            assertEquals(RoundPhase.RECOVERY, rig.rounds.phase());
            assertTrue(rig.published.isEmpty());
        }
    }

    // ------------------------------------------------------------- completion

    @Test
    void completionAnnouncesOnceAndCarriesEveryoneIntoTheNextIntermission() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            UUID first = rig.audience.join("A");
            UUID second = rig.audience.join("B");
            rig.startAndQueue(first, second);
            rig.tick();
            String matchId = rig.published.getFirst().matchId();

            rig.stats.bind(matchId);
            rig.stats.recordKill(matchId, first, "A");
            var completed = rig.scoreDao.awardWithCutoff(
                    matchId, Team.RED, 100L, ScoreReason.BANNER_CONTROL, rig.stats.snapshot()
            ).get(5, TimeUnit.SECONDS);
            assertTrue(completed.completedNow());

            rig.audience.broadcasts.clear();
            rig.coordinator().onMatchCompleted(completed.match());
            rig.settle();

            assertEquals(1, rig.broadcasts().stream()
                    .filter(line -> line.contains("Match Complete")).count());
            assertTrue(rig.broadcastContains("Kills MVP: A (1 kills)"), rig.broadcasts().toString());
            assertTrue(rig.broadcastContains("[Go to Lobby]"));
            assertTrue(rig.audience.discardedInventories.containsAll(List.of(first, second)));
            assertEquals(RoundPhase.INTERMISSION, rig.rounds.phase());
        }
    }

    @Test
    void aCategoryNobodyContributedToReadsNoneRatherThanBeingOmitted() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            UUID fighter = rig.audience.join("A");
            rig.startAndQueue(fighter);
            rig.tick();
            String matchId = rig.published.getFirst().matchId();

            var completed = rig.scoreDao.awardWithCutoff(
                    matchId, Team.RED, 100L, ScoreReason.BANNER_CONTROL, List.of()
            ).get(5, TimeUnit.SECONDS);
            rig.audience.broadcasts.clear();
            rig.coordinator().onMatchCompleted(completed.match());
            rig.settle();

            assertTrue(rig.broadcastContains("Kills MVP: None — 0"));
            assertTrue(rig.broadcastContains("Overall MVP: None"));
        }
    }

    @Test
    void adminEndingARoundStillPreparesAndLaunchesTheNextMap() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            UUID fighter = rig.audience.join("A");
            rig.startAndQueue(fighter);
            rig.tick();

            assertTrue(rig.coordinator().endActive());
            rig.settle();
            rig.advance(Duration.ofSeconds(41));
            rig.tick();

            assertEquals(RoundPhase.ACTIVE, rig.rounds.phase());
            assertEquals(2, rig.published.size(), "admin end must flow into the next automatic round");
            assertTrue(rig.broadcastContains("Preparing the next map"));
        }
    }

    @Test
    void theOldWorldIsEnrolledForCleanupAndRetriedWhilePlayersRemain() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            UUID fighter = rig.audience.join("A");
            rig.startAndQueue(fighter);
            rig.tick();
            String firstWorld = rig.published.getFirst().world().getName();
            rig.worlds.unloadFails.add(firstWorld);

            String matchId = rig.published.getFirst().matchId();
            var completed = rig.scoreDao.awardWithCutoff(
                    matchId, Team.RED, 100L, ScoreReason.BANNER_CONTROL, List.of()
            ).get(5, TimeUnit.SECONDS);
            rig.coordinator().onMatchCompleted(completed.match());
            rig.settle();
            // The fighter is still on the old battlefield, so the next round
            // waits for the forced-lobby deadline before launching.
            rig.advance(Duration.ofSeconds(41));
            rig.tick();

            assertEquals(2, rig.published.size(), "the next round still starts");
            assertEquals(
                    List.of(firstWorld),
                    await(rig.cleanupDao.all()).stream()
                            .map(pending -> pending.worldName())
                            .toList(),
                    "an unremovable world stays queued for a later retry"
            );
        }
    }

    // ---------------------------------------------------------------- joining

    @Test
    void joinIsPhaseExplicit() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            UUID player = rig.audience.join("A");
            rig.worlds.holdAllLoads = true;
            rig.coordinator().start();
            rig.settle();

            assertEquals(RotationCoordinator.JoinOutcome.QUEUED, rig.coordinator().requestJoin(player));
            rig.settle();
            rig.worlds.holdAllLoads = false;
            rig.worlds.releaseNextLoad("siege-active-");
            rig.settle();
            rig.tick();

            UUID latecomer = rig.audience.join("Late");
            assertEquals(RotationCoordinator.JoinOutcome.JOINED, rig.coordinator().requestJoin(latecomer));
            rig.settle();
            assertTrue(rig.audience.launchedFighters.containsKey(latecomer));
            assertEquals(RosterPresence.BATTLEFIELD,
                    rig.roster.presenceOf(latecomer).orElseThrow().presence());
        }
    }

    @Test
    void aMidRoundJoinTakesTheSmallerBattlefieldSide() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            UUID first = rig.audience.join("A");
            UUID second = rig.audience.join("B");
            UUID third = rig.audience.join("C");
            rig.startAndQueue(first, second);
            rig.tick();

            // One side loses a player to the lobby, so the next join must even it up.
            rig.coordinator().markReturnedToLobby(first);
            Team leftBehind = rig.audience.launchedFighters.get(second);
            rig.coordinator().requestJoin(third);
            rig.settle();

            assertEquals(leftBehind.opponent(), rig.audience.launchedFighters.get(third));
        }
    }

    @Test
    void returningToTheLobbyKeepsTheRosterEntryButDropsBattlefieldPresence() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            UUID fighter = rig.audience.join("A");
            rig.startAndQueue(fighter);
            rig.tick();

            rig.coordinator().markReturnedToLobby(fighter);
            rig.settle();

            assertEquals(RosterPresence.LOBBY, rig.roster.presenceOf(fighter).orElseThrow().presence());
            assertEquals(RosterPresence.LOBBY, await(
                    rig.stateDao.rosterAssignments(rig.published.getFirst().matchId())
            ).getFirst().presence());
        }
    }

    @Test
    void reconnectingRestoresABattlefieldFighterAndLeavesAVoluntaryLobbyChoiceAlone() throws Exception {
        try (RotationTestHarness rig = rig("kazan")) {
            UUID stayer = rig.audience.join("Stayer");
            UUID leaver = rig.audience.join("Leaver");
            rig.startAndQueue(stayer, leaver);
            rig.tick();
            rig.coordinator().markReturnedToLobby(leaver);
            rig.settle();

            rig.audience.launchedFighters.clear();
            rig.coordinator().handleJoin(stayer);
            rig.coordinator().handleJoin(leaver);
            rig.settle();

            assertTrue(rig.audience.launchedFighters.containsKey(stayer));
            assertFalse(rig.audience.launchedFighters.containsKey(leaver));
            assertTrue(rig.audience.isInLobby(leaver));
        }
    }

    // ------------------------------------------------------------ persistence

    @Test
    void statusReportsCandidateOutcomesForAnOperator() throws Exception {
        try (RotationTestHarness rig = rig("kazan", "murmansk")) {
            rig.worlds.loadFails.add("kazan");
            rig.worlds.loadFails.add("murmansk");
            rig.coordinator().start();
            rig.settle();

            List<String> lines = rig.coordinator().statusLines();
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("Phase RECOVERY")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("(FAILED: copy failed for")));
        }
    }

    private RotationTestHarness rig(String... maps) {
        return new RotationTestHarness(
                directory.resolve("rotation-" + UUID.randomUUID() + ".db"), List.of(maps)
        );
    }

    /** Creates the pre-rotation endless match this build is expected to archive. */
    private static void seedLegacyMatch(RotationTestHarness rig) throws Exception {
        await(new MatchScoreDao(rig.database).loadOrCreate(
                MatchDefinition.eternalForWorld("siegeworld")
        ));
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
}
