package woo.siegePlugin.minecart;

import org.bukkit.FeatureFlag;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Rejects the experiment that changes the stacking/fall mechanic Stage 4.5 relies on. */
public final class MinecartWorldCompatibility {

    private static final String CAPTURE_WORLD_PATH = "capture-point.world";

    private MinecartWorldCompatibility() {
    }

    public static List<String> findProblems(FileConfiguration config, Server server) {
        List<String> problems = new ArrayList<>();
        String worldName = config.getString(CAPTURE_WORLD_PATH);
        World world = worldName == null ? null : server.getWorld(worldName);
        if (world != null && world.getFeatureFlags().contains(FeatureFlag.MINECART_IMPROVEMENTS)) {
            problems.add("capture-point.world '" + worldName
                    + "' has the Minecart Improvements experiment enabled; disable it for siege cart stacking and fall physics");
        }
        return problems;
    }
}
