package woo.siegePlugin.capture;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import woo.siegePlugin.display.SidebarService;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs banner capture sessions against the configured capture point, following
 * SiegeWar's evaluate-existing-then-evaluate-new ordering on a one-second tick.
 */
public final class CaptureService implements CaptureSessionStatus, BannerControlStatus {

    private static final long TICK_PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final TownyAdapter townyAdapter;
    private final SidebarService sidebarService;
    private final CaptureBanner banner;
    private final CaptureBossBars bossBars;
    private final CaptureControl control = new CaptureControl();
    private final Map<UUID, CaptureSession> sessions = new LinkedHashMap<>();
    private final Clock clock;
    private final CaptureSettings settings;

    private BukkitTask task;
    private boolean suspended;
    private Consumer<Player> captureRewardHandler = player -> {
    };

    public CaptureService(
            JavaPlugin plugin,
            TownyAdapter townyAdapter,
            SidebarService sidebarService,
            CaptureBanner banner,
            CaptureSettings settings
    ) {
        this(plugin, townyAdapter, sidebarService, banner, settings, Clock.systemUTC());
    }

    CaptureService(
            JavaPlugin plugin,
            TownyAdapter townyAdapter,
            SidebarService sidebarService,
            CaptureBanner banner,
            CaptureSettings settings,
            Clock clock
    ) {
        this.plugin = plugin;
        this.townyAdapter = townyAdapter;
        this.sidebarService = sidebarService;
        this.banner = banner;
        this.settings = settings;
        this.clock = clock;
        this.bossBars = new CaptureBossBars(plugin.getServer());
    }

    public void start() {
        banner.ensurePresent();
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

    public CaptureBanner banner() {
        return banner;
    }

    /** Called on the server thread whenever a player completes a capture. */
    public void setCaptureRewardHandler(Consumer<Player> handler) {
        this.captureRewardHandler = handler;
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
    }

    /**
     * Moves the capture point and persists it. Progress and control are tied to
     * the old location, so both are discarded.
     */
    public void relocateBanner(Location destination) {
        resetControl();
        banner.moveTo(destination);
        banner.ensurePresent();

        Location moved = banner.location();
        FileConfiguration config = plugin.getConfig();
        config.set(CaptureSettings.WORLD_PATH, moved.getWorld().getName());
        config.set("capture-point.x", moved.getBlockX());
        config.set("capture-point.y", moved.getBlockY());
        config.set("capture-point.z", moved.getBlockZ());
        plugin.saveConfig();
    }

    private void tick() {
        if (suspended) {
            // Restoring tiles would fight with rebuilding the banner.
            return;
        }
        banner.ensurePresent();
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

            // The boss bar appearing is the start notification; a chat message
            // here would repeat every tick for anyone straddling the boundary.
            CaptureSession session = CaptureSession.starting(playerId, team, now, settings.sessionDuration());
            sessions.put(playerId, session);
            showProgress(player, session, now);
        }
    }

    private void completeSession(Player player, CaptureSession session) {
        sessions.remove(session.playerId());
        bossBars.remove(player);

        CaptureControl.Outcome outcome = control.completeSession(session.playerId(), session.side());
        publishControl();

        player.sendMessage(Component.text("You captured the banner.", NamedTextColor.GREEN));
        captureRewardHandler.accept(player);
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
        Component name = Component.text(
                "Capturing banner — " + session.remaining(now).toSeconds() + "s",
                NamedTextColor.WHITE
        );
        bossBars.update(player, name, session.progress(now), bossBarColor(session.side()));
    }

    private boolean isEligible(Player player, Team team, Location bannerLocation) {
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

    private static BossBar.Color bossBarColor(Team team) {
        return team == Team.RED ? BossBar.Color.RED : BossBar.Color.BLUE;
    }
}
