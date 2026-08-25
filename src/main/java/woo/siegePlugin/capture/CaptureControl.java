package woo.siegePlugin.capture;

import woo.siegePlugin.team.Team;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who holds the banner and who is counted toward it.
 *
 * <p>Following SiegeWar, the controlling side survives its controller set
 * emptying: only an opposing completion reverses control.</p>
 */
public final class CaptureControl {

    public enum Outcome {
        CONTROL_GAINED,
        CONTROL_REVERSED,
        CONTROLLER_ADDED
    }

    private final Set<UUID> controllers = new LinkedHashSet<>();
    private Team controllingTeam;

    public Outcome completeSession(UUID playerId, Team side) {
        if (side == controllingTeam) {
            controllers.add(playerId);
            return Outcome.CONTROLLER_ADDED;
        }

        boolean reversal = controllingTeam != null;
        controllers.clear();
        controllingTeam = side;
        controllers.add(playerId);
        return reversal ? Outcome.CONTROL_REVERSED : Outcome.CONTROL_GAINED;
    }

    public boolean removeController(UUID playerId) {
        return controllers.remove(playerId);
    }

    public boolean isController(UUID playerId) {
        return controllers.contains(playerId);
    }

    public void reset() {
        controllers.clear();
        controllingTeam = null;
    }

    public Optional<Team> controllingTeam() {
        return Optional.ofNullable(controllingTeam);
    }

    public int controllerCount() {
        return controllers.size();
    }
}
