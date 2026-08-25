package woo.siegePlugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.capture.CaptureSessionStatus;
import woo.siegePlugin.command.SiegeCommand;
import woo.siegePlugin.combat.CombatLogAdapter;
import woo.siegePlugin.display.TeamDisplayListener;
import woo.siegePlugin.display.TeamDisplayService;
import woo.siegePlugin.display.TeamIdentityColors;
import woo.siegePlugin.display.SidebarService;
import woo.siegePlugin.display.SidebarSettings;
import woo.siegePlugin.team.TeamAssignmentListener;
import woo.siegePlugin.team.TeamAssignmentService;
import woo.siegePlugin.team.TeamSpawnLocations;
import woo.siegePlugin.team.TeamSwitchService;
import woo.siegePlugin.team.TownyAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SiegePlugin extends JavaPlugin {

    private TownyAdapter townyAdapter;
    private TeamAssignmentService teamAssignmentService;
    private TeamSwitchService teamSwitchService;
    private TeamDisplayService teamDisplayService;
    private SidebarService sidebarService;

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
        Plugin combatLog = Objects.requireNonNull(getServer().getPluginManager().getPlugin("CombatLog"));
        this.teamSwitchService = new TeamSwitchService(
                townyAdapter,
                CombatLogAdapter.fromPlugin(combatLog),
                CaptureSessionStatus.noActiveSessions(),
                TeamSpawnLocations.fromConfig(getConfig(), getServer())
        );
        registerCommands();
        registerListeners();
        teamDisplayService.initializeOnlinePlayers();

        getLogger().info("SiegeMC enabled — configuration and Towny integration validated successfully.");
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

        String world = config.getString("capture-point.world");
        if (world == null || world.isBlank()) {
            problems.add("capture-point.world is missing or empty");
        } else if (getServer().getWorld(world) == null) {
            // Note: this check only works if the world is already loaded when
            // this plugin enables. If load order ever becomes a problem, this
            // check may need to move to a later point (e.g. a delayed task).
            problems.add("capture-point.world '" + world + "' is not a loaded world");
        }

        if (!config.isSet("capture-point.x") || !config.isSet("capture-point.y") || !config.isSet("capture-point.z")) {
            problems.add("capture-point.x/y/z must all be set");
        }

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
    }
}
