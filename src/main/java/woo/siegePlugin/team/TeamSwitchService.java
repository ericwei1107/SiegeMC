package woo.siegePlugin.team;

import org.bukkit.entity.Player;
import woo.siegePlugin.capture.CaptureSessionStatus;
import woo.siegePlugin.combat.CombatTagStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TeamSwitchService {

    static final Duration SWITCH_COOLDOWN = Duration.ofMinutes(15);

    private final TownyAdapter townyAdapter;
    private final CombatTagStatus combatTagStatus;
    private final CaptureSessionStatus captureSessionStatus;
    private final TeamSpawnLocations spawnLocations;
    private final Clock clock;
    private final Map<UUID, Instant> lastSwitches = new HashMap<>();

    public TeamSwitchService(
            TownyAdapter townyAdapter,
            CombatTagStatus combatTagStatus,
            CaptureSessionStatus captureSessionStatus,
            TeamSpawnLocations spawnLocations
    ) {
        this(townyAdapter, combatTagStatus, captureSessionStatus, spawnLocations, Clock.systemUTC());
    }

    TeamSwitchService(
            TownyAdapter townyAdapter,
            CombatTagStatus combatTagStatus,
            CaptureSessionStatus captureSessionStatus,
            TeamSpawnLocations spawnLocations,
            Clock clock
    ) {
        this.townyAdapter = townyAdapter;
        this.combatTagStatus = combatTagStatus;
        this.captureSessionStatus = captureSessionStatus;
        this.spawnLocations = spawnLocations;
        this.clock = clock;
    }

    public TeamSwitchResult switchTeam(Player player, Team destination) {
        Team currentTeam = townyAdapter.getPlayerTeam(player).orElse(null);
        if (currentTeam == null) {
            return TeamSwitchResult.of(TeamSwitchResult.Status.NO_CURRENT_TEAM);
        }
        if (currentTeam == destination) {
            return TeamSwitchResult.of(TeamSwitchResult.Status.ALREADY_ON_TEAM);
        }

        Duration cooldownRemaining = getCooldownRemaining(player.getUniqueId(), clock.instant());
        if (!cooldownRemaining.isZero()) {
            return TeamSwitchResult.cooldown(cooldownRemaining);
        }
        if (combatTagStatus.isInCombat(player)) {
            return TeamSwitchResult.of(TeamSwitchResult.Status.COMBAT_TAGGED);
        }
        if (captureSessionStatus.isActiveParticipant(player)) {
            return TeamSwitchResult.of(TeamSwitchResult.Status.CAPTURE_SESSION_ACTIVE);
        }

        int currentResidents = townyAdapter.getResidentCount(currentTeam);
        int destinationResidents = townyAdapter.getResidentCount(destination);
        if (wouldCreateTwoPlayerLead(currentResidents, destinationResidents)) {
            return TeamSwitchResult.of(TeamSwitchResult.Status.WOULD_UNBALANCE_TEAMS);
        }

        townyAdapter.setPlayerTeam(player, destination);
        lastSwitches.put(player.getUniqueId(), clock.instant());

        // No team-specific temporary state exists yet. Stage 4.4f will wire
        // its capture-session cancellation into captureSessionStatus.
        boolean teleported = player.teleport(spawnLocations.get(destination));
        return TeamSwitchResult.switched(teleported);
    }

    static boolean wouldCreateTwoPlayerLead(int sourceResidents, int destinationResidents) {
        int sourceAfterMove = sourceResidents - 1;
        int destinationAfterMove = destinationResidents + 1;
        return destinationAfterMove - sourceAfterMove >= 2;
    }

    private Duration getCooldownRemaining(UUID playerId, Instant now) {
        Instant lastSwitch = lastSwitches.get(playerId);
        if (lastSwitch == null) {
            return Duration.ZERO;
        }

        return calculateCooldownRemaining(lastSwitch, now);
    }

    static Duration calculateCooldownRemaining(Instant lastSwitch, Instant now) {
        Duration elapsed = Duration.between(lastSwitch, now);
        if (elapsed.isNegative() || elapsed.compareTo(SWITCH_COOLDOWN) < 0) {
            Duration remaining = SWITCH_COOLDOWN.minus(elapsed);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }
        return Duration.ZERO;
    }
}
