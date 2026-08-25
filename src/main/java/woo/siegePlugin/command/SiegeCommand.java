package woo.siegePlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TeamSwitchResult;
import woo.siegePlugin.team.TeamSwitchService;
import woo.siegePlugin.team.TownyAdapter;
import woo.siegePlugin.display.TeamDisplayService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class SiegeCommand implements CommandExecutor, TabCompleter {

    private final TownyAdapter townyAdapter;
    private final TeamSwitchService teamSwitchService;
    private final TeamDisplayService teamDisplayService;
    private final SiegeAdminCommand adminCommand;
    private final Logger logger;

    public SiegeCommand(
            TownyAdapter townyAdapter,
            TeamSwitchService teamSwitchService,
            TeamDisplayService teamDisplayService,
            SiegeAdminCommand adminCommand,
            Logger logger
    ) {
        this.townyAdapter = townyAdapter;
        this.teamSwitchService = teamSwitchService;
        this.teamDisplayService = teamDisplayService;
        this.adminCommand = adminCommand;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("team")) {
            return handleTeamQuery(sender, label, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("switch")) {
            return handleTeamSwitch(sender, label, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.handle(sender, label, args);
        }

        sender.sendMessage("Usage: /" + label + " <team|switch <red|blue>>");
        return true;
    }

    private boolean handleTeamQuery(CommandSender sender, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /" + label + " team");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can check their siege team.");
            return true;
        }

        if (!player.hasPermission("siege.team")) {
            player.sendMessage("You do not have permission to check your siege team.");
            return true;
        }

        String teamName = townyAdapter.getPlayerTeam(player)
                .map(Team::defaultDisplayName)
                .orElse("no team");
        player.sendMessage("Your siege team is: " + teamName);
        return true;
    }

    private boolean handleTeamSwitch(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can switch siege teams.");
            return true;
        }
        if (!player.hasPermission("siege.switch")) {
            player.sendMessage("You do not have permission to switch siege teams.");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("Usage: /" + label + " switch <red|blue>");
            return true;
        }

        Team destination = Team.fromInput(args[1]).orElse(null);
        if (destination == null) {
            player.sendMessage("Unknown team. Choose red or blue.");
            return true;
        }

        Team previousTeam = townyAdapter.getPlayerTeam(player).orElse(null);
        TeamSwitchResult result;
        try {
            result = teamSwitchService.switchTeam(player, destination);
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.SEVERE, "Team switch failed for " + player.getName(), exception);
            player.sendMessage("Your team could not be changed. Please contact an administrator.");
            return true;
        }

        switch (result.status()) {
            case SWITCHED -> {
                teamDisplayService.handleTeamSwitch(player);
                logger.info("Team switch: player=" + player.getName()
                        + ", from=" + previousTeam
                        + ", to=" + destination
                        + ", timestamp=" + Instant.now());
                if (result.teleported()) {
                    player.sendMessage("You switched to " + destination.defaultDisplayName() + ".");
                } else {
                    player.sendMessage("You switched teams, but teleporting to your team spawn failed. Contact an administrator.");
                    logger.warning("Could not teleport " + player.getName() + " to the " + destination + " spawn.");
                }
            }
            case NO_CURRENT_TEAM -> player.sendMessage("You must be on a siege team before you can switch teams.");
            case ALREADY_ON_TEAM -> player.sendMessage("You are already on " + destination.defaultDisplayName() + ".");
            case COOLDOWN_ACTIVE -> player.sendMessage(
                    "You must wait " + formatDuration(result.cooldownRemaining()) + " before switching again."
            );
            case COMBAT_TAGGED -> player.sendMessage("You cannot switch teams while combat-tagged.");
            case CAPTURE_SESSION_ACTIVE -> player.sendMessage("You cannot switch teams during an active capture session.");
            case WOULD_UNBALANCE_TEAMS -> player.sendMessage("That switch would make the destination team too large.");
        }
        return true;
    }

    static String formatDuration(Duration duration) {
        long totalSeconds = Math.max(1L, (duration.toMillis() + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes == 0L) {
            return seconds + "s";
        }
        return minutes + "m " + seconds + "s";
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("siege.team") && "team".startsWith(prefix)) {
                suggestions.add("team");
            }
            if (sender.hasPermission("siege.switch") && "switch".startsWith(prefix)) {
                suggestions.add("switch");
            }
            if (sender.hasPermission(SiegeAdminCommand.PERMISSION) && "admin".startsWith(prefix)) {
                suggestions.add("admin");
            }
            return suggestions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("switch") && sender.hasPermission("siege.switch")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("red", "blue").stream()
                    .filter(team -> team.startsWith(prefix))
                    .toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.tabComplete(sender, args);
        }
        return List.of();
    }
}
