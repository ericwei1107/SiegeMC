package woo.siegePlugin.team;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TeamSpawnLocations {

    private final Map<Team, Location> locations;

    private TeamSpawnLocations(Map<Team, Location> locations) {
        this.locations = new EnumMap<>(locations);
    }

    public static TeamSpawnLocations fromConfig(FileConfiguration config, Server server) {
        Map<Team, Location> locations = new EnumMap<>(Team.class);

        for (Team team : Team.values()) {
            String path = team.spawnConfigPath();
            String worldName = Objects.requireNonNull(config.getString(path + ".world"));
            World world = Objects.requireNonNull(
                    server.getWorld(worldName),
                    "Configured team spawn world is not loaded: " + worldName
            );
            locations.put(team, new Location(
                    world,
                    config.getDouble(path + ".x"),
                    config.getDouble(path + ".y"),
                    config.getDouble(path + ".z")
            ));
        }

        return new TeamSpawnLocations(locations);
    }

    public static List<String> findConfigurationProblems(FileConfiguration config, Server server) {
        List<String> problems = new ArrayList<>();

        for (Team team : Team.values()) {
            String path = team.spawnConfigPath();
            String worldName = config.getString(path + ".world");
            if (worldName == null || worldName.isBlank()) {
                problems.add(path + ".world is missing or empty");
            } else if (server.getWorld(worldName) == null) {
                problems.add(path + ".world '" + worldName + "' is not a loaded world");
            }

            for (String coordinate : List.of("x", "y", "z")) {
                String coordinatePath = path + "." + coordinate;
                if (!(config.get(coordinatePath) instanceof Number)) {
                    problems.add(coordinatePath + " must be a number");
                }
            }
        }

        return problems;
    }

    public Location get(Team team) {
        return Objects.requireNonNull(locations.get(team), "No spawn configured for " + team).clone();
    }
}
