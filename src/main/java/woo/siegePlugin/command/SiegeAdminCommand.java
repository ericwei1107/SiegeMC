package woo.siegePlugin.command;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.arena.ArenaRegion;
import woo.siegePlugin.arena.ArenaRegionSettings;
import woo.siegePlugin.arena.ArenaResetService;
import woo.siegePlugin.arena.ArenaSnapshotService;
import woo.siegePlugin.capture.CaptureService;
import woo.siegePlugin.cycle.ActivityCycleService;
import woo.siegePlugin.kit.KitService;
import woo.siegePlugin.kit.KitSnapshot;
import woo.siegePlugin.score.ScoringService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Everything under {@code /siege admin}. */
public final class SiegeAdminCommand {

    static final String PERMISSION = "siege.admin";
    static final String RESET_SCORES_PERMISSION = "siege.admin.resetscores";

    private static final List<String> SUBCOMMANDS = List.of(
            "setbanner",
            "resetscores",
            "break",
            "resume",
            "setresetpos1",
            "setresetpos2",
            "savesnapshot",
            "savekit",
            "resetmap"
    );

    private final JavaPlugin plugin;
    private final CaptureService captureService;
    private final ScoringService scoringService;
    private final ArenaSnapshotService snapshotService;
    private final ArenaResetService resetService;
    private final ActivityCycleService activityCycleService;
    private final KitService kitService;
    private final Logger logger;

