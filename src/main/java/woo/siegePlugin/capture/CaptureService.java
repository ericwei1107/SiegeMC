package woo.siegePlugin.capture;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import woo.siegePlugin.display.SidebarService;
import woo.siegePlugin.round.RoundActivityStatus;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs banner capture sessions against the configured capture point, following
 * SiegeWar's evaluate-existing-then-evaluate-new ordering on a one-second tick.
 */
public final class CaptureService implements CaptureSessionStatus, BannerControlStatus {

    private static final long TICK_PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final TownyAdapter townyAdapter;
    private final SidebarService sidebarService;
    private CaptureBanner banner;
    private final CaptureBeacon beacon = new CaptureBeacon();
    private final CaptureBossBars bossBars;
    private final CaptureControl control = new CaptureControl();
    private final Map<UUID, CaptureSession> sessions = new LinkedHashMap<>();
    private final Clock clock;
    private CaptureSettings settings;
    private final RoundActivityStatus phaseStatus;

    /**
     * Battlefield-presence gate. Capture progress must require the same
     * eligibility as scoring, or a player in the lobby standing at matching
     * coordinates could accrue banner control.
     */
    private java.util.function.Predicate<Player> battlefieldFighter = player -> true;
    private java.util.function.Predicate<java.util.UUID> bossBarVisible = playerId -> true;

    private BukkitTask task;
    private boolean suspended;

    public CaptureService(
            JavaPlugin plugin,
            TownyAdapter townyAdapter,
            SidebarService sidebarService,
            CaptureBanner banner,
            CaptureSettings settings,
            RoundActivityStatus phaseStatus
    ) {
        this(plugin, townyAdapter, sidebarService, banner, settings, phaseStatus, Clock.systemUTC());
    }

    CaptureService(
            JavaPlugin plugin,
            TownyAdapter townyAdapter,
            SidebarService sidebarService,
            CaptureBanner banner,
            CaptureSettings settings,
            RoundActivityStatus phaseStatus,
            Clock clock
    ) {
        this.plugin = plugin;
        this.townyAdapter = townyAdapter;
        this.sidebarService = sidebarService;
        this.banner = banner;
        this.settings = settings;
        this.phaseStatus = phaseStatus;
        this.clock = clock;
        this.bossBars = new CaptureBossBars(plugin.getServer());
    }

    public void start() {
        banner.ensurePresent();
        beacon.ensurePresent(banner.location());
        publishControl();
        this.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tick,
                TICK_PERIOD_TICKS,
                TICK_PERIOD_TICKS
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sessions.clear();
        bossBars.removeAll();
    }

