package woo.siegePlugin.stats;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import woo.siegePlugin.capture.CaptureService;
import woo.siegePlugin.round.RoundActivityStatus;
import woo.siegePlugin.persistence.MatchStatsDao;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Tracks banner seconds and checkpoints all dirty match stats every five seconds. */
public final class MatchStatsService {

    private final JavaPlugin plugin;
    private final MatchStatsDao dao;
    private final MatchStatsTracker tracker;
    private final CaptureService capture;
    private final RoundActivityStatus phaseStatus;
    private final Supplier<String> matchId;
    private BukkitTask bannerTask;
    private BukkitTask checkpointTask;

    public MatchStatsService(
            JavaPlugin plugin,
            MatchStatsDao dao,
            MatchStatsTracker tracker,
            CaptureService capture,
            RoundActivityStatus phaseStatus,
            Supplier<String> matchId
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.phaseStatus = Objects.requireNonNull(phaseStatus, "phaseStatus");
        this.matchId = Objects.requireNonNull(matchId, "matchId");
    }

    public void start() {
        bannerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::recordBannerSecond, 20L, 20L);
        checkpointTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkpoint, 100L, 100L);
    }

    public void stop() {
        if (bannerTask != null) bannerTask.cancel();
        if (checkpointTask != null) checkpointTask.cancel();
        checkpoint();
    }

    private void recordBannerSecond() {
        String currentMatch = matchId.get();
        if (currentMatch == null || !phaseStatus.isActive()) {
            return;
        }
        for (java.util.UUID playerId : capture.bannerParticipantIds()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                tracker.recordBannerSecond(currentMatch, playerId, player.getName());
            }
        }
    }

    /**
     * Checkpoints only while the tracker is bound to the currently active match.
     * Writing during intermission, or against a different match, would persist a
     * snapshot that no longer describes the round it is filed under.
     */
    private void checkpoint() {
        String currentMatch = matchId.get();
        if (currentMatch == null || !phaseStatus.isActive() || !tracker.isBoundTo(currentMatch)) {
            return;
        }
        dao.saveSnapshot(currentMatch, tracker.snapshot()).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Could not checkpoint siege MVP statistics.", failure);
            return null;
        });
    }
}
