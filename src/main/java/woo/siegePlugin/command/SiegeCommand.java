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
import woo.siegePlugin.economy.CurrencyService;
import woo.siegePlugin.economy.ShopMenu;
import woo.siegePlugin.kit.KitEditorListener;
import woo.siegePlugin.state.PlayerStateTransitionService;

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
    private final PlayerStateTransitionService playerStateTransitionService;
    private final SiegeAdminCommand adminCommand;
    private final CurrencyService currencyService;
    private final KitEditorListener kitEditorListener;
    private final Logger logger;

    public SiegeCommand(
            TownyAdapter townyAdapter,
            TeamSwitchService teamSwitchService,
            TeamDisplayService teamDisplayService,
            PlayerStateTransitionService playerStateTransitionService,
            SiegeAdminCommand adminCommand,
            CurrencyService currencyService,
            KitEditorListener kitEditorListener,
            Logger logger
    ) {
        this.townyAdapter = townyAdapter;
        this.teamSwitchService = teamSwitchService;
        this.teamDisplayService = teamDisplayService;
        this.playerStateTransitionService = playerStateTransitionService;
        this.adminCommand = adminCommand;
        this.currencyService = currencyService;
        this.kitEditorListener = kitEditorListener;
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("shop")) {
            return handleShop(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("kit")) {
            return handleKit(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("spectate")) {
            return handleSpectate(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("rejoin")) {
            return handleRejoin(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("join")) {
            return handleJoin(sender);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("lobby")) {
            return handleLobby(sender);
        }

        sender.sendMessage("Usage: /" + label
                + " <team|switch <red|blue>|shop|kit|spectate|rejoin|join|lobby>");
        return true;
    }

    private boolean handleSpectate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can spectate a siege.");
            return true;
        }
        if (!player.hasPermission("siege.spectate")) {
            player.sendMessage("You do not have permission to spectate the siege.");
            return true;
        }
        switch (playerStateTransitionService.enterSpectator(player)) {
            case STARTED -> player.sendMessage("You are now spectating. Use /siege rejoin to return to the battle.");
            case SPECTATOR_CONTEXT -> player.sendMessage("You are already spectating the siege.");
            case COMBAT_TAGGED -> player.sendMessage("You cannot spectate while combat-tagged.");
            case CAPTURE_SESSION_ACTIVE -> player.sendMessage("You cannot spectate during an active capture session.");
            case TRANSITION_IN_PROGRESS -> player.sendMessage("A siege transition is already in progress.");
            default -> player.sendMessage("Spectator mode could not be entered. Please contact an administrator.");
        }
        return true;
    }

    private boolean handleRejoin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can rejoin the siege.");
            return true;
        }
        if (!player.hasPermission("siege.rejoin")) {
            player.sendMessage("You do not have permission to rejoin the siege.");
            return true;
        }
        try {
            switch (playerStateTransitionService.rejoinSpectator(player)) {
                case STARTED -> {
                    // The transition service confirms completion after the
                    // durable inventory restore and team-spawn teleport.
                }
                case NOT_SPECTATING -> player.sendMessage("You must be in SpectatorTown before you can rejoin the siege.");
                case TRANSITION_IN_PROGRESS -> player.sendMessage("A siege transition is already in progress.");
                default -> player.sendMessage("You could not rejoin the siege. Please contact an administrator.");
            }
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.SEVERE, "Could not rejoin " + player.getName() + " to the siege.", exception);
            player.sendMessage("You could not rejoin the siege. Please contact an administrator.");
        }
        return true;
    }

    private boolean handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can join the siege.");
            return true;
        }
        if (!player.hasPermission("siege.join")) {
            player.sendMessage("You do not have permission to join the siege.");
            return true;
        }

        try {
            switch (playerStateTransitionService.enterSiegeFromLobby(player)) {
                case STARTED -> {
                    // The service sends the success message once persistence,
                    // teleport, and inventory restoration have all completed.
                }
                case ALREADY_IN_SIEGE -> player.sendMessage("You are already in the siege. Use /siege lobby first.");
                case NOT_IN_LOBBY -> player.sendMessage("You must be in the lobby before joining the siege.");
                case SPECTATOR_CONTEXT -> player.sendMessage("Use /siege rejoin to return from spectator mode.");
                case TRANSITION_IN_PROGRESS -> player.sendMessage("A siege transition is already in progress.");
                default -> player.sendMessage("You could not join the siege. Please contact an administrator.");
            }
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.SEVERE, "Could not start a siege join for " + player.getName(), exception);
            player.sendMessage("You could not join the siege. Please contact an administrator.");
        }
        return true;
    }

    private boolean handleLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can return to the lobby.");
            return true;
        }
        if (!player.hasPermission("siege.lobby")) {
            player.sendMessage("You do not have permission to return to the lobby.");
            return true;
        }

        try {
            switch (playerStateTransitionService.returnToLobby(player)) {
                case STARTED -> {
                    // The service reports success only after the inventory is durable.
                }
                case ALREADY_IN_LOBBY -> player.sendMessage("You are already in the lobby.");
                case SPECTATOR_CONTEXT -> player.sendMessage("Spectators must use /siege rejoin to return to the battle.");
                case COMBAT_TAGGED -> player.sendMessage("You cannot return to the lobby while combat-tagged.");
                case CAPTURE_SESSION_ACTIVE -> player.sendMessage("You cannot return to the lobby during an active capture session.");
                case TRANSITION_IN_PROGRESS -> player.sendMessage("A siege transition is already in progress.");
                default -> player.sendMessage("You could not return to the lobby. Please contact an administrator.");
            }
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.SEVERE, "Could not start a lobby transition for " + player.getName(), exception);
            player.sendMessage("You could not return to the lobby. Please contact an administrator.");
        }
        return true;
    }

    private boolean handleKit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can edit their siege kit.");
            return true;
        }
        if (!player.hasPermission("siege.kit")) {
            player.sendMessage("You do not have permission to edit your siege kit.");
            return true;
        }

        kitEditorListener.open(player);
        return true;
    }

    private boolean handleShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can open the siege shop.");
            return true;
        }
        if (!player.hasPermission("siege.shop")) {
            player.sendMessage("You do not have permission to use the siege shop.");
            return true;
        }

        ShopMenu.open(player, currencyService);
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
            if (sender.hasPermission("siege.shop") && "shop".startsWith(prefix)) {
                suggestions.add("shop");
            }
            if (sender.hasPermission("siege.kit") && "kit".startsWith(prefix)) {
                suggestions.add("kit");
            }
            if (sender.hasPermission("siege.spectate") && "spectate".startsWith(prefix)) {
                suggestions.add("spectate");
            }
            if (sender.hasPermission("siege.rejoin") && "rejoin".startsWith(prefix)) {
                suggestions.add("rejoin");
            }
            if (sender.hasPermission("siege.join") && "join".startsWith(prefix)) {
                suggestions.add("join");
            }
            if (sender.hasPermission("siege.lobby") && "lobby".startsWith(prefix)) {
                suggestions.add("lobby");
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
