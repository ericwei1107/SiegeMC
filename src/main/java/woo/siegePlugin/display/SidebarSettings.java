package woo.siegePlugin.display;

import org.bukkit.configuration.file.FileConfiguration;
import woo.siegePlugin.team.Team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SidebarSettings {

    private final String title;
    private final Map<Team, String> displayNames;

    private SidebarSettings(String title, Map<Team, String> displayNames) {
        this.title = title;
        this.displayNames = new EnumMap<>(displayNames);
    }

    public static SidebarSettings fromConfig(FileConfiguration config) {
        Map<Team, String> displayNames = new EnumMap<>(Team.class);
        for (Team team : Team.values()) {
            displayNames.put(
                    team,
                    config.getString(team.displayNameConfigPath(), team.defaultDisplayName())
            );
        }
        return new SidebarSettings(
                config.getString("sidebar.title", "Siege Status"),
                displayNames
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        String title = config.getString("sidebar.title");
        if (title != null && title.isBlank()) {
            problems.add("sidebar.title must not be empty");
        }

        for (Team team : Team.values()) {
            String displayName = config.getString(team.displayNameConfigPath());
            if (displayName != null && displayName.isBlank()) {
                problems.add(team.displayNameConfigPath() + " must not be empty");
            }
        }
        return problems;
    }

    public String title() {
        return title;
    }

    public String displayName(Team team) {
        return Objects.requireNonNull(displayNames.get(team));
    }
}
