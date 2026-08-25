package woo.siegePlugin.capture;

import woo.siegePlugin.team.Team;

import java.util.Optional;

/** The banner state scoring needs, without depending on session mechanics. */
public interface BannerControlStatus {

    Optional<Team> controllingTeam();

    int controllerCount();
}
