package woo.siegePlugin.minecart;

import woo.siegePlugin.team.Team;

/** Immutable RED/BLUE presence at the banner for one explosion. */
public record MinecartHeadcounts(int red, int blue) {

    public MinecartHeadcounts {
        if (red < 0 || blue < 0) {
            throw new IllegalArgumentException("Minecart headcounts cannot be negative");
        }
    }

    public int forTeam(Team team) {
        return team == Team.RED ? red : blue;
    }

    public int against(Team team) {
        return forTeam(team.opponent());
    }
}
