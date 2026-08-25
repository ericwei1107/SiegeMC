package woo.siegePlugin.score;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import woo.siegePlugin.capture.BannerControlStatus;
import woo.siegePlugin.cycle.SiegePhaseStatus;
import woo.siegePlugin.display.SidebarService;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.MatchScores;
import woo.siegePlugin.persistence.ScoreReason;
import woo.siegePlugin.team.Team;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Awards and persists the eternal siege score.
 *
 * <p>Scoring only begins once the match row is recovered, so a slow or failed
 * database never lets points accrue against a total that was never loaded.</p>
 */
public final class ScoringService {

    /** The single persistent match this server scores into. */
    public static final String MATCH_ID = "eternal-1";

    /** SiegeWar awards banner-control points once per 20-second tick. */
    private static final long SCORING_PERIOD_TICKS = 400L;

    private final JavaPlugin plugin;
    private final MatchScoreDao matchScoreDao;
    private final BannerControlStatus bannerControl;
    private final SidebarService sidebarService;
    private final SiegePhaseStatus phaseStatus;
    private final ScoringSettings settings;
    private final SessionPoints sessionPoints = new SessionPoints();
    private final AtomicBoolean active = new AtomicBoolean(true);

    private MatchScores scores;
    private BukkitTask task;

    public ScoringService(
            JavaPlugin plugin,
            MatchScoreDao matchScoreDao,
            BannerControlStatus bannerControl,
            SidebarService sidebarService,
            SiegePhaseStatus phaseStatus,
            ScoringSettings settings
    ) {
        this.plugin = plugin;
        this.matchScoreDao = matchScoreDao;
        this.bannerControl = bannerControl;
        this.sidebarService = sidebarService;
        this.phaseStatus = phaseStatus;
        this.settings = settings;
    }

    public void start() {
        matchScoreDao.loadOrCreate(MATCH_ID).whenComplete((loaded, failure) ->
                onServerThread(() -> finishStart(loaded, failure))
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
     * Clears the eternal score and ledgers the reversal. The completion runs on
     * the server thread.
     */
    public void resetScores(BiConsumer<MatchScores, Throwable> completion) {
        matchScoreDao.reset(MATCH_ID).whenComplete((reset, failure) -> onServerThread(() -> {
            if (failure == null) {
                scores = reset;
                sessionPoints.reset();
                publishScores();
                publishSessionPoints();
            } else {
                logScoreFailure("reset", failure);
            }
            completion.accept(reset, failure);
        }));
    }

    private void finishStart(MatchScores loaded, Throwable failure) {
        if (failure != null) {
            logScoreFailure("load", failure);
            plugin.getLogger().severe("Siege scoring is disabled because " + MATCH_ID + " could not be loaded.");
            return;
        }

        this.scores = loaded;
        publishScores();
        publishSessionPoints();
        plugin.getLogger().info(
                "Loaded siege match " + MATCH_ID
                        + " (red=" + loaded.redScore() + ", blue=" + loaded.blueScore() + ")."
        );

        this.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::awardBannerControlPoints,
                SCORING_PERIOD_TICKS,
                SCORING_PERIOD_TICKS
        );
    }

    /**
     * Credits a team for an enemy death. The caller decides who died; this
     * decides whether the award currently counts.
     */
    public void awardEnemyDeathBonus(Team beneficiary) {
        award(beneficiary, settings.enemyDeathBonusPoints(), ScoreReason.ENEMY_DEATH_BONUS);
    }

    private void awardBannerControlPoints() {
        Team controllingTeam = bannerControl.controllingTeam().orElse(null);
        if (controllingTeam == null) {
            return;
        }

        // SiegeWar's reversal multiplier is deliberately omitted.
        long points = settings.pointsForControllers(bannerControl.controllerCount());
        award(controllingTeam, points, ScoreReason.BANNER_CONTROL);
    }

    /** The single gate every score change passes through. */
    private void award(Team team, long points, ScoreReason reason) {
        if (points == 0L || !phaseStatus.isActive()) {
            return;
        }
        if (scores == null) {
            // The match never loaded, so there is no total to add to.
            plugin.getLogger().warning("Ignoring a " + reason + " award because " + MATCH_ID + " is not loaded.");
            return;
        }

        matchScoreDao.award(MATCH_ID, team, points, reason).whenComplete((updated, failure) ->
                onServerThread(() -> {
                    if (failure != null) {
                        logScoreFailure("award", failure);
                        return;
                    }
                    // Session points follow the persisted total so the sidebar
                    // never shows points that failed to save.
                    scores = updated;
                    sessionPoints.add(team, points);
                    publishScores();
                    publishSessionPoints();
                })
        );
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
}
