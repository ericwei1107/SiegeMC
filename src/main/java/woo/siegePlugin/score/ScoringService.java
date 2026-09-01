package woo.siegePlugin.score;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import woo.siegePlugin.capture.BannerControlStatus;
import woo.siegePlugin.round.RoundActivityStatus;
import woo.siegePlugin.display.SidebarService;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.MatchDefinition;
import woo.siegePlugin.persistence.MatchRecord;
import woo.siegePlugin.persistence.ScoreReason;
import woo.siegePlugin.stats.PlayerMatchStats;
import woo.siegePlugin.team.Team;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Awards the currently published finite match and hands its durable winner to rotation.
 *
 * <p>Scoring only begins once the match row is recovered, so a slow or failed
 * database never lets points accrue against a total that was never loaded.</p>
 */
public final class ScoringService implements woo.siegePlugin.round.RoundScoring {

    /** Legacy identifier retained only for database migration tests. */
    public static final String MATCH_ID = "eternal-1";

    private final JavaPlugin plugin;
    private final MatchScoreDao matchScoreDao;
    private final BannerControlStatus bannerControl;
    private final SidebarService sidebarService;
    private final RoundActivityStatus phaseStatus;
    private final ScoringSettings settings;
    private MatchDefinition matchDefinition;
    private final SessionPoints sessionPoints = new SessionPoints();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private Consumer<Set<UUID>> bannerControlRewardHandler = playerIds -> {
    };
    private Consumer<MatchRecord> matchCompletedHandler = ignored -> {
    };
    private Supplier<Collection<PlayerMatchStats>> finalStatsSupplier = List::of;

    private MatchRecord scores;
    private BukkitTask task;
    private long activeWindowGeneration;

    public ScoringService(
            JavaPlugin plugin,
            MatchScoreDao matchScoreDao,
            BannerControlStatus bannerControl,
            SidebarService sidebarService,
            RoundActivityStatus phaseStatus,
            ScoringSettings settings
    ) {
        this.plugin = plugin;
        this.matchScoreDao = matchScoreDao;
        this.bannerControl = bannerControl;
        this.sidebarService = sidebarService;
        this.phaseStatus = phaseStatus;
        this.settings = settings;
    }

