package woo.siegePlugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class SiegePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Ensures plugins/SiegeMC/config.yml exists, copying the bundled
        // default from resources/config.yml if it's missing. Does NOT
        // overwrite an existing file.
        saveDefaultConfig();

        List<String> problems = validateConfig();

        if (!problems.isEmpty()) {
            getLogger().severe("SiegeMC failed to start due to invalid configuration:");
            for (String problem : problems) {
                getLogger().severe(" - " + problem);
            }
            getLogger().severe("Fix config.yml and restart the server. Plugin will now disable.");
            getServer().getPluginManager().disablePlugin(this);
            return; // stop here — nothing below this line should assume config is valid
        }

        getLogger().info("SiegeMC enabled — config validated successfully.");
        // Real plugin startup logic (Towny adapter, team repository, etc.)
        // goes here, AFTER this point, once config is confirmed valid.
    }

    /**
     * Checks every config value the plugin actually depends on.
     * Returns a list of human-readable problems — empty list means config is valid.
     */
    private List<String> validateConfig() {
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

        return problems;
    }
}