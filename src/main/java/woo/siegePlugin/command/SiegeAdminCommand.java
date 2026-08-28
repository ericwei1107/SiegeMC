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
import woo.siegePlugin.storage.PotionStorage;
import woo.siegePlugin.storage.PotionStorageService;
import woo.siegePlugin.storage.PotionStorageTemplates;
import woo.siegePlugin.team.Team;

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
            "resetmap",
            "supply"
    );

    private final JavaPlugin plugin;
    private final CaptureService captureService;
    private final ScoringService scoringService;
    private final ArenaSnapshotService snapshotService;
    private final ArenaResetService resetService;
    private final ActivityCycleService activityCycleService;
    private final KitService kitService;
    private final Logger logger;
    private final PotionStorageService potionStorageService;

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
        this(
                plugin,
                captureService,
                scoringService,
                snapshotService,
                resetService,
                activityCycleService,
                kitService,
                logger,
                null
        );
    }

    public SiegeAdminCommand(
            JavaPlugin plugin,
            CaptureService captureService,
            ScoringService scoringService,
            ArenaSnapshotService snapshotService,
            ArenaResetService resetService,
            ActivityCycleService activityCycleService,
            KitService kitService,
            Logger logger,
            PotionStorageService potionStorageService
    ) {
        this.plugin = plugin;
        this.captureService = captureService;
        this.scoringService = scoringService;
        this.snapshotService = snapshotService;
        this.resetService = resetService;
        this.activityCycleService = activityCycleService;
        this.kitService = kitService;
        this.logger = logger;
        this.potionStorageService = potionStorageService;
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
            case "supply" -> handleSupply(sender, label, args);
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
        if (args[1].equalsIgnoreCase("supply")) {
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return List.of("register", "unregister", "list", "info").stream()
                        .filter(option -> option.startsWith(prefix))
                        .toList();
            }
            if (args.length == 4 && args[2].equalsIgnoreCase("register")) {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                return List.of("red", "blue").stream().filter(team -> team.startsWith(prefix)).toList();
            }
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

    private boolean handleSupply(CommandSender sender, String label, String[] args) {
        if (potionStorageService == null) {
            sender.sendMessage("Potion storage is unavailable while SiegePlugin is starting.");
            return true;
        }
        if (args.length < 3) {
            sendSupplyUsage(sender, label);
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "register" -> handleSupplyRegister(sender, label, args);
            case "unregister" -> handleSupplyUnregister(sender, label, args);
            case "list" -> handleSupplyList(sender, label, args);
            case "info" -> handleSupplyInfo(sender, label, args);
            default -> {
                sendSupplyUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean handleSupplyRegister(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can register a potion storage.");
            return true;
        }
        if (args.length != 4) {
            sender.sendMessage("Usage: /" + label + " admin supply register <red|blue>");
            return true;
        }
        Team team = Team.fromInput(args[3]).orElse(null);
        if (team == null) {
            sender.sendMessage("Unknown team. Choose red or blue.");
            return true;
        }
        PotionStorageService.RegistrationResult result = potionStorageService.register(player, team);
        sender.sendMessage(result.message());
        if (result.success()) {
            logger.info("Potion storage registered by " + player.getName() + " for " + team + ".");
        }
        return true;
    }

    private boolean handleSupplyUnregister(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can unregister a potion storage.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " admin supply unregister");
            return true;
        }
        PotionStorageService.UnregistrationResult result = potionStorageService.unregister(player);
        sender.sendMessage(result.message());
        if (result.success()) {
            logger.info("Potion storage unregistered by " + player.getName() + ".");
        }
        return true;
    }

    private boolean handleSupplyList(CommandSender sender, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " admin supply list");
            return true;
        }
        int count = 0;
        for (PotionStorage storage : potionStorageService.storages()) {
            count++;
            sender.sendMessage("- " + storage.team().defaultDisplayName() + " "
                    + PotionStorageTemplates.label(storage.potion()) + " at "
                    + storage.key().first().worldName() + " "
                    + storage.key().first().x() + ", " + storage.key().first().y() + ", "
                    + storage.key().first().z());
        }
        if (count == 0) {
            sender.sendMessage("No potion storages are registered.");
        }
        return true;
    }

    private boolean handleSupplyInfo(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can inspect a potion storage.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " admin supply info");
            return true;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        PotionStorage storage = target == null ? null : potionStorageService.find(target).orElse(null);
        if (storage == null) {
            sender.sendMessage("Look directly at a registered potion storage within 6 blocks.");
            return true;
        }
        sender.sendMessage(storage.team().defaultDisplayName() + " potion storage: "
                + PotionStorageTemplates.label(storage.potion()) + ".");
        return true;
    }

    private void sendSupplyUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " admin supply <register <red|blue>|unregister|list|info>");
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " admin <" + String.join("|", SUBCOMMANDS) + ">");
        if (!resetService.hasSnapshot()) {
            sender.sendMessage("WARNING: no arena snapshot exists, so /" + label + " admin resetmap is disabled.");
        }
    }
}
