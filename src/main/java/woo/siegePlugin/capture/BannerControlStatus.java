package woo.siegePlugin.capture;

import woo.siegePlugin.team.Team;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The banner state scoring needs, without depending on session mechanics. */
public interface BannerControlStatus {

    Optional<Team> controllingTeam();

    int controllerCount();

    /** Immutable snapshot of the completed controllers currently earning control credit. */
    Set<UUID> controllerIds();
}
