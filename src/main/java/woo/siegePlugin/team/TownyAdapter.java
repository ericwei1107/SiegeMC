package woo.siegePlugin.team;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
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

    private final TownyAPI townyApi;
    private final Map<Team, String> townNames;

    private TownyAdapter(TownyAPI townyApi, Map<Team, String> townNames) {
        this.townyApi = townyApi;
        this.townNames = new EnumMap<>(townNames);
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

        return new TownyAdapter(api, configuredTownNames);
    }

    /**
     * Keeps Towny-aware startup validation inside the same API boundary used
     * by gameplay code. Towny's name lookup is case-insensitive.
     */
    public static List<String> findConfigurationProblems(FileConfiguration config) {
        TownyAPI api = TownyAPI.getInstance();
        List<String> problems = new ArrayList<>();

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

    public void setPlayerTeam(Player player, Team team) {
        Resident resident = townyApi.getResident(player);
        if (resident == null) {
            throw new IllegalStateException("Towny has no resident record for " + player.getName());
        }

        Town destination = getTown(team);
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
}
