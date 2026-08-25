package woo.siegePlugin.score;

import woo.siegePlugin.team.Team;

import java.util.EnumMap;
import java.util.Map;

/**
 * Points earned during the current ACTIVE window only. Unlike match totals
 * these are never persisted — each new ACTIVE window starts them at zero.
 */
public final class SessionPoints {

    private final Map<Team, Long> points = new EnumMap<>(Team.class);

    public void add(Team team, long amount) {
        points.merge(team, amount, Long::sum);
    }

    public long get(Team team) {
        return points.getOrDefault(team, 0L);
    }

    public void reset() {
        points.clear();
    }
}
