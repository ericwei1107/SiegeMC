package woo.siegePlugin.round;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import woo.siegePlugin.map.ActiveMapWorld;
import woo.siegePlugin.map.MapBounds;
import woo.siegePlugin.map.MapManifest;
import woo.siegePlugin.map.MapPoint;
import woo.siegePlugin.map.MapValidator;
import woo.siegePlugin.map.SiegeMap;
import woo.siegePlugin.persistence.MatchDefinition;
import woo.siegePlugin.persistence.MatchRecord;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.MatchStatsDao;
import woo.siegePlugin.persistence.RotationStateDao;
import woo.siegePlugin.persistence.WorldCleanupDao;
import woo.siegePlugin.stats.MatchStatsTracker;
import woo.siegePlugin.stats.MvpCalculator;
import woo.siegePlugin.stats.MvpResults;
import woo.siegePlugin.stats.PlayerMatchStats;
import woo.siegePlugin.team.Team;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sole owner of round completion, intermission, clean-copy preparation,
 * atomic service rebinding, balanced launch, and durable recovery.
 *
 * <p>Two invariants carry most of the safety here:</p>
 *
 * <ul>
 *   <li><b>Attempt tokens.</b> Every preparation and activation runs under a
 *       token. A retry, a shutdown, or entering recovery invalidates earlier
 *       tokens, and a completion arriving under a stale token may do nothing
 *       except delete the world copy it created. This is what stops a slow map
 *       copy from activating over a newer decision.</li>
 *   <li><b>Compare-and-set persistence.</b> Lifecycle writes carry the revision
 *       they were derived from. Nothing — a phase change, a queue entry, a
 *       roster, an abort, or the active publication — is acted on until its
 *       database write has succeeded.</li>
 * </ul>
 */
public final class RotationCoordinator {

    private final Logger logger;
    private final RoundScheduler scheduler;
    private final RoundAudience audience;
    private final WorldLifecycle worlds;
    private final ActiveRoundProvider rounds;
    private final RoundRoster roster;
    private final RotationStateDao stateDao;
    private final MatchScoreDao scoreDao;
    private final MatchStatsDao statsDao;
    private final WorldCleanupDao cleanupDao;
    private final Supplier<MapManifest> manifestLoader;
    private final BiFunction<String, MapBounds, List<String>> supplyValidator;
    private final RoundScoring scoring;
    private final MatchStatsTracker stats;
    private final RotationSettings settings;
    private final long scoreLimit;
    private final Consumer<ActiveRoundContext> contextBinder;
    private final Random random;

    private final Map<UUID, QueuedPlayer> queue = new LinkedHashMap<>();
    private final Set<UUID> excludedFromLaunch = new HashSet<>();

    private MapManifest manifest;
    private RotationState durableState;
    private PreparedRound prepared;
    private ActiveMapWorld disposableActiveWorld;
    private Instant preparationDeadline;
    private boolean evacuationAttempted;
    private boolean stopped;
    private int lastLobbyCountdownSecond = Integer.MIN_VALUE;
    private long attemptSequence;
    private long activeAttempt;

