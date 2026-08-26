package woo.siegePlugin.state;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record LobbySettings(Location spawn) {

    static final String WORLD_PATH = "lobby.world";
    private static final String SPAWN_ROOT = "lobby.spawn";

    public LobbySettings {
        spawn = spawn.clone();
    }

    public static LobbySettings fromConfig(FileConfiguration config, Server server) {
        String worldName = Objects.requireNonNull(config.getString(WORLD_PATH));
        World world = Objects.requireNonNull(
                server.getWorld(worldName),
                "Configured lobby world is not loaded: " + worldName
        );
        return new LobbySettings(new Location(
                world,
                config.getDouble(SPAWN_ROOT + ".x"),
                config.getDouble(SPAWN_ROOT + ".y"),
                config.getDouble(SPAWN_ROOT + ".z")
        ));
    }

    public static List<String> findConfigurationProblems(FileConfiguration config, Server server) {
        List<String> problems = new ArrayList<>();
        String worldName = config.getString(WORLD_PATH);
        if (worldName == null || worldName.isBlank()) {
            problems.add(WORLD_PATH + " is missing or empty");
        } else if (server.getWorld(worldName) == null) {
            problems.add(WORLD_PATH + " '" + worldName + "' is not a loaded world");
        }

        for (String coordinate : List.of("x", "y", "z")) {
            String path = SPAWN_ROOT + "." + coordinate;
            if (!(config.get(path) instanceof Number)) {
                problems.add(path + " must be a number");
            }
        }
        return problems;
    }

    @Override
    public Location spawn() {
        return spawn.clone();
    }
}