    /**
     * Starts the accrual tick only. No match is loaded here: the rotation
     * coordinator decides which match exists and calls {@link #activateMatch},
     * so scoring can never open against a boot-time world nobody is playing on.
     */
    public void start() {
        publishSessionPoints();
        this.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::awardBannerControlPoints,
                scoringPeriodTicks(),
                scoringPeriodTicks()
        );
    }

    public void stop() {
        active.set(false);
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Compatibility API for explicit tooling; normal rollover always creates
     * a new match and never mutates completed history. Banner session points
     * remain intact because only a new ACTIVE window resets them. The
     * completion runs on the server thread.
     */
    public void resetScores(BiConsumer<MatchRecord, Throwable> completion) {
        if (matchDefinition == null) {
            completion.accept(null, new IllegalStateException("No siege match is active"));
            return;
        }
        matchScoreDao.reset(matchDefinition.matchId()).whenComplete((reset, failure) -> onServerThread(() -> {
            if (failure == null) {
                scores = reset;
                publishScores();
            } else {
                logScoreFailure("reset", failure);
            }
            completion.accept(reset, failure);
        }));
    }

    /** Starts a new ephemeral ACTIVE window without affecting persistent scores. */
    public void beginActiveWindow() {
        activeWindowGeneration++;
        sessionPoints.reset();
        publishSessionPoints();
    }

    /** Receives all online completed controllers once for every active scoring tick. */
    public void setBannerControlRewardHandler(Consumer<Set<UUID>> handler) {
        bannerControlRewardHandler = handler;
    }

    public void setMatchCompletedHandler(Consumer<MatchRecord> handler) {
        matchCompletedHandler = handler;
    }

    public void setFinalStatsSupplier(Supplier<Collection<PlayerMatchStats>> supplier) {
        finalStatsSupplier = supplier;
    }

    /**
     * Rebinds this service after a prepared round has been durably created.
     *
     * <p>A recovered row is only accepted when it is still ACTIVE and records
     * the same map and generated world the caller is about to publish. Opening
     * scoring against a completed match, or against a row that remembers a
     * different world, would let points accrue somewhere nobody is playing.</p>
     */
    @Override
    public void activateMatch(MatchDefinition definition, Consumer<Throwable> completion) {
        matchScoreDao.loadOrCreate(definition).whenComplete((loaded, failure) -> onServerThread(() -> {
            if (failure != null) {
                completion.accept(failure);
                return;
            }
            String mismatch = loaded.mismatchAgainst(definition).orElse(null);
            if (mismatch != null) {
                completion.accept(new IllegalStateException("Refusing to open scoring: " + mismatch));
                return;
            }
            matchDefinition = definition;
            scores = loaded;
            beginActiveWindow();
            publishScores();
            completion.accept(null);
        }));
    }

    public MatchRecord currentScores() {
        return scores;
    }

    /** Prepares an active team for an end-of-siege rotation test. */
    public void primeTeamForEndTest(Team team, Consumer<String> completion) {
        final long targetScore = 9_990L;
        if (!phaseStatus.isActive() || scores == null || matchDefinition == null) {
            completion.accept("No active siege is available to prepare for a rotation test.");
            return;
        }
        if (scores.scoreLimit() <= targetScore) {
            completion.accept("The configured winning score must be above " + targetScore + " for this test.");
            return;
        }
        String matchId = matchDefinition.matchId();
        matchScoreDao.setActiveTeamScore(matchId, team, targetScore, ScoreReason.ADMIN_TEST_SET)
                .whenComplete((updated, failure) -> onServerThread(() -> {
                    if (failure != null) {
                        logScoreFailure("prepare the rotation test", failure);
                        completion.accept("Could not prepare the rotation test. Check the server log.");
                        return;
                    }
                    if (matchDefinition == null || !matchId.equals(matchDefinition.matchId())) {
                        completion.accept("The active siege changed before the test score could be applied.");
                        return;
                    }
                    scores = updated;
                    publishScores();
                    completion.accept(team.defaultDisplayName() + " was set to " + targetScore
                            + " points. Earn 10 more points to test match completion and rotation.");
                }));
    }

    /**
     * Credits a team for an enemy death. The caller decides who died; this
     * decides whether the award currently counts.
     */
    public boolean awardEnemyDeathBonus(Team beneficiary) {
        return award(beneficiary, settings.killRewardPoints(), ScoreReason.ENEMY_DEATH_BONUS);
    }

    public void awardEnemyDeathBonus(Team beneficiary, Consumer<Boolean> completion) {
        award(beneficiary, settings.killRewardPoints(), ScoreReason.ENEMY_DEATH_BONUS, completion);
    }

    /** The configured team-score award displayed in a Siege death announcement. */
    public long killRewardPoints() {
        return settings.killRewardPoints();
    }

    private void awardBannerControlPoints() {
        Team controllingTeam = bannerControl.controllingTeam().orElse(null);
        if (controllingTeam == null || !phaseStatus.isActive()) {
            return;
        }

        // SiegeWar's reversal multiplier is deliberately omitted.
        long points = settings.pointsForControllers(bannerControl.controllerCount());
        Set<UUID> controllers = rewardableControllerIds(phaseStatus, bannerControl);
        award(controllingTeam, points, ScoreReason.BANNER_CONTROL, accepted -> {
            if (accepted) {
                awardBannerControllerCurrency(controllers);
            }
        });
    }

    private void awardBannerControllerCurrency(Set<UUID> controllerIds) {
        Set<UUID> onlineControllerIds = controllerIds.stream()
                .filter(controllerId -> {
                    var controller = plugin.getServer().getPlayer(controllerId);
                    return controller != null && controller.isOnline();
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!onlineControllerIds.isEmpty()) {
            bannerControlRewardHandler.accept(onlineControllerIds);
        }
    }

    /**
     * The single gate every score change passes through. Queues an award when
     * scoring is currently able to accept it.
     *
     * @return {@code true} when the award was accepted for persistence
     */
    private boolean award(Team team, long points, ScoreReason reason) {
        return award(team, points, reason, ignored -> {
        });
    }

    private boolean award(Team team, long points, ScoreReason reason, Consumer<Boolean> completion) {
        if (points == 0L || !phaseStatus.isActive()) {
            completion.accept(false);
            return false;
        }
        if (scores == null || matchDefinition == null) {
            // No round is published, so there is no total to add to.
            plugin.getLogger().warning("Ignoring a " + reason + " award because no active match is loaded.");
            completion.accept(false);
            return false;
        }
        long awardWindowGeneration = activeWindowGeneration;
        String awardMatchId = matchDefinition.matchId();
        Collection<PlayerMatchStats> finalStats = List.copyOf(finalStatsSupplier.get());

        matchScoreDao.awardWithCutoff(awardMatchId, team, points, reason, finalStats).whenComplete((outcome, failure) ->
                onServerThread(() -> {
                    if (failure != null) {
                        logScoreFailure("award", failure);
                        completion.accept(false);
                        return;
                    }
                    if (!awardMatchId.equals(matchDefinition.matchId())) {
                        completion.accept(false);
                        return;
                    }
                    // Session points follow a successful banner-control write,
                    // so the sidebar never shows points that failed to save or
                    // rewards earned for a different reason.
                    scores = outcome.match();
                    if (outcome.accepted() && shouldApplySessionPoints(
                            reason, phaseStatus, awardWindowGeneration, activeWindowGeneration
                    )) {
                        sessionPoints.add(team, points);
                    }
                    publishScores();
                    publishSessionPoints();
                    completion.accept(outcome.accepted());
                    if (outcome.completedNow()) {
                        matchCompletedHandler.accept(outcome.match());
                    }
                })
        );
        return true;
    }

    static boolean shouldApplySessionPoints(
            ScoreReason reason,
            RoundActivityStatus phaseStatus,
            long awardWindowGeneration,
            long currentWindowGeneration
    ) {
        return reason.contributesToSessionPoints()
                && phaseStatus.isActive()
                && awardWindowGeneration == currentWindowGeneration;
    }

    static Set<UUID> rewardableControllerIds(RoundActivityStatus phaseStatus, BannerControlStatus bannerControl) {
        if (!phaseStatus.isActive() || bannerControl.controllingTeam().isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(bannerControl.controllerIds());
    }

    private void publishScores() {
        if (scores != null) {
            sidebarService.updateScores(scores.redScore(), scores.blueScore());
        }
    }

    private void publishSessionPoints() {
        sidebarService.updateSessionPoints(sessionPoints.get(Team.RED), sessionPoints.get(Team.BLUE));
    }

    private void onServerThread(Runnable action) {
        if (!active.get()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (active.get()) {
                action.run();
            }
        });
    }

    private void logScoreFailure(String operation, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        plugin.getLogger().log(Level.SEVERE, "Could not " + operation + " siege scores.", cause);
    }

    private long scoringPeriodTicks() {
        return Math.max(1L, settings.tickInterval().toSeconds() * 20L);
    }
}