    public RotationCoordinator(
            Logger logger,
            RoundScheduler scheduler,
            RoundAudience audience,
            WorldLifecycle worlds,
            ActiveRoundProvider rounds,
            RoundRoster roster,
            RotationStateDao stateDao,
            MatchScoreDao scoreDao,
            MatchStatsDao statsDao,
            WorldCleanupDao cleanupDao,
            Supplier<MapManifest> manifestLoader,
            BiFunction<String, MapBounds, List<String>> supplyValidator,
            RoundScoring scoring,
            MatchStatsTracker stats,
            RotationSettings settings,
            long scoreLimit,
            Consumer<ActiveRoundContext> contextBinder,
            Random random
    ) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.audience = audience;
        this.worlds = worlds;
        this.rounds = rounds;
        this.roster = roster;
        this.stateDao = stateDao;
        this.scoreDao = scoreDao;
        this.statsDao = statsDao;
        this.cleanupDao = cleanupDao;
        this.manifestLoader = manifestLoader;
        this.supplyValidator = supplyValidator;
        this.scoring = scoring;
        this.stats = stats;
        this.settings = settings;
        this.scoreLimit = scoreLimit;
        this.contextBinder = contextBinder;
        this.random = random;
        this.manifest = manifestLoader.get();
    }

    // ---------------------------------------------------------------- startup

    /** Establishes or resumes durable ownership. */
    public void start() {
        stateDao.load().whenComplete((loaded, failure) -> onServerThread(() -> {
            if (failure != null) {
                enterRecovery("Could not load durable rotation state", failure);
                return;
            }
            reportUntrackedWorlds();
            resumePendingCleanups();
            loaded.ifPresentOrElse(this::hydrate, this::bootstrapFirstRotation);
        }));
    }

    public void stop() {
        stopped = true;
        scheduler.stopTicking();
        invalidateAttempts();
    }

    /**
     * First boot after rotation was introduced. The endless match is archived
     * with no ceremony and rotation begins at intermission, so nobody plays a
     * transitional round on the old boot world.
     */
    private void bootstrapFirstRotation() {
        scoreDao.archiveLegacyMatch().whenComplete((ignored, failure) -> onServerThread(() -> {
            if (failure != null) {
                enterRecovery("Could not archive the pre-rotation match", failure);
                return;
            }
            logger.info("Archived the pre-rotation endless match; rotation starts at intermission.");
            durableState = new RotationState(
                    RoundPhase.INTERMISSION, 1L, 0L,
                    null, null, null, null, null, null,
                    scheduler.now().plus(settings.forcedLobbyDelay()),
                    List.of()
            );
            rounds.restore(RoundPhase.INTERMISSION, null);
            evacuateEveryone();
            beginPreparation(null, "Siege rotation is starting.");
        }));
    }

    private void hydrate(RotationState state) {
        durableState = state;
        stateDao.queuedPlayers().whenComplete((loadedQueue, failure) -> onServerThread(() -> {
            if (failure != null) {
                enterRecovery("Could not restore durable participants", failure);
                return;
            }
            loadedQueue.forEach(value -> queue.put(value.playerId(), value));
            reconcile(state);
        }));
    }

    /**
     * Reconciles durable phase against the stored match row. The match table is
     * the authority on whether a round actually ended, because its completion
     * transaction is what closed it.
     */
    private void reconcile(RotationState state) {
        if (state.currentMatchId() == null) {
            resumeWithoutMatch(state);
            return;
        }
        scoreDao.load(state.currentMatchId()).whenComplete((match, failure) -> onServerThread(() -> {
            if (failure != null) {
                enterRecovery("Could not read the recorded match", failure);
                return;
            }
            switch (match.status()) {
                case ACTIVE -> resumeActiveMatch(state, match);
                case COMPLETED -> continueCompletedMatch(state, match);
                case ABORTED, LEGACY -> transferRosterAndPrepare(
                        state, "The previous siege was abandoned with no winner."
                );
            }
        }));
    }

    private void resumeWithoutMatch(RotationState state) {
        if (state.phase() == RoundPhase.RECOVERY) {
            rounds.restore(RoundPhase.RECOVERY, null);
            evacuateEveryone();
            announceRecovery();
            return;
        }
        rounds.restore(RoundPhase.INTERMISSION, null);
        evacuateEveryone();
        resumePreparedOrRestart(state);
    }

    private void resumeActiveMatch(RotationState state, MatchRecord match) {
        if (state.phase() == RoundPhase.ACTIVATING) {
            resumeActivating(state, match);
            return;
        }
        if (state.phase() != RoundPhase.ACTIVE && state.phase() != RoundPhase.COMPLETING) {
            // Durable state says intermission or recovery while the match row is
            // still open: the match never really closed, so abort it cleanly.
            abortAndPrepare(state, "Recorded round no longer matches its durable phase", null);
            return;
        }
        SiegeMap map = manifest.find(state.currentMapId()).orElse(null);
        if (map == null || state.currentRuntimeWorld() == null) {
            abortAndPrepare(state, "Recorded active map is no longer enabled", null);
            return;
        }
        long attempt = beginAttempt();
        worlds.resume(map, state.currentRuntimeWorld()).whenComplete((loaded, failure) -> onServerThread(() -> {
            if (!isCurrent(attempt)) {
                discardStale(loaded);
                return;
            }
            if (failure != null) {
                abortAndPrepare(state, "Recorded active world could not be reopened", failure);
                return;
            }
            ActiveRoundContext context = context(state.generation(), match.matchId(), loaded);
            disposableActiveWorld = loaded;
            reopenScoring(state, context, match);
        }));
    }

    /** Restores stats, roster, and scoring for a match that is genuinely still open. */
    private void reopenScoring(RotationState state, ActiveRoundContext context, MatchRecord match) {
        statsDao.load(match.matchId())
                .thenCombine(stateDao.rosterAssignments(match.matchId()), RecoveredRound::new)
                .whenComplete((recovered, failure) -> onServerThread(() -> {
                    if (failure != null) {
                        abortAndPrepare(state, "Could not recover the active round's records", failure);
                        return;
                    }
                    try {
                        contextBinder.accept(context);
                    } catch (RuntimeException bindFailure) {
                        abortAndPrepare(state, "Could not rebind services to the recovered map", bindFailure);
                        return;
                    }
                    stats.restore(match.matchId(), recovered.stats());
                    bindRoster(match.matchId(), recovered.roster());
                    scoring.activateMatch(definitionFor(context), scoreFailure -> {
                        if (scoreFailure != null) {
                            abortAndPrepare(state, "Could not reopen the recovered score", scoreFailure);
                            return;
                        }
                        rounds.restore(RoundPhase.ACTIVE, context);
                        writeState(state.withPhase(RoundPhase.ACTIVE), ignored -> {
                        });
                        logger.info("Resumed active siege " + match.matchId() + " on " + context.map().id() + ".");
                    });
                }));
    }

    /** A match the completion transaction already closed: run intermission, never a second ceremony. */
    private void continueCompletedMatch(RotationState state, MatchRecord match) {
        rounds.restore(RoundPhase.COMPLETING, null);
        evacuateEveryone();
        boolean alreadyAnnounced = state.phase() == RoundPhase.INTERMISSION
                || state.phase() == RoundPhase.RECOVERY
                || state.phase() == RoundPhase.ACTIVATING;
        stateDao.transferRosterToQueue(match.matchId())
                .thenCombine(statsDao.load(match.matchId()), RecoveredCeremony::new)
                .whenComplete((recovered, failure) -> onServerThread(() -> {
                    if (failure != null) {
                        enterRecovery("Could not recover the completed match roster", failure);
                        return;
                    }
                    recovered.roster().forEach(value -> {
                        queue.putIfAbsent(value.playerId(), value);
                        audience.discardStoredRoundInventory(value.playerId());
                    });
                    rounds.restore(RoundPhase.INTERMISSION, null);
                    if (state.phase() == RoundPhase.ACTIVATING) {
                        resumeActivatingAfterCompletion(state);
                        return;
                    }
                    beginIntermission(
                            match,
                            alreadyAnnounced ? null : MvpCalculator.calculate(recovered.stats())
                    );
                }));
    }

    private void resumeActivatingAfterCompletion(RotationState state) {
        // The winner's match is closed but a next match was already being
        // activated; resume that rather than announcing anything again.
        resumePreparedOrRestart(state);
    }

    /**
     * An unrecoverable open match: close it as ABORTED, move its whole roster
     * into the intermission queue, and prepare the next map. Nobody is lost and
     * no winner is invented. Recovery is only entered if the abort itself fails.
     */
    /**
     * Picks up an interrupted intermission. A prepared copy recorded before the
     * restart is reopened and reused; otherwise preparation simply starts again.
     */
    private void resumePreparedOrRestart(RotationState state) {
        SiegeMap map = state.preparedMapId() == null
                ? null
                : manifest.find(state.preparedMapId()).orElse(null);
        if (map == null || state.preparedRuntimeWorld() == null) {
            durableState = state.withPrepared(null, null);
            beginPreparation(null, null);
            return;
        }
        long attempt = beginAttempt();
        worlds.resume(map, state.preparedRuntimeWorld()).whenComplete((loaded, failure) -> onServerThread(() -> {
            if (!isCurrent(attempt)) {
                discardStale(loaded);
                return;
            }
            if (failure != null) {
                logger.log(
                        Level.WARNING,
                        "Discarding an incomplete prepared map copy; preparing again.",
                        unwrap(failure)
                );
                durableState = state.withPrepared(null, null);
                beginPreparation(null, null);
                return;
            }
            List<String> problems = worlds.loadedCopyProblems(loaded);
            if (!problems.isEmpty()) {
                logger.warning("Recovered prepared copy of " + map.id() + " is unusable: "
                        + String.join("; ", problems));
                enrollAndDiscard(loaded);
                durableState = state.withPrepared(null, null);
                beginPreparation(null, null);
                return;
            }
            long generation = state.generation() + 1L;
            prepared = new PreparedRound(generation, "rotation-" + generation, loaded);
            durableState = state.withPrepared(map.id(), loaded.world().getName());
            evacuationAttempted = false;
            excludedFromLaunch.clear();
            writeState(durableState, saved -> {
                if (!isCurrent(attempt)) {
                    return;
                }
                scheduler.startTicking(this::tick);
                tryActivateIfReady();
            });
        }));
    }

    private void abortAndPrepare(RotationState state, String message, Throwable failure) {
        if (failure == null) {
            logger.severe(message);
        } else {
            logger.log(Level.SEVERE, message, unwrap(failure));
        }
        if (state.currentMatchId() == null) {
            enterRecovery(message, failure);
            return;
        }
        scoreDao.abort(state.currentMatchId()).whenComplete((aborted, abortFailure) -> onServerThread(() -> {
            if (abortFailure != null) {
                enterRecovery("Could not abort an unrecoverable match", abortFailure);
                return;
            }
            transferRosterAndPrepare(state, "The previous siege was abandoned with no winner.");
        }));
    }

    private void transferRosterAndPrepare(RotationState state, String announcement) {
        stateDao.transferRosterToQueue(state.currentMatchId()).whenComplete((transferred, failure) ->
                onServerThread(() -> {
                    if (failure != null) {
                        enterRecovery("Could not transfer an aborted roster to the queue", failure);
                        return;
                    }
                    transferred.forEach(value -> {
                        queue.putIfAbsent(value.playerId(), value);
                        audience.discardStoredRoundInventory(value.playerId());
                    });
                    clearRoundBindings();
                    rounds.restore(RoundPhase.INTERMISSION, null);
                    evacuateEveryone();
                    durableState = new RotationState(
                            RoundPhase.INTERMISSION, state.generation(), state.revision(),
                            null, null, null, state.currentMapId(), null, null,
                            scheduler.now().plus(settings.forcedLobbyDelay()), List.of()
                    );
                    beginPreparation(null, announcement);
                }));
    }

    // ------------------------------------------------------------- completion

    public void onMatchCompleted(MatchRecord completed) {
        if (!rounds.transition(RoundPhase.ACTIVE, RoundPhase.COMPLETING)) {
            return;
        }
        invalidateAttempts();
        captureOnlineParticipants(completed.matchId());
        MvpResults mvp = MvpCalculator.calculate(stats.snapshot());
        // The winning transaction moved durable state to COMPLETING and bumped
        // its revision, so the cached copy here is already one behind. Reload
        // before the intermission write, or it would lose its compare-and-set.
        stateDao.load()
                .thenCombine(stateDao.transferRosterToQueue(completed.matchId()), CompletedRound::new)
                .whenComplete((recovered, failure) -> onServerThread(() -> {
                    if (failure != null) {
                        enterRecovery("Could not recover the completed match roster", failure);
                        evacuateEveryone();
                        return;
                    }
                    recovered.state().ifPresent(reloaded -> durableState = reloaded);
                    recovered.roster().forEach(value -> queue.putIfAbsent(value.playerId(), value));
                    queue.values().forEach(value -> audience.discardStoredRoundInventory(value.playerId()));
                    clearRoundBindings();
                    rounds.restore(RoundPhase.INTERMISSION, null);
                    beginIntermission(completed, mvp);
                }));
    }

    private void beginIntermission(MatchRecord completed, MvpResults mvp) {
        durableState = new RotationState(
                RoundPhase.INTERMISSION,
                durableState.generation(),
                durableState.revision(),
                null,
                null,
                null,
                completed.mapId(),
                null,
                null,
                scheduler.now().plus(settings.forcedLobbyDelay()),
                List.of()
        );
        beginPreparation(mvp == null ? null : results(completed, mvp), null);
    }

    /**
     * Chooses the candidate order, persists it, announces, and starts preparing.
     * The results card is passed in already rendered so the announcement can
     * name the map that was actually selected.
     */
    private void beginPreparation(List<Component> ceremony, String announcement) {
        List<SiegeMap> candidates = candidates(null);
        durableState = durableState.withCandidates(
                candidates.stream().map(SiegeMap::id).map(RotationCandidate::pending).toList()
        );
        evacuationAttempted = false;
        excludedFromLaunch.clear();
        lastLobbyCountdownSecond = Integer.MIN_VALUE;

        writeState(durableState, saved -> {
            if (announcement != null) {
                audience.broadcast(Component.text(announcement, NamedTextColor.RED));
            }
            String nextMap = candidates.isEmpty() ? "unavailable" : candidates.getFirst().displayName();
            if (ceremony != null) {
                ceremony.forEach(audience::broadcast);
                audience.broadcast(Component.text(
                        "Preparing next map: " + nextMap + " — it will be loaded shortly.",
                        NamedTextColor.YELLOW
                ));
                audience.broadcast(lobbyButton());
            } else if (!candidates.isEmpty()) {
                audience.broadcast(Component.text(
                        "Preparing next map: " + nextMap + " — it will be loaded shortly.",
                        NamedTextColor.YELLOW
                ));
                audience.broadcast(lobbyButton());
            }
            if (candidates.isEmpty()) {
                enterRecovery("No enabled map passed validation", null);
                return;
            }
            loadCandidate(beginAttempt(), candidates, 0);
            scheduler.startTicking(this::tick);
        });
    }

    private void captureOnlineParticipants(String matchId) {
        for (RoundRoster.Membership member : roster.all()) {
            if (member.presence() != RosterPresence.BATTLEFIELD) {
                continue;
            }
            QueuedPlayer queued = new QueuedPlayer(
                    member.playerId(), member.role(), QueueSource.PREVIOUS_MATCH
            );
            queue.put(member.playerId(), queued);
        }
    }

    // ------------------------------------------------------------ preparation

    private void loadCandidate(long attempt, List<SiegeMap> candidates, int index) {
        if (!isCurrent(attempt)) {
            return;
        }
        if (index >= candidates.size()) {
            enterRecovery("Every enabled map candidate failed to load", null);
            return;
        }
        SiegeMap candidate = candidates.get(index);
        List<String> staticProblems = staticProblemsFor(candidate);
        if (!staticProblems.isEmpty()) {
            failCandidate(attempt, candidates, index, candidate, String.join("; ", staticProblems), false);
            return;
        }

        preparationDeadline = scheduler.now().plus(settings.preparationTimeout());
        worlds.load(candidate).whenComplete((loaded, failure) -> onServerThread(() -> {
            if (!isCurrent(attempt)) {
                // A newer decision won. This copy exists only because of a
                // superseded attempt, so deleting it is the only thing allowed.
                discardStale(loaded);
                return;
            }
            preparationDeadline = null;
            if (failure != null) {
                failCandidate(
                        attempt, candidates, index, candidate, messageOf(unwrap(failure)), true
                );
                return;
            }
            List<String> runtimeProblems = worlds.loadedCopyProblems(loaded);
            if (!runtimeProblems.isEmpty()) {
                enrollAndDiscard(loaded);
                failCandidate(attempt, candidates, index, candidate, String.join("; ", runtimeProblems), true);
                return;
            }
            promoteCandidate(attempt, candidate, loaded, index > 0);
        }));
    }

    private void failCandidate(
            long attempt,
            List<SiegeMap> candidates,
            int index,
            SiegeMap candidate,
            String reason,
            boolean announce
    ) {
        logger.warning("Map " + candidate.id() + " cannot host a round: " + reason);
        durableState = durableState.withCandidateStatus(candidate.id(),
                RotationCandidate.pending(candidate.id()).failed(reason));
        writeState(durableState, saved -> {
            if (!isCurrent(attempt)) {
                return;
            }
            if (announce) {
                audience.broadcast(Component.text(
                        candidate.displayName() + " could not be prepared. Trying the next map.",
                        NamedTextColor.RED
                ));
            }
            loadCandidate(attempt, candidates, index + 1);
        });
    }

    private void promoteCandidate(long attempt, SiegeMap candidate, ActiveMapWorld loaded, boolean isFallback) {
        long generation = durableState.generation() + 1L;
        PreparedRound next = new PreparedRound(generation, "rotation-" + generation, loaded);
        durableState = durableState
                .withPrepared(candidate.id(), loaded.world().getName())
                .withCandidateStatus(candidate.id(), RotationCandidate.pending(candidate.id()).prepared());
        writeState(durableState, saved -> {
            if (!isCurrent(attempt)) {
                enrollAndDiscard(loaded);
                return;
            }
            prepared = next;
            if (isFallback) {
                audience.broadcast(Component.text(
                        "Fallback map selected: " + candidate.displayName() + ". It will be loaded shortly.",
                        NamedTextColor.GOLD
                ));
            }
            tryActivateIfReady();
        });
    }

    // ------------------------------------------------------------------ ticks

    private void tick() {
        if (stopped) {
            return;
        }
        Instant deadline = durableState == null ? null : durableState.intermissionDeadline();
        if (deadline != null) {
            long millis = Duration.between(scheduler.now(), deadline).toMillis();
            int seconds = millis <= 0L ? 0 : (int) Math.ceil(millis / 1000.0D);
            announceLobbyCountdown(seconds);
            if (seconds == 0 && !evacuationAttempted) {
                forceEvacuationOnce();
            }
        }
        if (preparationDeadline != null && !scheduler.now().isBefore(preparationDeadline)) {
            preparationDeadline = null;
            logger.severe("Map preparation exceeded " + settings.preparationTimeout().toSeconds()
                    + "s; abandoning this attempt.");
            invalidateAttempts();
            enterRecovery("Map preparation timed out", null);
            return;
        }
        tryActivateIfReady();
    }

    /**
     * One forced sweep at the deadline. A player whose teleport fails is
     * excluded from this launch rather than retried forever — otherwise a
     * single stuck player would hold the whole server in intermission.
     */
    private void forceEvacuationOnce() {
        evacuationAttempted = true;
        for (QueuedPlayer queued : List.copyOf(queue.values())) {
            if (!audience.isOnline(queued.playerId()) || audience.isInLobby(queued.playerId())) {
                continue;
            }
            if (!audience.sendToLobby(queued.playerId())) {
                excludedFromLaunch.add(queued.playerId());
                logger.warning("Could not evacuate " + audience.nameOf(queued.playerId())
                        + "; excluding them from this launch and quarantining the old world.");
                audience.message(queued.playerId(), Component.text(
                        "You could not be moved to the lobby. Use /siege join once the next siege begins.",
                        NamedTextColor.RED
                ));
            }
        }
    }

    private void announceLobbyCountdown(int seconds) {
        Component actionBar = seconds == 0
                ? Component.text("Moving you to the lobby…", NamedTextColor.YELLOW)
                : Component.text(
                        "Lobby transfer in " + seconds + "s", NamedTextColor.YELLOW
                );
        for (QueuedPlayer queued : queue.values()) {
            if (audience.isOnline(queued.playerId())) {
                audience.actionBar(queued.playerId(), actionBar);
            }
        }
        if (seconds == lastLobbyCountdownSecond || (seconds != 30 && seconds != 10 && seconds != 5)) {
            return;
        }
        lastLobbyCountdownSecond = seconds;
        audience.broadcast(Component.text(
                "Return to the lobby — automatic transfer in " + seconds + " seconds.",
                NamedTextColor.YELLOW
        ));
    }

    private void tryActivateIfReady() {
        if (prepared == null || rounds.phase() != RoundPhase.INTERMISSION || !lobbyGateOpen()) {
            return;
        }
        scheduler.stopTicking();
        activatePrepared();
    }

    private boolean lobbyGateOpen() {
        if (evacuationAttempted) {
            return true;
        }
        for (QueuedPlayer queued : queue.values()) {
            if (audience.isOnline(queued.playerId()) && !audience.isInLobby(queued.playerId())) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- activation

    private void activatePrepared() {
        if (!rounds.transition(RoundPhase.INTERMISSION, RoundPhase.ACTIVATING)) {
            return;
        }
        long attempt = beginAttempt();
        PreparedRound next = prepared;
        ActiveRoundContext context = context(next.generation(), next.matchId(), next.activeWorld());

        durableState = new RotationState(
                RoundPhase.ACTIVATING, next.generation(), durableState.revision(),
                next.matchId(), context.map().id(), context.world().getName(),
                durableState.previousMapId(), context.map().id(), context.world().getName(),
                null, durableState.candidates()
        );
        writeState(durableState, saved -> {
            if (!isCurrent(attempt)) {
                return;
            }
            try {
                contextBinder.accept(context);
            } catch (RuntimeException bindFailure) {
                discardPreparedAndRecover("Could not rebind services to the prepared map", bindFailure);
                return;
            }
            stats.bind(context.matchId());
            scoring.activateMatch(definitionFor(context), failure -> {
                if (failure != null) {
                    discardPreparedAndRecover("Could not create the next durable match", failure);
                    return;
                }
                planAndLaunch(attempt, context);
            });
        });
    }

    private void resumeActivating(RotationState state, MatchRecord match) {
        SiegeMap map = manifest.find(state.currentMapId()).orElse(null);
        if (map == null || state.currentRuntimeWorld() == null) {
            abortAndPrepare(state, "Incomplete activation has no recoverable prepared world", null);
            return;
        }
        long attempt = beginAttempt();
        worlds.resume(map, state.currentRuntimeWorld()).whenComplete((loaded, failure) -> onServerThread(() -> {
            if (!isCurrent(attempt)) {
                discardStale(loaded);
                return;
            }
            if (failure != null) {
                abortAndPrepare(state, "Incomplete activation world could not be reopened", failure);
                return;
            }
            prepared = new PreparedRound(state.generation(), match.matchId(), loaded);
            ActiveRoundContext context = context(state.generation(), match.matchId(), loaded);
            rounds.restore(RoundPhase.ACTIVATING, null);
            try {
                contextBinder.accept(context);
            } catch (RuntimeException bindFailure) {
                discardPreparedAndRecover("Could not rebind services to the recovered activation", bindFailure);
                return;
            }
            stats.bind(context.matchId());
            scoring.activateMatch(definitionFor(context), scoreFailure -> {
                if (scoreFailure != null) {
                    discardPreparedAndRecover("Incomplete activation score could not be recovered", scoreFailure);
                    return;
                }
                replayPersistedPlan(attempt, context);
            });
        }));
    }

    /**
     * Replays exactly what was persisted before the interruption. A failed read
     * enters recovery rather than reshuffling, because inventing a new plan
     * could move a player who was already launched onto the other team.
     */
    private void replayPersistedPlan(long attempt, ActiveRoundContext context) {
        stateDao.rosterAssignments(context.matchId()).whenComplete((plan, failure) -> onServerThread(() -> {
            if (!isCurrent(attempt)) {
                return;
            }
            if (failure != null) {
                discardPreparedAndRecover(
                        "Could not read the persisted activation plan; refusing to reshuffle it", failure
                );
                return;
            }
            plan.forEach(entry -> queue.putIfAbsent(
                    entry.playerId(), new QueuedPlayer(entry.playerId(), entry.role(), QueueSource.PREVIOUS_MATCH)
            ));
            applyRoster(attempt, context, plan);
        }));
    }

    /** Records the planned assignments before any Towny call, then applies them. */
    private void planAndLaunch(long attempt, ActiveRoundContext context) {
        List<QueuedPlayer> launchable = queue.values().stream()
                .filter(value -> audience.isOnline(value.playerId()))
                .filter(value -> !excludedFromLaunch.contains(value.playerId()))
                .toList();
        List<UUID> fighters = launchable.stream()
                .filter(value -> value.role() == RoundRole.PLAYER)
                .map(QueuedPlayer::playerId)
                .toList();

        List<RotationStateDao.RosterEntry> plan = new ArrayList<>();
        for (RosterBalancer.Assignment assignment : RosterBalancer.balance(fighters, random)) {
            plan.add(entry(assignment.playerId(), assignment.team(), RoundRole.PLAYER, RosterPresence.PLANNED));
        }
        launchable.stream()
                .filter(value -> value.role() == RoundRole.SPECTATOR)
                .forEach(value -> plan.add(
                        entry(value.playerId(), null, RoundRole.SPECTATOR, RosterPresence.PLANNED)
                ));

        stateDao.replaceRoster(context.matchId(), plan).whenComplete((ignored, failure) -> onServerThread(() -> {
            if (!isCurrent(attempt)) {
                return;
            }
            if (failure != null) {
                abortActivation(context, "Could not durably record the next match roster", failure);
                return;
            }
            applyRoster(attempt, context, plan);
        }));
    }

    /**
     * Applies one plan through Towny. Each player is launched inside its own
     * guard so a single Towny or teleport failure cannot strand the round in
     * ACTIVATING; the survivors are still balanced because each fighter is
     * assigned to whichever side has actually succeeded fewer times.
     */
    private void applyRoster(long attempt, ActiveRoundContext context, List<RotationStateDao.RosterEntry> plan) {
        List<RotationStateDao.RosterEntry> launched = new ArrayList<>();
        int red = 0;
        int blue = 0;
        for (RotationStateDao.RosterEntry planned : plan) {
            if (!audience.isOnline(planned.playerId())) {
                requeue(planned);
                continue;
            }
            if (planned.role() == RoundRole.SPECTATOR) {
                if (guarded(() -> audience.launchSpectator(planned.playerId(), context), planned)) {
                    launched.add(planned.withPresence(RosterPresence.BATTLEFIELD));
                } else {
                    reportLaunchFailure(planned);
                }
                continue;
            }
            Team team = RosterBalancer.smallerSide(
                    red, blue, planned.team() == null ? Team.RED : planned.team()
            );
            if (!guarded(() -> audience.launchFighter(planned.playerId(), team, context), planned)) {
                reportLaunchFailure(planned);
                continue;
            }
            if (team == Team.RED) red++;
            else blue++;
            launched.add(new RotationStateDao.RosterEntry(
                    planned.playerId(), audience.nameOf(planned.playerId()),
                    team, RoundRole.PLAYER, RosterPresence.BATTLEFIELD
            ));
        }

        stateDao.replaceRoster(context.matchId(), launched).whenComplete((ignored, failure) -> onServerThread(() -> {
            if (!isCurrent(attempt)) {
                return;
            }
            if (failure != null) {
                abortActivation(context, "Could not durably publish the launched roster", failure);
                return;
            }
            launched.forEach(value -> queue.remove(value.playerId()));
            bindRoster(context.matchId(), launched);
            finalizeActivation(attempt, context);
        }));
    }

    private boolean guarded(java.util.function.BooleanSupplier launch, RotationStateDao.RosterEntry planned) {
        try {
            return launch.getAsBoolean();
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "Could not launch " + planned.playerName() + " into the new round.",
                    failure
            );
            return false;
        }
    }

    private void reportLaunchFailure(RotationStateDao.RosterEntry planned) {
        try {
            audience.sendToLobby(planned.playerId());
        } catch (RuntimeException ignored) {
            // Leaving them in the old world quarantines it from deletion, which
            // the cleanup queue already reports and retries.
        }
        audience.message(planned.playerId(), Component.text(
                "You could not be moved into the new siege. Use /siege join to queue again.",
                NamedTextColor.RED
        ));
        requeue(planned);
    }

    private void requeue(RotationStateDao.RosterEntry planned) {
        QueuedPlayer requeued = new QueuedPlayer(
                planned.playerId(), planned.role(), QueueSource.PREVIOUS_MATCH
        );
        queue.put(planned.playerId(), requeued);
        stateDao.queue(requeued.playerId(), requeued.role(), requeued.source());
    }

    private void finalizeActivation(long attempt, ActiveRoundContext context) {
        ActiveMapWorld old = disposableActiveWorld;
        RotationState activeState = new RotationState(
                RoundPhase.ACTIVE, context.generation(), durableState.revision(),
                context.matchId(), context.map().id(), context.world().getName(),
                durableState.previousMapId(), null, null, null, List.of()
        );
        writeState(activeState, saved -> {
            if (!isCurrent(attempt)) {
                return;
            }
            if (!rounds.publish(RoundPhase.ACTIVATING, context.generation(), context)) {
                abortActivation(context, "A stale generation blocked active-context publication", null);
                return;
            }
            disposableActiveWorld = prepared.activeWorld();
            prepared = null;
            excludedFromLaunch.clear();
            audience.broadcast(Component.text(
                    context.map().displayName() + " is live. Teams have been randomized and balanced.",
                    NamedTextColor.GOLD
            ));
            if (old != null) {
                enrollAndDiscard(old);
            }
        });
    }

    private void abortActivation(ActiveRoundContext context, String message, Throwable failure) {
        scoreDao.abort(context.matchId()).whenComplete((ignored, abortFailure) -> onServerThread(() ->
                discardPreparedAndRecover(message, failure == null ? abortFailure : failure)
        ));
    }

    private void discardPreparedAndRecover(String message, Throwable failure) {
        ActiveMapWorld failed = prepared == null ? null : prepared.activeWorld();
        prepared = null;
        clearRoundBindings();
        evacuateEveryone();
        enterRecovery(message, failure);
        if (failed != null && failed != disposableActiveWorld) {
            enrollAndDiscard(failed);
        }
    }

    // ------------------------------------------------------------------ world

    /**
     * Records a generated copy in the durable cleanup queue before attempting
     * to remove it, so a crash between unload and delete still leaves a row to
     * retry rather than an orphaned folder.
     */
    private void enrollAndDiscard(ActiveMapWorld world) {
        String name = world.world().getName();
        cleanupDao.enroll(name, world.folder().toString()).whenComplete((ignored, failure) -> onServerThread(() -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not record " + name + " for cleanup.", unwrap(failure));
                return;
            }
            attemptCleanup(name, worlds.unload(world), 0);
        }));
    }

    private void discardStale(ActiveMapWorld world) {
        if (world == null) {
            return;
        }
        logger.info("Discarding " + world.world().getName() + " from a superseded preparation attempt.");
        enrollAndDiscard(world);
    }

    private void attemptCleanup(String worldName, CompletableFuture<Void> operation, int attempts) {
        operation.whenComplete((ignored, failure) -> onServerThread(() -> {
            if (failure == null) {
                cleanupDao.complete(worldName);
                return;
            }
            Duration backoff = settings.cleanupBackoff(attempts);
            String reason = messageOf(unwrap(failure));
            logger.warning("Cleanup of " + worldName + " deferred (" + reason + "); retrying in "
                    + backoff.toSeconds() + "s.");
            cleanupDao.recordFailure(worldName, reason, scheduler.now().plus(backoff).toEpochMilli());
        }));
    }

    /** Retries every generated copy the previous run could not remove. */
    private void resumePendingCleanups() {
        cleanupDao.due(scheduler.now()).whenComplete((pending, failure) -> onServerThread(() -> {
            if (failure != null) {
                logger.log(Level.WARNING, "Could not read the world cleanup queue.", unwrap(failure));
                return;
            }
            pending.forEach(value -> attemptCleanup(
                    value.worldName(),
                    worlds.discard(value.worldName(), value.folder()),
                    value.attempts()
            ));
        }));
    }

    private void reportUntrackedWorlds() {
        List<String> untracked = worlds.untrackedGeneratedWorlds();
        if (!untracked.isEmpty()) {
            logger.warning("Found generated map folders with no durable record: " + untracked
                    + ". They were left in place for manual review; SiegePlugin will not delete them.");
        }
    }

    // -------------------------------------------------------------- operators

    public RoundPhase phase() {
        return rounds.phase();
    }

    public Optional<ActiveRoundContext> activeContext() {
        return rounds.isActive() ? rounds.current() : Optional.empty();
    }

    public List<String> statusLines() {
        RotationState state = durableState;
        if (state == null) {
            return List.of("Rotation is bootstrapping.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Phase " + state.phase() + " generation=" + state.generation()
                + " revision=" + state.revision());
        lines.add("Current match=" + value(state.currentMatchId())
                + " map=" + value(state.currentMapId())
                + " world=" + value(state.currentRuntimeWorld()));
        lines.add("Prepared map=" + value(state.preparedMapId())
                + " world=" + value(state.preparedRuntimeWorld()));
        lines.add("Queued players: " + queue.size() + "; rostered: " + roster.all().size());
        if (state.candidates().isEmpty()) {
            lines.add("Candidates: none");
        } else {
            lines.add("Candidates:");
            state.candidates().forEach(candidate -> lines.add("  - " + candidate.describe()));
        }
        return List.copyOf(lines);
    }

    /**
     * Reloads {@code maps.yml} and reports every admission problem. Loaded-copy
     * checks run against a throwaway copy, which is always unloaded again and
     * can never touch round state.
     */
    public void validate(String mapId, Consumer<List<String>> report) {
        MapManifest reloaded;
        try {
            reloaded = manifestLoader.get();
        } catch (RuntimeException failure) {
            report.accept(List.of(
                    "maps.yml could not be reloaded: " + messageOf(failure),
                    "Keeping the previously loaded manifest."
            ));
            return;
        }
        manifest = reloaded;

        List<SiegeMap> maps = mapId == null
                ? manifest.rotationPool()
                : manifest.find(mapId).map(List::of).orElse(List.of());
        if (maps.isEmpty()) {
            report.accept(List.of(mapId == null
                    ? "No enabled maps are configured in maps.yml."
                    : "Unknown or disabled map: " + mapId));
            return;
        }
        validateNext(new ArrayList<>(maps), 0, new ArrayList<>(), report);
    }

    private void validateNext(
            List<SiegeMap> maps,
            int index,
            List<String> report,
            Consumer<List<String>> completion
    ) {
        if (index >= maps.size()) {
            completion.accept(List.copyOf(report));
            return;
        }
        SiegeMap map = maps.get(index);
        List<String> problems = staticProblemsFor(map);
        if (!problems.isEmpty()) {
            appendReport(report, map, problems);
            validateNext(maps, index + 1, report, completion);
            return;
        }
        // A throwaway copy is the only way to check spawn footing and chests.
        worlds.load(map).whenComplete((loaded, failure) -> onServerThread(() -> {
            if (failure != null) {
                appendReport(report, map, List.of("copy failed: " + messageOf(unwrap(failure))));
                validateNext(maps, index + 1, report, completion);
                return;
            }
            List<String> runtime = worlds.loadedCopyProblems(loaded);
            enrollAndDiscard(loaded);
            appendReport(report, map, runtime);
            validateNext(maps, index + 1, report, completion);
        }));
    }

    private static void appendReport(List<String> report, SiegeMap map, List<String> problems) {
        if (problems.isEmpty()) {
            report.add(map.id() + " (" + map.displayName() + "): valid");
        } else {
            report.add(map.id() + " (" + map.displayName() + "): " + problems.size() + " problem(s)");
            problems.forEach(problem -> report.add("  - " + problem));
        }
        MapValidator.baseClaimWarnings(map).forEach(warning -> report.add("  - warning: " + warning));
    }

    public boolean retry(String requestedMapId) {
        if (rounds.phase() != RoundPhase.RECOVERY && rounds.phase() != RoundPhase.INTERMISSION) {
            return false;
        }
        List<SiegeMap> candidates = candidates(requestedMapId);
        if (candidates.isEmpty()) {
            return false;
        }
        // Invalidate first: an earlier preparation may still be in flight, and
        // its completion must not activate over this retry.
        invalidateAttempts();
        ActiveMapWorld stalePrepared = prepared == null ? null : prepared.activeWorld();
        prepared = null;
        rounds.restore(RoundPhase.INTERMISSION, null);
        durableState = durableState
                .withPhase(RoundPhase.INTERMISSION)
                .withPrepared(null, null)
                .withCandidates(candidates.stream().map(SiegeMap::id).map(RotationCandidate::pending).toList());
        if (stalePrepared != null) {
            enrollAndDiscard(stalePrepared);
        }
        writeState(durableState, saved -> {
            loadCandidate(beginAttempt(), candidates, 0);
            scheduler.startTicking(this::tick);
        });
        return true;
    }

    /**
     * Admin override for a round stuck in ACTIVE with no way to end naturally.
     * It follows the same automatic intermission and map-preparation path as a
     * normally completed siege, without inventing a winner.
     */
    public boolean endActive() {
        if (rounds.phase() != RoundPhase.ACTIVE) {
            return false;
        }
        RotationState state = durableState;
        if (state == null || state.currentMatchId() == null) {
            return false;
        }
        invalidateAttempts();
        scoreDao.abort(state.currentMatchId()).whenComplete((aborted, abortFailure) -> onServerThread(() -> {
            if (abortFailure != null) {
                enterRecovery("Could not abort the admin-ended match", abortFailure);
                return;
            }
            stateDao.transferRosterToQueue(state.currentMatchId()).whenComplete((transferred, transferFailure) ->
                    onServerThread(() -> {
                        if (transferFailure != null) {
                            enterRecovery("Could not transfer the admin-ended match's roster", transferFailure);
                            return;
                        }
                        transferred.forEach(value -> {
                            queue.putIfAbsent(value.playerId(), value);
                            audience.discardStoredRoundInventory(value.playerId());
                        });
                        clearRoundBindings();
                        rounds.restore(RoundPhase.INTERMISSION, null);
                        evacuateEveryone();
                        preparationDeadline = null;
                        RotationState next = new RotationState(
                                RoundPhase.INTERMISSION, state.generation(), state.revision(),
                                null, null, null, state.currentMapId(), null, null,
                                scheduler.now().plus(settings.forcedLobbyDelay()), List.of()
                        );
                        durableState = next;
                        beginPreparation(null, "The siege was ended by an admin. Preparing the next map.");
                    }));
        }));
        return true;
    }

    /** Returns the authoritative active-round team for an in-game test command. */
    public Optional<Team> activeFighterTeam(UUID playerId) {
        if (rounds.phase() != RoundPhase.ACTIVE) {
            return Optional.empty();
        }
        RoundRoster.Membership membership = roster.presenceOf(playerId).orElse(null);
        if (membership == null || membership.role() != RoundRole.PLAYER || membership.team() == null) {
            return Optional.empty();
        }
        return Optional.of(membership.team());
    }

    // ----------------------------------------------------------------- joins

    /** Phase-explicit {@code /siege join}. Returns the outcome to report. */
    public JoinOutcome requestJoin(UUID playerId) {
        return switch (rounds.phase()) {
            case ACTIVE -> joinActiveRound(playerId);
            case INTERMISSION, RECOVERY -> {
                QueuedPlayer value = new QueuedPlayer(playerId, RoundRole.PLAYER, QueueSource.OPT_IN);
                stateDao.queue(playerId, value.role(), value.source()).whenComplete((ignored, failure) ->
                        onServerThread(() -> {
                            if (failure != null) {
                                audience.message(playerId, Component.text(
                                        "Your queue request could not be saved. Please try again.",
                                        NamedTextColor.RED
                                ));
                                return;
                            }
                            queue.put(playerId, value);
                            audience.sendToLobby(playerId);
                            audience.message(playerId, Component.text(
                                    "You are queued for the next siege.", NamedTextColor.GREEN
                            ));
                        }));
                yield JoinOutcome.QUEUED;
            }
            case BOOTSTRAPPING, COMPLETING, ACTIVATING -> JoinOutcome.TEMPORARILY_UNAVAILABLE;
        };
    }

    private JoinOutcome joinActiveRound(UUID playerId) {
        ActiveRoundContext context = rounds.current().orElse(null);
        if (context == null) {
            return JoinOutcome.TEMPORARILY_UNAVAILABLE;
        }
        Team team = roster.smallerActiveSide(audience::isOnline);
        RoundRoster.Membership membership = new RoundRoster.Membership(
                playerId, audience.nameOf(playerId), team, RoundRole.PLAYER, RosterPresence.BATTLEFIELD
        );
        // Claim the side in the in-memory roster before the durable write. Two
        // joins arriving back to back would otherwise both read the same "smaller
        // side" and stack onto one team; the entry is withdrawn if either the
        // write or the launch fails.
        roster.put(membership);
        RotationStateDao.RosterEntry assignment = new RotationStateDao.RosterEntry(
                playerId, membership.playerName(), team, RoundRole.PLAYER, RosterPresence.BATTLEFIELD
        );
        stateDao.upsertRoster(context.matchId(), assignment).whenComplete((ignored, failure) ->
                onServerThread(() -> {
                    if (failure != null) {
                        roster.remove(playerId);
                        audience.message(playerId, Component.text(
                                "Your siege assignment could not be saved. Please try again.",
                                NamedTextColor.RED
                        ));
                        return;
                    }
                    boolean launched;
                    try {
                        launched = audience.launchFighter(playerId, team, context);
                    } catch (RuntimeException townyFailure) {
                        logger.log(
                                Level.WARNING,
                                "Could not launch " + membership.playerName() + " into the active round.",
                                townyFailure
                        );
                        launched = false;
                    }
                    if (!launched) {
                        roster.remove(playerId);
                        audience.message(playerId, Component.text(
                                "You could not be moved onto the battlefield.", NamedTextColor.RED
                        ));
                        return;
                    }
                    queue.remove(playerId);
                }));
        return JoinOutcome.JOINED;
    }

    /**
     * The intermission lobby button. Outside {@code ACTIVE} the round inventory
     * is being discarded anyway, so this evacuates directly instead of running
     * the normal save-and-restore lobby transition.
     *
     * @return false while a round is active, so the caller uses the normal path
     */
    public boolean goToLobby(UUID playerId) {
        if (rounds.phase() == RoundPhase.ACTIVE) {
            return false;
        }
        return audience.sendToLobby(playerId);
    }

    /** A player voluntarily returning to the lobby stays rostered but not present. */
    public void markReturnedToLobby(UUID playerId) {
        String matchId = roster.matchId();
        if (matchId == null || roster.presenceOf(playerId).isEmpty()) {
            return;
        }
        roster.setPresence(playerId, RosterPresence.LOBBY);
        stateDao.updatePresence(matchId, playerId, RosterPresence.LOBBY);
    }

    /** Records a mid-round team switch so balancing keeps using real numbers. */
    public void recordTeamSwitch(UUID playerId, Team team) {
        String matchId = roster.matchId();
        RoundRoster.Membership current = roster.presenceOf(playerId).orElse(null);
        if (matchId == null || current == null) {
            return;
        }
        RoundRoster.Membership updated = new RoundRoster.Membership(
                playerId, current.playerName(), team, current.role(), current.presence()
        );
        roster.put(updated);
        stateDao.upsertRoster(matchId, new RotationStateDao.RosterEntry(
                playerId, current.playerName(), team, current.role(), current.presence()
        ));
    }

    /** Restores a reconnecting player to whatever the durable roster remembers. */
    public void handleJoin(UUID playerId) {
        RoundPhase phase = rounds.phase();
        if (phase == RoundPhase.ACTIVE) {
            rehydrateIntoActiveRound(playerId);
            return;
        }
        if (phase == RoundPhase.INTERMISSION || phase == RoundPhase.RECOVERY
                || phase == RoundPhase.ACTIVATING) {
            audience.sendToLobby(playerId);
            audience.message(playerId, queue.containsKey(playerId)
                    ? Component.text("You are still queued for the next siege.", NamedTextColor.GREEN)
                    : Component.text("Use /siege join to opt into the next siege.", NamedTextColor.YELLOW));
        }
    }

    private void rehydrateIntoActiveRound(UUID playerId) {
        ActiveRoundContext context = rounds.current().orElse(null);
        if (context == null) {
            return;
        }
        RoundRoster.Membership member = roster.presenceOf(playerId).orElse(null);
        if (member == null) {
            if (queue.containsKey(playerId)) {
                audience.sendToLobby(playerId);
                audience.message(playerId, Component.text(
                        "The siege has started. Use /siege join to enter.", NamedTextColor.YELLOW
                ));
            }
            return;
        }
        if (member.presence() != RosterPresence.BATTLEFIELD) {
            // They chose the lobby before disconnecting; leave that choice alone.
            audience.sendToLobby(playerId);
            audience.message(playerId, Component.text(
                    "Use /siege join to return to the siege.", NamedTextColor.YELLOW
            ));
            return;
        }
        boolean restored = member.role() == RoundRole.SPECTATOR
                ? audience.launchSpectator(playerId, context)
                : audience.launchFighter(playerId, member.team(), context);
        if (!restored) {
            audience.sendToLobby(playerId);
            audience.message(playerId, Component.text(
                    "You could not be returned to the battlefield. Use /siege join to try again.",
                    NamedTextColor.RED
            ));
        }
    }

    // ------------------------------------------------------------- internals

    private void bindRoster(String matchId, List<RotationStateDao.RosterEntry> entries) {
        roster.bind(matchId, entries.stream()
                .map(value -> new RoundRoster.Membership(
                        value.playerId(), value.playerName(), value.team(), value.role(), value.presence()
                ))
                .toList());
    }

    private void clearRoundBindings() {
        roster.clear();
        stats.unbind();
    }

    private MatchDefinition definitionFor(ActiveRoundContext context) {
        return MatchDefinition.rotating(
                context.matchId(), context.map().id(), context.world().getName(), scoreLimit
        );
    }

    /**
     * Persists one lifecycle decision, then runs the follow-up only if the write
     * landed. A lost compare-and-set means another path already moved the
     * lifecycle on, so this one reloads rather than overwriting it.
     */
    private void writeState(RotationState next, Consumer<RotationState> onSaved) {
        stateDao.save(next).whenComplete((saved, failure) -> onServerThread(() -> {
            if (failure == null) {
                durableState = saved;
                onSaved.accept(saved);
                return;
            }
            Throwable cause = unwrap(failure);
            if (cause instanceof RotationStateDao.StaleRevisionException) {
                logger.warning("Rotation state changed underneath this decision; reloading. "
                        + cause.getMessage());
                stateDao.load().whenComplete((reloaded, reloadFailure) -> onServerThread(() -> {
                    if (reloadFailure != null || reloaded.isEmpty()) {
                        enterRecovery("Could not reload rotation state after a lost write", reloadFailure);
                        return;
                    }
                    durableState = reloaded.get();
                }));
                return;
            }
            enterRecovery("Could not persist a rotation lifecycle change", failure);
        }));
    }

    private List<String> staticProblemsFor(SiegeMap map) {
        List<String> problems = new ArrayList<>(worlds.staticProblems(map));
        problems.addAll(supplyValidator.apply(map.id(), map.bounds()));
        return problems;
    }

    private List<SiegeMap> candidates(String requestedMapId) {
        String previous = durableState == null ? null
                : durableState.previousMapId() != null
                ? durableState.previousMapId()
                : durableState.currentMapId();
        List<SiegeMap> admissible = manifest.rotationPool().stream()
                .filter(map -> {
                    List<String> problems = staticProblemsFor(map);
                    if (problems.isEmpty()) {
                        return true;
                    }
                    logger.warning("Excluding map " + map.id() + ": " + String.join("; ", problems));
                    return false;
                })
                .toList();
        return RotationOrder.candidates(admissible, previous, requestedMapId, random);
    }

    private ActiveRoundContext context(long generation, String matchId, ActiveMapWorld active) {
        SiegeMap map = active.map();
        Map<Team, Location> spawns = Map.of(
                Team.RED, location(active, map.redSpawn()),
                Team.BLUE, location(active, map.blueSpawn())
        );
        return new ActiveRoundContext(
                generation, matchId, map, active.world(), scoreLimit,
                spawns, location(active, map.capturePoint()), map.bounds()
        );
    }

    private static Location location(ActiveMapWorld world, MapPoint point) {
        return new Location(world.world(), point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    private void evacuateEveryone() {
        audience.onlinePlayers().forEach(audience::sendToLobby);
    }

    private void enterRecovery(String message, Throwable failure) {
        if (failure == null) {
            logger.severe(message);
        } else {
            logger.log(Level.SEVERE, message, unwrap(failure));
        }
        invalidateAttempts();
        scheduler.stopTicking();
        preparationDeadline = null;
        clearRoundBindings();
        rounds.restore(RoundPhase.RECOVERY, null);
        RotationState before = durableState;
        durableState = new RotationState(
                RoundPhase.RECOVERY,
                before == null ? 1L : before.generation(),
                before == null ? 0L : before.revision(),
                null, null, null,
                before == null ? null : before.previousMapId(),
                null, null, null,
                before == null ? List.of() : before.candidates()
        );
        stateDao.save(durableState).whenComplete((saved, saveFailure) -> onServerThread(() -> {
            if (saveFailure == null) {
                durableState = saved;
            } else {
                logger.log(Level.SEVERE, "Could not record RECOVERY state.", unwrap(saveFailure));
            }
        }));
        announceRecovery();
    }

    private void announceRecovery() {
        audience.broadcast(Component.text(
                "Siege rotation needs administrator recovery. Players will remain in the lobby.",
                NamedTextColor.RED
        ));
    }

    private long beginAttempt() {
        activeAttempt = ++attemptSequence;
        return activeAttempt;
    }

    private void invalidateAttempts() {
        activeAttempt = ++attemptSequence;
    }

    private boolean isCurrent(long attempt) {
        return !stopped && attempt == activeAttempt;
    }

    private RotationStateDao.RosterEntry entry(
            UUID playerId, Team team, RoundRole role, RosterPresence presence
    ) {
        return new RotationStateDao.RosterEntry(playerId, audience.nameOf(playerId), team, role, presence);
    }

    private void onServerThread(Runnable action) {
        scheduler.onServerThread(action);
    }

    // ------------------------------------------------------------- ceremony

    private List<Component> results(MatchRecord match, MvpResults mvp) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(Component.text("[Siege] Match Complete", NamedTextColor.GOLD, TextDecoration.BOLD));
        lines.add(Component.text(
                match.winner().defaultDisplayName() + " wins " + match.redScore() + " - " + match.blueScore(),
                match.winner() == Team.RED ? NamedTextColor.RED : NamedTextColor.BLUE
        ));
        lines.add(category("Kills MVP", mvp.killsMvp(), value -> value.kills() + " kills"));
        lines.add(category("Damage MVP", mvp.damageMvp(),
                value -> String.format("%.1f damage", value.damage())));
        lines.add(category("Banner MVP", mvp.bannerMvp(), value -> formatBanner(value.bannerSeconds())));
        lines.add(Component.text("Overall MVP: ", NamedTextColor.GOLD)
                .append(Component.text(
                        mvp.overallMvp().map(PlayerMatchStats::playerName).orElse("None"),
                        NamedTextColor.AQUA
                )));
        return List.copyOf(lines);
    }

    private static Component lobbyButton() {
        return Component.text("[Go to Lobby]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/siege lobby"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Return to the lobby now instead of waiting for the automatic transfer.",
                        NamedTextColor.GRAY
                )));
    }

    private static Component category(
            String label,
            Optional<PlayerMatchStats> value,
            java.util.function.Function<PlayerMatchStats, String> stat
    ) {
        return Component.text(label + ": ", NamedTextColor.GOLD).append(value
                .map(player -> Component.text(
                        player.playerName() + " (" + stat.apply(player) + ")", NamedTextColor.AQUA
                ))
                .orElse(Component.text("None — 0", NamedTextColor.GRAY)));
    }

    private static String formatBanner(long seconds) {
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private static String messageOf(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? Objects.toString(failure == null ? "unknown failure" : failure.getClass().getSimpleName())
                : message;
    }

    private static String value(String text) {
        return text == null ? "none" : text;
    }

    /** What {@code /siege join} did, so the command layer can phrase the reply. */
    public enum JoinOutcome {
        JOINED,
        QUEUED,
        TEMPORARILY_UNAVAILABLE
    }

    private record RecoveredRound(List<PlayerMatchStats> stats, List<RotationStateDao.RosterEntry> roster) {
    }

    private record RecoveredCeremony(List<QueuedPlayer> roster, List<PlayerMatchStats> stats) {
    }

    private record CompletedRound(Optional<RotationState> state, List<QueuedPlayer> roster) {
    }
}
