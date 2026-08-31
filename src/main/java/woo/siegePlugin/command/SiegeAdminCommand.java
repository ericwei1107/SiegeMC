package woo.siegePlugin.command;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.capture.CaptureService;
import woo.siegePlugin.kit.KitService;
import woo.siegePlugin.kit.KitSnapshot;
import woo.siegePlugin.kit.RuntimeKitOverrides;
import woo.siegePlugin.map.MapValidator;
import woo.siegePlugin.map.RuntimeMapOverrides;
import woo.siegePlugin.map.MapCalibrationService;
import woo.siegePlugin.round.ActiveRoundContext;
import woo.siegePlugin.round.RotationCoordinator;
import woo.siegePlugin.storage.PotionStorage;
import woo.siegePlugin.storage.PotionStorageService;
import woo.siegePlugin.storage.PotionStorageTemplates;
import woo.siegePlugin.team.Team;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Commands that mutate durable SiegePlugin administration state. */
public final class SiegeAdminCommand {

    public static final String PERMISSION = "siege.admin";
    private static final List<String> SUBCOMMANDS = List.of("setbanner", "savekit", "supply", "rotation", "map");

    private final JavaPlugin plugin;
    private final CaptureService capture;
    private final KitService kits;
    private final Logger logger;
    private final PotionStorageService storages;
    private final RotationCoordinator rotation;
    private final RuntimeKitOverrides runtimeKitOverrides;
    private final RuntimeMapOverrides runtimeMapOverrides;
    private final MapCalibrationService calibration;

    public SiegeAdminCommand(
            JavaPlugin plugin, CaptureService capture, woo.siegePlugin.score.ScoringService scoring,
            KitService kits, Logger logger, PotionStorageService storages, RotationCoordinator rotation,
            RuntimeKitOverrides runtimeKitOverrides, RuntimeMapOverrides runtimeMapOverrides
    ) {
        this(plugin, capture, scoring, kits, logger, storages, rotation, runtimeKitOverrides, runtimeMapOverrides, null);
    }

