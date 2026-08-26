package woo.siegePlugin.team;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.InvalidNameException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The single boundary between SiegePlugin and Towny's API.
 *
 * <p>Gameplay code must ask this adapter about residency instead of caching
 * or duplicating team membership.</p>
 */
public final class TownyAdapter {

    private static final String SPECTATOR_TOWN_PATH = "spectator.town";

    private final TownyAPI townyApi;
    private final Map<Team, String> townNames;
    private final String spectatorTownName;

    private TownyAdapter(TownyAPI townyApi, Map<Team, String> townNames, String spectatorTownName) {
        this.townyApi = townyApi;
        this.townNames = new EnumMap<>(townNames);
        this.spectatorTownName = spectatorTownName;
    }

    public static TownyAdapter fromConfig(FileConfiguration config) {
        TownyAPI api = TownyAPI.getInstance();
        Map<Team, String> configuredTownNames = new EnumMap<>(Team.class);

        for (Team team : Team.values()) {
            String townName = Objects.requireNonNull(config.getString(team.townConfigPath()));
            Objects.requireNonNull(
                    api.getTown(townName),
                    "Configured Towny town was not validated: " + townName
            );
            configuredTownNames.put(team, townName);
        }

        String spectatorTown = Objects.requireNonNull(config.getString(SPECTATOR_TOWN_PATH));
        Objects.requireNonNull(
                api.getTown(spectatorTown),
                "Configured spectator town was not provisioned: " + spectatorTown
        );

        return new TownyAdapter(api, configuredTownNames, spectatorTown);
    }

    /**
     * Creates the configured spectator town without a homeblock or any claims.
     * Towny's direct universe API intentionally supports landless towns.
     */
    public static List<String> provisionSpectatorTown(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        String spectatorTown = config.getString(SPECTATOR_TOWN_PATH);
        if (spectatorTown == null || spectatorTown.isBlank()) {
            problems.add(SPECTATOR_TOWN_PATH + " is missing or empty");
            return problems;
        }

        TownyUniverse universe = TownyUniverse.getInstance();
        if (universe.getTown(spectatorTown) != null) {
            return problems;
        }

        try {
            universe.newTown(spectatorTown);
        } catch (AlreadyRegisteredException ignored) {
            // A concurrent Towny operation registered it after the lookup.
        } catch (InvalidNameException exception) {
            problems.add(SPECTATOR_TOWN_PATH + " '" + spectatorTown + "' is not a valid Towny town name");
        }

        if (universe.getTown(spectatorTown) == null && problems.isEmpty()) {
            problems.add("could not provision " + SPECTATOR_TOWN_PATH + " '" + spectatorTown + "'");
        }
        return problems;
    }

    /**
     * Keeps Towny-aware startup validation inside the same API boundary used
     * by gameplay code. Towny's name lookup is case-insensitive.
     */
    public static List<String> findConfigurationProblems(FileConfiguration config) {
        TownyAPI api = TownyAPI.getInstance();
        List<String> problems = new ArrayList<>();

        String spectatorTown = config.getString(SPECTATOR_TOWN_PATH);
        if (spectatorTown == null || spectatorTown.isBlank()) {
            problems.add(SPECTATOR_TOWN_PATH + " is missing or empty");
        }

        for (Team team : Team.values()) {
            String townName = config.getString(team.townConfigPath());
            if (townName != null && !townName.isBlank() && api.getTown(townName) == null) {
                problems.add(team.townConfigPath() + " '" + townName + "' does not exist in Towny");
            }
        }

        return problems;
    }

    /**
     * Performs a fresh Towny lookup every time. No plugin-owned membership
     * record exists by design.
     */
    public Optional<Team> getPlayerTeam(Player player) {
        Resident resident = townyApi.getResident(player);
        if (resident == null || !resident.hasTown()) {
            return Optional.empty();
        }

        Town residentTown = resident.getTownOrNull();
        for (Team team : Team.values()) {
            if (getTown(team).equals(residentTown)) {
                return Optional.of(team);
            }
        }

        return Optional.empty();
    }

    public int getResidentCount(Team team) {
        return getTown(team).getResidents().size();
    }

    /** Spectator residents intentionally do not map to a competitive team. */
    public boolean isSpectator(Player player) {
        Resident resident = townyApi.getResident(player);
        return resident != null && getSpectatorTown().equals(resident.getTownOrNull());
    }

    /** True only for unclaimed Towny wilderness, never for either protected base. */
    public boolean isWilderness(Location location) {
        return townyApi.isWilderness(location);
    }

    public void setPlayerTeam(Player player, Team team) {
        movePlayerToTown(player, getTown(team));
    }

    public void movePlayerToSpectatorTown(Player player) {
        movePlayerToTown(player, getSpectatorTown());
    }

    private void movePlayerToTown(Player player, Town destination) {
        Resident resident = townyApi.getResident(player);
        if (resident == null) {
            throw new IllegalStateException("Towny has no resident record for " + player.getName());
        }

        if (destination.equals(resident.getTownOrNull())) {
            return;
        }

        // Towny deliberately rejects setTown(newTown) while the resident is
        // still registered to a different town. removeTown() performs Towny's
        // normal departure cleanup before the direct force-join.
        if (resident.hasTown()) {
            resident.removeTown();
        }

        try {
            resident.setTown(destination);
        } catch (AlreadyRegisteredException exception) {
            throw new IllegalStateException(
                    "Towny rejected moving " + player.getName() + " to " + destination.getName(),
                    exception
            );
        }
        resident.save();
        destination.save();
    }

    public Town getTown(Team team) {
        String townName = Objects.requireNonNull(
                townNames.get(team),
                "No Towny town configured for " + team
        );
        return Objects.requireNonNull(
                townyApi.getTown(townName),
                "Configured Towny town no longer exists: " + townName
        );
    }

    private Town getSpectatorTown() {
        return Objects.requireNonNull(
                townyApi.getTown(spectatorTownName),
                "Configured spectator town no longer exists: " + spectatorTownName
        );
    }
}