    @Override
    public boolean isActiveParticipant(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    @Override
    public Optional<Team> controllingTeam() {
        return control.controllingTeam();
    }

    @Override
    public int controllerCount() {
        return control.controllerCount();
    }

    @Override
    public Set<UUID> controllerIds() {
        return control.controllerIds();
    }

    /** Everyone currently contributing capture time, including completed controllers. */
    public Set<UUID> bannerParticipantIds() {
        java.util.LinkedHashSet<UUID> playerIds = new java.util.LinkedHashSet<>(sessions.keySet());
        playerIds.addAll(control.controllerIds());
        return Set.copyOf(playerIds);
    }

    @Override
    public void clearParticipation(Player player) {
        sessions.remove(player.getUniqueId());
        bossBars.remove(player);
        if (control.removeController(player.getUniqueId())) {
            publishControl();
        }
    }

    /** Cancels every session and surrenders the banner. */
    public void resetControl() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            sessions.remove(playerId);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                bossBars.remove(player);
            }
        }
        control.reset();
        publishControl();
    }

    /** Cancels in-progress sessions while preserving completed controllers. */
    public void cancelInProgressSessions() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            sessions.remove(playerId);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                bossBars.remove(player);
            }
        }
    }

    public CaptureBanner banner() {
        return banner;
    }

    /** Atomically switches capture geometry after a prepared round is loaded. */
    public void rebind(Location capturePoint, int radiusBlocks) {
        resetControl();
        banner.moveTo(capturePoint);
        settings = new CaptureSettings(radiusBlocks, settings.sessionDuration());
        suspended = false;
        banner.ensurePresent();
        beacon.ensurePresent(banner.location());
    }

    /**
     * Stops all capture activity while the arena is being rebuilt. Existing
     * sessions are discarded and no new ones start until
     * {@link #resumeAfterReset()}.
     */
    public void suspendForReset() {
        suspended = true;
        resetControl();
    }

    /** Re-enables captures and rebuilds the banner the restore overwrote. */
    public void resumeAfterReset() {
        suspended = false;
        banner.ensurePresent();
        beacon.ensurePresent(banner.location());
    }

    /** Moves the capture point for the active map. Progress and control are tied to
     * the old location, so both are discarded. The admin command persists the
     * map-specific location in runtime-map-overrides.yml. */
    public void relocateBanner(Location destination) {
        resetControl();
        banner.moveTo(destination);
        banner.ensurePresent();
        beacon.ensurePresent(banner.location());

    }

    private void tick() {
        if (suspended) {
            // Restoring tiles would fight with rebuilding the banner.
            return;
        }
        if (!phaseStatus.isActive()) {
            cancelInProgressSessions();
            return;
        }
        banner.ensurePresent();
        beacon.ensurePresent(banner.location());
        Location bannerLocation = banner.location();
        Instant now = clock.instant();
        evaluateExistingSessions(bannerLocation, now);
        evaluateNewSessions(bannerLocation, now);
    }

    private void evaluateExistingSessions(Location bannerLocation, Instant now) {
        for (CaptureSession session : List.copyOf(sessions.values())) {
            Player player = plugin.getServer().getPlayer(session.playerId());
            if (player == null) {
                sessions.remove(session.playerId());
                continue;
            }

            Team team = townyAdapter.getPlayerTeam(player).orElse(null);
            if (team != session.side() || !isEligible(player, team, bannerLocation)) {
                sessions.remove(session.playerId());
                bossBars.remove(player);
                player.sendMessage(Component.text(
                        "You are no longer holding the banner. Your capture progress was lost.",
                        NamedTextColor.RED
                ));
                continue;
            }

            if (session.isComplete(now)) {
                completeSession(player, session);
            } else {
                showProgress(player, session, now);
            }
        }
    }

    private void evaluateNewSessions(Location bannerLocation, Instant now) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (sessions.containsKey(playerId) || control.isController(playerId)) {
                continue;
            }

            Team team = townyAdapter.getPlayerTeam(player).orElse(null);
            if (!isEligible(player, team, bannerLocation)) {
                continue;
            }

            CaptureSession session = CaptureSession.starting(playerId, team, now, settings.sessionDuration());
            sessions.put(playerId, session);
            player.sendMessage(Component.text(
                    "You are in the process of capturing the banner.",
                    NamedTextColor.GREEN
            ));
            showProgress(player, session, now);
        }
    }

    private void completeSession(Player player, CaptureSession session) {
        sessions.remove(session.playerId());
        bossBars.remove(player);

        CaptureControl.Outcome outcome = control.completeSession(session.playerId(), session.side());
        publishControl();

        player.sendMessage(Component.text("You captured the banner.", NamedTextColor.GREEN));
        if (outcome == CaptureControl.Outcome.CONTROLLER_ADDED) {
            return;
        }

        String captor = session.side().defaultDisplayName();
        String announcement = outcome == CaptureControl.Outcome.CONTROL_REVERSED
                ? captor + " took the banner from " + session.side().opponent().defaultDisplayName() + "!"
                : captor + " now controls the banner!";
        plugin.getServer().broadcast(Component.text(announcement, NamedTextColor.GOLD));
    }

    private void showProgress(Player player, CaptureSession session, Instant now) {
        if (!bossBarVisible.test(player.getUniqueId())) {
            bossBars.remove(player);
            return;
        }
        Component name = Component.text(
                "Banner Cap Timer: " + formatRemainingMinutes(session.remaining(now)) + " minutes",
                NamedTextColor.YELLOW
        );
        bossBars.update(player, name, session.progress(now), BossBar.Color.YELLOW);
    }

    static String formatRemainingMinutes(Duration remaining) {
        BigDecimal minutes = BigDecimal.valueOf(Math.max(0L, remaining.toMillis()))
                .divide(BigDecimal.valueOf(60_000L), 1, RoundingMode.HALF_UP);
        return minutes.toPlainString();
    }

    /** Binds the shared active-combat eligibility policy. */
    public void setBattlefieldFighterCheck(java.util.function.Predicate<Player> check) {
        this.battlefieldFighter = java.util.Objects.requireNonNull(check, "check");
    }

    /** Applies player-owned boss-bar visibility immediately to active capture sessions. */
    public void setBossBarVisible(java.util.function.Predicate<java.util.UUID> check) {
        this.bossBarVisible = java.util.Objects.requireNonNull(check, "check");
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && !bossBarVisible.test(playerId)) {
                bossBars.remove(player);
            }
        }
    }

    private boolean isEligible(Player player, Team team, Location bannerLocation) {
        if (!battlefieldFighter.test(player)) {
            return false;
        }
        return CaptureEligibility.isEligible(
                player.isOnline(),
                player.isDead(),
                player.getGameMode() == GameMode.SPECTATOR,
                player.isFlying(),
                player.isGliding(),
                team != null,
                CaptureGeometry.isWithinCaptureZone(player.getLocation(), bannerLocation, settings.radiusBlocks())
        );
    }

    private void publishControl() {
        sidebarService.updateBannerControl(control.controllingTeam().orElse(null), control.controllerCount());
    }

}
