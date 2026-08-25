package woo.siegePlugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.capture.CaptureBanner;
import woo.siegePlugin.capture.CaptureListener;
import woo.siegePlugin.capture.CaptureService;
import woo.siegePlugin.capture.CaptureSettings;
import woo.siegePlugin.command.SiegeCommand;
import woo.siegePlugin.combat.CombatLogAdapter;
import woo.siegePlugin.display.TeamDisplayListener;
import woo.siegePlugin.display.TeamDisplayService;
import woo.siegePlugin.display.TeamIdentityColors;
import woo.siegePlugin.display.SidebarService;
import woo.siegePlugin.display.SidebarSettings;
import woo.siegePlugin.cycle.SiegePhaseStatus;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.PlayerInventoryDao;
import woo.siegePlugin.persistence.SiegeDatabase;
import woo.siegePlugin.score.ScoringService;
import woo.siegePlugin.score.ScoringSettings;
import woo.siegePlugin.state.KitLoadoutProvider;
import woo.siegePlugin.state.PlayerStateTransitionListener;
import woo.siegePlugin.state.PlayerStateTransitionService;
import woo.siegePlugin.state.PlayerStateTransitions;
import woo.siegePlugin.state.SpectatorResidencyHandler;
import woo.siegePlugin.team.TeamAssignmentListener;
import woo.siegePlugin.team.TeamAssignmentService;
import woo.siegePlugin.team.TeamSpawnLocations;
import woo.siegePlugin.team.TeamSwitchService;
import woo.siegePlugin.team.TownyAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class SiegePlugin extends JavaPlugin {

    private TownyAdapter townyAdapter;
    private TeamAssignmentService teamAssignmentService;
    private TeamSwitchService teamSwitchService;
    private TeamDisplayService teamDisplayService;
    private SidebarService sidebarService;
    private CaptureService captureService;
    private ScoringService scoringService;
    private SiegeDatabase database;
    private PlayerStateTransitionService playerStateTransitionService;
    private PlayerStateTransitions playerStateTransitions;

    @Override
    public void onEnable() {
        // Ensures plugins/SiegeMC/config.yml exists, copying the bundled
        // default from resources/config.yml if it's missing. Does NOT
        // overwrite an existing file.
        saveDefaultConfig();

        List<String> problems = validateStartup();

        if (!problems.isEmpty()) {
            getLogger().severe("SiegeMC failed to start due to invalid configuration:");
            for (String problem : problems) {
                getLogger().severe(" - " + problem);
            }
            getLogger().severe("Fix config.yml and restart the server. Plugin will now disable.");
            getServer().getPluginManager().disablePlugin(this);
            return; // stop here — nothing below this line should assume config is valid
        }

        this.townyAdapter = TownyAdapter.fromConfig(getConfig());
        initializePlayerStateTransitions();
        this.teamAssignmentService = new TeamAssignmentService(townyAdapter);
        TeamIdentityColors identityColors = TeamIdentityColors.fromConfig(getConfig());
        this.teamDisplayService = new TeamDisplayService(
                getServer(),
                townyAdapter,
                identityColors
        );
        this.sidebarService = new SidebarService(
                getServer(),
                teamDisplayService,
                SidebarSettings.fromConfig(getConfig()),
                identityColors
        );
        teamDisplayService.setScoreboardReadyHandler(sidebarService::initializePlayer);
        this.captureService = new CaptureService(
                this,
                townyAdapter,
                sidebarService,
                CaptureBanner.fromConfig(getConfig(), getServer(), getLogger()),
                CaptureSettings.fromConfig(getConfig())
        );
        Plugin combatLog = Objects.requireNonNull(getServer().getPluginManager().getPlugin("CombatLog"));
        this.teamSwitchService = new TeamSwitchService(
                townyAdapter,
                CombatLogAdapter.fromPlugin(combatLog),
                captureService,
                TeamSpawnLocations.fromConfig(getConfig(), getServer())
        );
        this.scoringService = new ScoringService(
                this,
                new MatchScoreDao(database),
                captureService,
                sidebarService,
                SiegePhaseStatus.alwaysActive(),
                ScoringSettings.fromConfig(getConfig())
        );
        registerCommands();
        registerListeners();
        teamDisplayService.initializeOnlinePlayers();
        captureService.start();
        scoringService.start();

        getLogger().info("SiegeMC enabled — configuration and Towny integration validated successfully.");
    }

    @Override
    public void onDisable() {
        if (captureService != null) {
            captureService.stop();
        }
        if (scoringService != null) {
            scoringService.stop();
        }
        if (playerStateTransitionService != null) {
            playerStateTransitionService.shutdown();
        }
        if (database != null) {
            try {
                database.close();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Could not flush the SiegeMC database during shutdown.", exception);
            }
        }
    }

    public PlayerStateTransitions getPlayerStateTransitions() {
        return Objects.requireNonNull(
                playerStateTransitions,
                "Player state transitions are unavailable while SiegeMC is disabled"
        );
    }

    /**
     * Checks every config value the plugin actually depends on.
     * Returns a list of human-readable problems — empty list means config is valid.
     */
    private List<String> validateStartup() {
        List<String> problems = new ArrayList<>();
        FileConfiguration config = getConfig();

        String redTown = config.getString("teams.red.town");
        if (redTown == null || redTown.isBlank()) {
            problems.add("teams.red.town is missing or empty");
        }

        String blueTown = config.getString("teams.blue.town");
        if (blueTown == null || blueTown.isBlank()) {
            problems.add("teams.blue.town is missing or empty");
        }

        if (redTown != null && redTown.equalsIgnoreCase(blueTown)) {
            problems.add("teams.red.town and teams.blue.town must be different towns");
        }

        problems.addAll(CaptureSettings.findConfigurationProblems(config, getServer()));
        problems.addAll(ScoringSettings.findConfigurationProblems(config));

        Plugin towny = getServer().getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            problems.add("Towny is missing or not enabled");
        } else {
            problems.addAll(TownyAdapter.findConfigurationProblems(config));
        }

        problems.addAll(TeamSpawnLocations.findConfigurationProblems(config, getServer()));
        problems.addAll(TeamIdentityColors.findConfigurationProblems(config));
        problems.addAll(SidebarSettings.findConfigurationProblems(config));

        Plugin combatLog = getServer().getPluginManager().getPlugin("CombatLog");
        if (combatLog == null || !combatLog.isEnabled()) {
            problems.add("CombatLog is missing or not enabled");
        } else {
            problems.addAll(CombatLogAdapter.findIntegrationProblems(combatLog));
        }

        return problems;
    }

    private void registerCommands() {
        PluginCommand siegeCommand = Objects.requireNonNull(
                getCommand("siege"),
                "The siege command is missing from plugin.yml"
        );
        SiegeCommand commandHandler = new SiegeCommand(
                townyAdapter,
                teamSwitchService,
                teamDisplayService,
                captureService,
                scoringService,
                getLogger()
        );
        siegeCommand.setExecutor(commandHandler);
        siegeCommand.setTabCompleter(commandHandler);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new TeamAssignmentListener(this, teamAssignmentService, teamDisplayService::handleJoin),
                this
        );
        getServer().getPluginManager().registerEvents(
                new TeamDisplayListener(teamDisplayService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PlayerStateTransitionListener(playerStateTransitionService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new CaptureListener(captureService),
                this
        );
    }

    private void initializePlayerStateTransitions() {
        this.database = new SiegeDatabase(getDataFolder().toPath().resolve("siege.db"));
        this.playerStateTransitionService = new PlayerStateTransitionService(
                this,
                new PlayerInventoryDao(database),
                KitLoadoutProvider.empty(),
                SpectatorResidencyHandler.deferredUntilStage4_4l()
        );
        this.playerStateTransitions = new PlayerStateTransitions(getServer());

        database.initialized().whenComplete((ignored, failure) -> {
            if (failure == null) {
                getLogger().info("SiegeMC SQLite persistence initialized.");
                return;
            }

            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            Throwable databaseFailure = cause;
            getLogger().log(Level.SEVERE, "SiegeMC SQLite persistence failed to initialize.", databaseFailure);
            if (!isEnabled()) {
                return;
            }
            getServer().getScheduler().runTask(this, () -> {
                if (isEnabled()) {
                    getLogger().severe("Disabling SiegeMC because durable inventory storage is unavailable.");
                    getServer().getPluginManager().disablePlugin(this);
                }
            });
        });
    }
}
