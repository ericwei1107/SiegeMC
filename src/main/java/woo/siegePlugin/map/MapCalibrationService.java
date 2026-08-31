package woo.siegePlugin.map;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.state.LobbySettings;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.storage.PotionStorageService;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Admin-only, disposable map setup session. It is never a combat round. */
public final class MapCalibrationService {
    private final JavaPlugin plugin;
    private final NativeMapWorldLoader loader;
    private final RuntimeMapOverrides overrides;
    private final File mapsFile;
    private final LobbySettings lobby;
    private final PotionStorageService storages;
    private ActiveMapWorld active;
    private java.util.UUID owner;
    private final Map<Team, MapPoint> spawns = new EnumMap<>(Team.class);
    private Location firstCorner;
    private Location secondCorner;
    private Location banner;
    private int bannerRadius = 8;

    public MapCalibrationService(JavaPlugin plugin, NativeMapWorldLoader loader, RuntimeMapOverrides overrides, File mapsFile, LobbySettings lobby, PotionStorageService storages) {
        this.plugin = plugin; this.loader = loader; this.overrides = overrides; this.mapsFile = mapsFile; this.lobby = lobby; this.storages = storages;
    }

    public boolean start(Player player, String mapId) {
        if (active != null) { player.sendMessage("A calibration session is already open."); return false; }
        SiegeMap map;
        try { map = overrides.calibrationMap(mapsFile, mapId); }
        catch (IllegalArgumentException failure) { player.sendMessage("Could not open map: " + failure.getMessage()); return false; }
        if (!loader.templateExists(map)) { player.sendMessage("That map template is missing or unsafe to load."); return false; }
        owner = player.getUniqueId();
        loader.load(map).whenComplete((loaded, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) { owner = null; player.sendMessage("Calibration copy could not be loaded: " + failure.getMessage()); return; }
            active = loaded;
            MapPoint arrival = loaded.map().redSpawn();
            player.teleport(new Location(loaded.world(), arrival.x(), arrival.y(), arrival.z(), arrival.yaw(), arrival.pitch()));
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage("Calibration copy opened for " + loaded.map().displayName() + ". Set both spawns, bounds, banner, then finish.");
        }));
        return true;
    }

    public Optional<ActiveMapWorld> activeFor(Player player) {
        return active != null && player.getUniqueId().equals(owner) && player.getWorld().equals(active.world()) ? Optional.of(active) : Optional.empty();
    }
    public boolean ownsSession(Player player) {
        return active != null && player.getUniqueId().equals(owner);
    }
    public String returnToMap(Player player) {
        if (!ownsSession(player)) return "You do not have an active calibration session.";
        MapPoint arrival = active.map().redSpawn();
        player.teleport(new Location(active.world(), arrival.x(), arrival.y(), arrival.z(), arrival.yaw(), arrival.pitch()));
        player.setGameMode(GameMode.CREATIVE);
        return "Returned to the calibration copy.";
    }
    public void setSpawn(Team team, Location location) { spawns.put(team, point(location)); }
    public void setCorner(int number, Location location) {
        if (number == 1) firstCorner = location.clone(); else secondCorner = location.clone();
        if (firstCorner != null && secondCorner != null && active != null) {
            storages.activateCalibrationMap(active.map().id(), active.world().getName(), bounds());
        }
    }
    public void setBanner(Location location, int radius) { banner = location.clone(); bannerRadius = radius; }

    public String finish(Player player) {
        ActiveMapWorld world = ownsSession(player) ? active : null;
        if (world == null) return "You do not have an active calibration session.";
        if (spawns.size() != 2 || firstCorner == null || secondCorner == null || banner == null) return "Set red/blue spawns, both bounds corners, and the banner first.";
        SiegeMap map;
        try {
            MapBounds bounds = bounds();
            map = new SiegeMap(world.map().id(), world.map().displayName(), world.map().templateFolder(), spawns.get(Team.RED), spawns.get(Team.BLUE), point(banner), bannerRadius, bounds);
            java.util.List<String> problems = new java.util.ArrayList<>(MapValidator.staticProblems(map, true));
            ActiveMapWorld calibrated = new ActiveMapWorld(map, world.world(), world.folder());
            problems.addAll(MapValidator.loadedCopyProblems(calibrated));
            if (!problems.isEmpty()) return "Not enabled: " + String.join("; ", problems);
            detachForPromotion(player);
            loader.promote(calibrated).whenComplete((backup, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not promote calibration for " + map.id(), failure);
                    if (player.isOnline()) player.sendMessage("Calibration could not update the clean template. The previous template remains available; check the server log.");
                    return;
                }
                try {
                    overrides.saveCalibration(map);
                    if (player.isOnline()) player.sendMessage(map.displayName() + " was promoted, saved, and enabled. Run /siege admin rotation validate " + map.id());
                } catch (IOException saveFailure) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Template was promoted but map setup could not be saved for " + map.id(), saveFailure);
                    if (player.isOnline()) player.sendMessage("The template was updated, but setup coordinates could not be saved. Check the server log.");
                }
            }));
            return "Saving and promoting the calibration copy; you will receive a confirmation shortly.";
        } catch (IllegalArgumentException failure) { return "Could not save calibration: " + failure.getMessage(); }
    }
    public String abort(Player player) { if (!ownsSession(player)) return "You do not have an active calibration session."; close(player); return "Calibration discarded."; }
    private void close(Player player) {
        ActiveMapWorld closing = active; active = null; owner = null; spawns.clear(); firstCorner = secondCorner = banner = null;
        storages.deactivateCalibrationMap();
        player.teleport(lobby.spawn()); player.setGameMode(GameMode.ADVENTURE);
        loader.unload(closing); // no save: the clean template remains immutable
    }
    private void detachForPromotion(Player player) {
        active = null; owner = null; spawns.clear(); firstCorner = secondCorner = banner = null;
        storages.deactivateCalibrationMap();
        player.teleport(lobby.spawn()); player.setGameMode(GameMode.ADVENTURE);
    }
    private static MapPoint point(Location location) { return new MapPoint(location.getBlockX() + .5, location.getBlockY(), location.getBlockZ() + .5, location.getYaw(), location.getPitch()); }
    private MapBounds bounds() { return new MapBounds(Math.min(firstCorner.getBlockX(), secondCorner.getBlockX()), Math.min(firstCorner.getBlockZ(), secondCorner.getBlockZ()), Math.max(firstCorner.getBlockX(), secondCorner.getBlockX()), Math.max(firstCorner.getBlockZ(), secondCorner.getBlockZ())); }
}
