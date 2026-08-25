package woo.siegePlugin.team;

import java.util.Locale;
import java.util.Optional;

/**
 * The two competitive siege teams. Towny town names are configuration, not
 * enum constants, so server owners can rename the backing towns later.
 */
public enum Team {
    RED("red", "Red Team"),
    BLUE("blue", "Blue Team");

    private final String configKey;
    private final String defaultDisplayName;

    Team(String configKey, String defaultDisplayName) {
        this.configKey = configKey;
        this.defaultDisplayName = defaultDisplayName;
    }

    public String configKey() {
        return configKey;
    }

    public String townConfigPath() {
        return "teams." + configKey + ".town";
    }

    public String spawnConfigPath() {
        return "teams." + configKey + ".spawn";
    }

    public String colorConfigPath() {
        return "teams." + configKey + ".color";
    }

    public String defaultDisplayName() {
        return defaultDisplayName;
    }

    public Team opponent() {
        return this == RED ? BLUE : RED;
    }

    public static Optional<Team> fromInput(String input) {
        if (input == null) {
            return Optional.empty();
        }

        return switch (input.toLowerCase(Locale.ROOT)) {
            case "red" -> Optional.of(RED);
            case "blue" -> Optional.of(BLUE);
            default -> Optional.empty();
        };
    }
}
