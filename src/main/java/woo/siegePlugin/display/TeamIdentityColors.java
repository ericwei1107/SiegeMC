package woo.siegePlugin.display;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import woo.siegePlugin.team.Team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TeamIdentityColors {

    private final Map<Team, NamedTextColor> colors;

    private TeamIdentityColors(Map<Team, NamedTextColor> colors) {
        this.colors = new EnumMap<>(colors);
    }

    public static TeamIdentityColors fromConfig(FileConfiguration config) {
        Map<Team, NamedTextColor> colors = new EnumMap<>(Team.class);
        for (Team team : Team.values()) {
            NamedTextColor fallback = defaultColor(team);
            String configured = config.getString(team.colorConfigPath(), colorName(fallback));
            colors.put(team, Objects.requireNonNull(parse(configured)));
        }
        return new TeamIdentityColors(colors);
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        for (Team team : Team.values()) {
            String configured = config.getString(team.colorConfigPath());
            if (configured != null && parse(configured) == null) {
                problems.add(team.colorConfigPath() + " '" + configured + "' is not a standard Minecraft color");
            }
        }
        return problems;
    }

    public NamedTextColor get(Team team) {
        return Objects.requireNonNull(colors.get(team), "No identity color configured for " + team);
    }

    static NamedTextColor parse(String value) {
        if (value == null) {
            return null;
        }
        return NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }

    private static NamedTextColor defaultColor(Team team) {
        return team == Team.RED ? NamedTextColor.RED : NamedTextColor.BLUE;
    }

    private static String colorName(NamedTextColor color) {
        return Objects.requireNonNull(NamedTextColor.NAMES.key(color));
    }
}