    public SiegeAdminCommand(
            JavaPlugin plugin,
            CaptureService captureService,
            ScoringService scoringService,
            ArenaSnapshotService snapshotService,
            ArenaResetService resetService,
            ActivityCycleService activityCycleService,
            KitService kitService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.captureService = captureService;
        this.scoringService = scoringService;
        this.snapshotService = snapshotService;
        this.resetService = resetService;
        this.activityCycleService = activityCycleService;
        this.kitService = kitService;
        this.logger = logger;
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("You do not have permission to use siege admin commands.");
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "setbanner" -> handleSetBanner(sender);
            case "resetscores" -> handleResetScores(sender, label, args);
            case "break" -> handleBreak(sender, label, args);
            case "resume" -> handleResume(sender, label, args);
            case "setresetpos1" -> handleSetResetCorner(sender, "pos1");
            case "setresetpos2" -> handleSetResetCorner(sender, "pos2");
            case "savesnapshot" -> handleSaveSnapshot(sender, label, args);
            case "savekit" -> handleSaveKit(sender, label, args);
            case "resetmap" -> handleResetMap(sender);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(name -> !name.equals("resetscores") || sender.hasPermission(RESET_SCORES_PERMISSION))
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("resetscores")
                && !sender.hasPermission(RESET_SCORES_PERMISSION)) {
            return List.of();
        }
        if (args.length == 3
                && List.of("resetscores", "savesnapshot", "savekit")
                .contains(args[1].toLowerCase(Locale.ROOT))) {
            return "confirm".startsWith(args[2].toLowerCase(Locale.ROOT)) ? List.of("confirm") : List.of();
        }
        return List.of();
    }

    private boolean handleSetBanner(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can set the capture banner location.");
            return true;
        }

        try {
            captureService.relocateBanner(player.getLocation());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not move the capture banner", exception);
            player.sendMessage("The capture banner could not be moved. Check the server log.");
            return true;
        }

        String description = captureService.banner().describe();
        logger.info("Capture banner moved to " + description + " by " + player.getName() + ".");
        player.sendMessage("Capture banner set to " + description + ". All capture progress was reset.");
        return true;
    }

    private boolean handleResetScores(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(RESET_SCORES_PERMISSION)) {
            sender.sendMessage("You do not have permission to reset siege scores.");
            return true;
        }
        if (args.length != 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage("This clears the eternal siege score for both teams and cannot be undone.");
            sender.sendMessage("Run /" + label + " admin resetscores confirm to proceed.");
            return true;
        }

        sender.sendMessage("Resetting siege scores...");
        scoringService.resetScores((reset, failure) -> {
            if (failure != null) {
                sender.sendMessage("Siege scores could not be reset. Check the server log.");
                return;
            }
            logger.info("Siege scores reset by " + sender.getName() + ".");
            sender.sendMessage("Siege scores reset to zero for both teams.");
        });
        return true;
    }

    private boolean handleBreak(CommandSender sender, String label, String[] args) {
        if (args.length > 3) {
            sender.sendMessage("Usage: /" + label + " admin break [seconds]");
            return true;
        }
        if (activityCycleService == null) {
            sender.sendMessage("The activity cycle is unavailable.");
            return true;
        }
        Duration duration = activityCycleService.configuredBreakDuration();
        if (args.length == 3) {
            try {
                long seconds = Long.parseLong(args[2]);
                if (seconds <= 0L) {
                    throw new NumberFormatException("non-positive");
                }
                duration = Duration.ofSeconds(seconds);
            } catch (NumberFormatException | ArithmeticException exception) {
                sender.sendMessage("Break duration must be a positive number of seconds.");
                return true;
            }
        }

        ActivityCycleService.CycleCommandResult result = activityCycleService.forceBreak(duration);
        switch (result) {
            case BREAK_STARTED -> sender.sendMessage("Siege banner control is now on break.");
            case BREAK_EXTENDED -> sender.sendMessage("The current siege break was extended.");
            case DISABLED -> sender.sendMessage("The activity cycle is disabled in config.");
            default -> throw new IllegalStateException("Unexpected break result: " + result);
        }
        return true;
    }

    private boolean handleResume(CommandSender sender, String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " admin resume");
            return true;
        }
        if (activityCycleService == null) {
            sender.sendMessage("The activity cycle is unavailable.");
            return true;
        }
        ActivityCycleService.CycleCommandResult result = activityCycleService.resume();
        switch (result) {
            case RESUMED -> sender.sendMessage("Siege banner control is active again.");
            case ALREADY_ACTIVE -> sender.sendMessage("Siege banner control is already active.");
            case DISABLED -> sender.sendMessage("The activity cycle is disabled in config.");
            default -> throw new IllegalStateException("Unexpected resume result: " + result);
        }
        return true;
    }

    private boolean handleSetResetCorner(CommandSender sender, String corner) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can set an arena reset corner.");
            return true;
        }

        FileConfiguration config = plugin.getConfig();
        ArenaRegionSettings.saveCorner(config, corner, player.getLocation());
        plugin.saveConfig();

        Optional<ArenaRegion> region = ArenaRegionSettings.fromConfig(config, plugin.getServer());
        sender.sendMessage("Arena reset " + corner + " set to "
                + player.getLocation().getBlockX() + ", "
                + player.getLocation().getBlockY() + ", "
                + player.getLocation().getBlockZ() + ".");
        region.ifPresentOrElse(
                complete -> sender.sendMessage("Region is now " + complete.blockCount()
                        + " blocks across " + complete.tileCount() + " tiles."),
                () -> sender.sendMessage("Set the other corner before saving a snapshot.")
        );
        return true;
    }

    private boolean handleSaveSnapshot(CommandSender sender, String label, String[] args) {
        Optional<ArenaRegion> region = ArenaRegionSettings.fromConfig(plugin.getConfig(), plugin.getServer());
        if (region.isEmpty()) {
            sender.sendMessage("Set both corners first with /" + label + " admin setresetpos1 and setresetpos2.");
            return true;
        }

        ArenaRegion arena = region.orElseThrow();
        if (args.length != 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage("This overwrites the saved clean-map snapshot with the arena's CURRENT state.");
            sender.sendMessage("Region: " + arena.blockCount() + " blocks in " + arena.tileCount() + " tiles.");
            sender.sendMessage("Only run this on a clean map. Use /" + label + " admin savesnapshot confirm.");
            return true;
        }

        snapshotService.capture(arena, sender::sendMessage);
        return true;
    }

    private boolean handleSaveKit(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can capture the default siege kit.");
            return true;
        }
        if (args.length != 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage("This replaces the server-wide siege kit with your CURRENT inventory, armor, and offhand.");
            sender.sendMessage("Run /" + label + " admin savekit confirm to proceed.");
            return true;
        }

        try {
            KitSnapshot snapshot = KitSnapshot.fromInventory(player.getInventory());
            snapshot.saveToConfig(plugin.getConfig());
            plugin.saveConfig();
            kitService.replaceSnapshot(snapshot);
            logger.info("Default siege kit snapshot replaced by " + player.getName() + ".");
            player.sendMessage("Default siege kit saved and activated. Use /siege kit to verify it.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("The default siege kit was not saved: " + exception.getMessage());
        }
        return true;
    }

    private boolean handleResetMap(CommandSender sender) {
        resetService.scheduleReset(sender::sendMessage);
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " admin <" + String.join("|", SUBCOMMANDS) + ">");
        if (!resetService.hasSnapshot()) {
            sender.sendMessage("WARNING: no arena snapshot exists, so /" + label + " admin resetmap is disabled.");
        }
    }
}