    public SiegeAdminCommand(
            JavaPlugin plugin,
            CaptureService capture,
            woo.siegePlugin.score.ScoringService ignoredScoring,
            KitService kits,
            Logger logger,
            PotionStorageService storages,
            RotationCoordinator rotation,
            RuntimeKitOverrides runtimeKitOverrides,
            RuntimeMapOverrides runtimeMapOverrides,
            MapCalibrationService calibration
    ) {
        this.plugin = plugin;
        this.capture = capture;
        this.kits = kits;
        this.logger = logger;
        this.storages = storages;
        this.rotation = rotation;
        this.runtimeKitOverrides = runtimeKitOverrides;
        this.runtimeMapOverrides = runtimeMapOverrides;
        this.calibration = calibration;
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("You do not have permission to use siege admin commands.");
            return true;
        }
        if (args.length < 2) {
            usage(sender, label);
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "setbanner" -> setBanner(sender);
            case "savekit" -> saveKit(sender, label, args);
            case "supply" -> supply(sender, label, args);
            case "rotation" -> rotation(sender, label, args);
            case "map" -> map(sender, label, args);
            default -> {
                usage(sender, label);
                yield true;
            }
        };
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("savekit")) {
            return "confirm".startsWith(args[2].toLowerCase(Locale.ROOT)) ? List.of("confirm") : List.of();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("rotation")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("status", "validate", "retry", "force").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("supply")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("register", "unregister", "list", "info").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("supply")
                && args[2].equalsIgnoreCase("register")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return List.of("red", "blue").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private boolean map(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || calibration == null) { sender.sendMessage("Only an in-game admin can calibrate a map."); return true; }
        if (args.length < 3) { mapUsage(sender, label); return true; }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "calibrate" -> {
                if (args.length != 4) mapUsage(sender, label);
                else if (rotation != null && rotation.activeContext().isPresent()) sender.sendMessage("End or leave the active siege before starting calibration.");
                else calibration.start(player, args[3]);
            }
            case "setspawn" -> { Team team = args.length == 4 ? Team.fromInput(args[3]).orElse(null) : null; if (calibration.activeFor(player).isEmpty() || team == null) sender.sendMessage("Stand in the calibration copy. Usage: /" + label + " admin map setspawn <red|blue>"); else { calibration.setSpawn(team, player.getLocation()); sender.sendMessage(team.defaultDisplayName() + " spawn saved."); } }
            case "corner" -> { int corner = args.length == 4 ? parseCorner(args[3]) : 0; if (calibration.activeFor(player).isEmpty() || corner == 0) sender.sendMessage("Stand in the calibration copy. Usage: /" + label + " admin map corner <1|2>"); else { calibration.setCorner(corner, player.getLocation()); sender.sendMessage("Bounds corner " + corner + " saved."); } }
            case "setbanner" -> { int radius = args.length == 4 ? parsePositive(args[3]) : 8; if (calibration.activeFor(player).isEmpty() || radius == 0) sender.sendMessage("Stand in the calibration copy. Usage: /" + label + " admin map setbanner [radius]"); else { calibration.setBanner(player.getLocation(), radius); sender.sendMessage("Banner position and radius saved."); } }
            case "return" -> sender.sendMessage(calibration.returnToMap(player));
            case "finish" -> sender.sendMessage(calibration.finish(player));
            case "abort" -> sender.sendMessage(calibration.abort(player));
            default -> mapUsage(sender, label);
        }
        return true;
    }
    private static int parseCorner(String value) { return value.equals("1") ? 1 : value.equals("2") ? 2 : 0; }
    private static int parsePositive(String value) { try { int number = Integer.parseInt(value); return number > 0 ? number : 0; } catch (NumberFormatException ignored) { return 0; } }
    private static void mapUsage(CommandSender sender, String label) { sender.sendMessage("Usage: /" + label + " admin map <calibrate <map>|return|setspawn <red|blue>|corner <1|2>|setbanner [radius]|finish|abort>"); }

    private boolean rotation(CommandSender sender, String label, String[] args) {
        if (rotation == null || args.length < 3) {
            sender.sendMessage("Usage: /" + label + " admin rotation <status|validate [map|all]|retry [map]>");
            return true;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "status" -> rotation.statusLines().forEach(sender::sendMessage);
            case "validate" -> {
                String map = args.length >= 4 && !args[3].equalsIgnoreCase("all") ? args[3] : null;
                // Loaded-copy checks copy each template, so the report arrives
                // asynchronously rather than blocking the command thread.
                sender.sendMessage("Validating maps; loaded-copy checks may take a moment…");
                rotation.validate(map, lines -> lines.forEach(sender::sendMessage));
            }
            case "retry", "force" -> {
                String map = args.length >= 4 ? args[3] : null;
                sender.sendMessage(rotation.retry(map)
                        ? "Requested map preparation started."
                        : "Rotation is not recoverable now, or that enabled map is unknown.");
            }
            default -> sender.sendMessage("Usage: /" + label
                    + " admin rotation <status|validate [map|all]|retry [map]>");
        }
        return true;
    }

    /**
     * Moves the banner for the map the admin is standing in and writes the new
     * coordinates into the VPS-owned runtime map overrides.
     *
     * <p>Previously this only moved the runtime banner, so the change was lost
     * the moment the round rotated. It now records an overlay applied whenever
     * the manifest reloads, and refuses outright when the admin is not in the
     * active map — writing template-relative coordinates taken from an unrelated
     * world would silently corrupt the manifest.</p>
     */
    private boolean setBanner(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can set the capture banner location.");
            return true;
        }
        ActiveRoundContext context = rotation == null ? null : rotation.activeContext().orElse(null);
        if (context == null || !player.getWorld().equals(context.world())) {
            player.sendMessage("Stand in the active siege map before moving its capture banner.");
            return true;
        }
        Location target = player.getLocation();
        if (!MapValidator.contains(context.bounds(), target.getX(), target.getZ())) {
            player.sendMessage("That position is outside this map's arena bounds.");
            return true;
        }
        try {
            capture.relocateBanner(target);
            if (runtimeMapOverrides == null) {
                throw new IOException("runtime map overrides are unavailable");
            }
            runtimeMapOverrides.saveCaptureCoordinates(
                    context.map().id(), target.getBlockX() + 0.5D, target.getBlockY(), target.getBlockZ() + 0.5D
            );
            player.sendMessage("Capture banner set to " + capture.banner().describe()
                    + " and saved to runtime-map-overrides.yml for " + context.map().id() + ".");
        } catch (RuntimeException | IOException failure) {
            logger.log(Level.SEVERE, "Could not move the capture banner", failure);
            player.sendMessage("The capture banner could not be moved. Check the server log.");
        }
        return true;
    }

    private boolean saveKit(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can capture the default siege kit.");
            return true;
        }
        if (args.length != 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage("Run /" + label + " admin savekit confirm to replace the global default kit.");
            return true;
        }
        try {
            KitSnapshot snapshot = KitSnapshot.fromInventory(player.getInventory());
            if (runtimeKitOverrides == null) {
                throw new IOException("runtime kit overrides are unavailable");
            }
            runtimeKitOverrides.save(snapshot);
            snapshot.saveToConfig(plugin.getConfig());
            kits.replaceSnapshot(snapshot);
            player.sendMessage("Default siege kit saved to runtime-overrides.yml and activated.");
        } catch (IllegalArgumentException | IOException failure) {
            player.sendMessage("The default siege kit was not saved: " + failure.getMessage());
        }
        return true;
    }

    private boolean supply(CommandSender sender, String label, String[] args) {
        if (storages == null || args.length < 3) {
            supplyUsage(sender, label);
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "register" -> supplyRegister(sender, label, args);
            case "unregister" -> supplyUnregister(sender, label, args);
            case "list" -> supplyList(sender, label, args);
            case "info" -> supplyInfo(sender, label, args);
            default -> {
                supplyUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean supplyRegister(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 4) {
            sender.sendMessage("Usage: /" + label + " admin supply register <red|blue>");
            return true;
        }
        Team team = Team.fromInput(args[3]).orElse(null);
        if (team == null) {
            sender.sendMessage("Unknown team. Choose red or blue.");
            return true;
        }
        sender.sendMessage(storages.register(player, team).message());
        return true;
    }

    private boolean supplyUnregister(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 3) {
            sender.sendMessage("Usage: /" + label + " admin supply unregister");
            return true;
        }
        sender.sendMessage(storages.unregister(player).message());
        return true;
    }

    private boolean supplyList(CommandSender sender, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " admin supply list");
            return true;
        }
        int count = 0;
        for (PotionStorage storage : storages.storages()) {
            count++;
            sender.sendMessage("- " + storage.team().defaultDisplayName() + " "
                    + PotionStorageTemplates.label(storage.potion()) + " on map "
                    + storage.key().mapId() + " at " + storage.key().first().x() + ", "
                    + storage.key().first().y() + ", " + storage.key().first().z());
        }
        if (count == 0) sender.sendMessage("No potion storages are registered.");
        return true;
    }

    private boolean supplyInfo(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 3) {
            sender.sendMessage("Usage: /" + label + " admin supply info");
            return true;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        PotionStorage storage = target == null ? null : storages.find(target).orElse(null);
        sender.sendMessage(storage == null
                ? "Look directly at a registered potion storage within 6 blocks."
                : storage.team().defaultDisplayName() + " potion storage: "
                + PotionStorageTemplates.label(storage.potion()) + ".");
        return true;
    }

    private static void supplyUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " admin supply <register <red|blue>|unregister|list|info>");
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " admin <" + String.join("|", SUBCOMMANDS) + ">");
    }
}
